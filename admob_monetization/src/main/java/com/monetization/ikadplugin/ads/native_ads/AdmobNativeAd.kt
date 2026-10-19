package com.monetization.ikadplugin.ads.native_ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.monetization.ikadplugin.ads.AdRetryPolicy
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference

/**
 * Loads native ads. Rendering is done in Compose (see `NativeAdSlot` / `IkComposeNativeAd`), so this
 * class only hands out [NativeAd] objects.
 *
 * Ads are pooled per ad unit, so placements never steal each other's ads and two slots on one
 * screen each get their own request. A pooled ad older than [AdRetryPolicy.MAX_AD_AGE_MS] is
 * dropped instead of shown. Network / internal errors are retried with backoff; no-fills are not,
 * because retrying a no-fill only lowers the match rate.
 */
class AdmobNativeAd {

    companion object {
        @Volatile
        private var INSTANCE: AdmobNativeAd? = null

        fun getInstance(
        ): AdmobNativeAd {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobNativeAd().also { INSTANCE = it }
            }
        }

        /** Preloaded ads kept per ad unit. */
        private const val MAX_POOLED_PER_UNIT = 2
    }

    private class CachedAd(val ad: NativeAd, val loadedAt: Long)

    private class NativeAdConsumer(
        val adIdNativeReference: String,
        val onLoaded: (NativeAd) -> Unit,
        val onFailed: () -> Unit,
    )

    private class UnitSlot(val adUnitId: String) {
        val pool = ArrayDeque<CachedAd>()
        val waiters = ArrayDeque<NativeAdConsumer>()
        var inFlight = 0
        val retry = AdRetryPolicy()
    }

    private val slots = mutableMapOf<String, UnitSlot>()

    private fun slotFor(reference: String): UnitSlot {
        val adUnitId = FetchConfig.getNativeId(reference)
        return slots.getOrPut(adUnitId) { UnitSlot(adUnitId) }
    }

    fun onDestroyNativeAd(context: Context) {
        slots.values.forEach { slot ->
            slot.retry.cancel()
            slot.pool.forEach { cached ->
                try {
                    cached.ad.destroy()
                } catch (e: Exception) {
                    AdClickDurationTracker.warn(
                        context,
                        adType = "NATIVE",
                        stage = "DESTROY",
                        placement = slot.adUnitId,
                        message = "Failed destroying native ad: ${e.message}"
                    )
                }
            }
            slot.pool.clear()
        }
    }

    fun hasLargeAdOrLoading(): Boolean = slots.values.any { it.pool.isNotEmpty() || it.inFlight > 0 }

    fun hasLargeAd(): Boolean = slots.values.any { it.pool.isNotEmpty() }

    /** True when a fresh ad for [adIdNativeReference]'s ad unit is ready to show. */
    fun hasAd(adIdNativeReference: String): Boolean {
        val slot = slotFor(adIdNativeReference)
        dropExpired(slot)
        return slot.pool.isNotEmpty()
    }

    internal fun canRequest(context: Context, enable: Boolean): Boolean =
        !FirebaseValue.ALL_ADS_OFF_ENABLE &&
            GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
            enable &&
            !AdSharedPreference.getInstance(context).isAppPurchased &&
            InternetController.getInstance(context).isInternetConnected

    fun preLoadNativeAd(
        adIdNativeReference: String, context: Context, enable: Boolean
    ) {
        try {
            if (!canRequest(context, enable)) return
            val slot = slotFor(adIdNativeReference)
            dropExpired(slot)
            if (slot.pool.isEmpty() && slot.inFlight == 0) {
                requestAd(slot, adIdNativeReference, context)
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(context,
                adType = "NATIVE",
                stage = "PRELOAD",
                placement = adIdNativeReference,
                throwable = e,
                message = "Native preload flow failed."
            )
        }
    }

    /**
     * Hands a native ad to [onLoaded]: a pooled one if there is a fresh one, otherwise a freshly
     * requested one. [onFailed] fires when ads are suppressed (ads off, no consent, purchased,
     * offline, [enable] false) or the request fails.
     *
     * The ad now belongs to the caller, who must `destroy()` it when done. With [loadNewAd] the
     * next ad is preloaded straight away.
     */
    fun loadNativeAd(
        adIdNativeReference: String,
        context: Context,
        enable: Boolean,
        loadNewAd: Boolean = false,
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit = {},
    ) {
        try {
            if (!canRequest(context, enable)) {
                onFailed()
                return
            }

            val slot = slotFor(adIdNativeReference)
            dropExpired(slot)
            val cached = slot.pool.removeFirstOrNull()
            if (cached != null) {
                AdClickDurationTracker.adShow(context,
                    adType = AdType.NATIVE,
                    adIdReferenceName = adIdNativeReference
                )
                onLoaded(cached.ad)
                if (loadNewAd) preLoadNativeAd(adIdNativeReference, context, enable)
                return
            }

            slot.waiters.addLast(
                NativeAdConsumer(
                    adIdNativeReference = adIdNativeReference,
                    onLoaded = { ad ->
                        onLoaded(ad)
                        if (loadNewAd) preLoadNativeAd(adIdNativeReference, context, enable)
                    },
                    onFailed = onFailed,
                )
            )
            // Every waiting slot gets its own request; one already in flight covers one waiter.
            if (slot.waiters.size > slot.inFlight) {
                slot.retry.cancel()
                requestAd(slot, adIdNativeReference, context)
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(context,
                adType = "NATIVE",
                stage = "LOAD_AND_SHOW",
                placement = adIdNativeReference,
                throwable = e,
                message = "Native load-and-show flow failed."
            )
            onFailed()
        }
    }

    private fun dropExpired(slot: UnitSlot) {
        val now = AdRetryPolicy.now()
        while (slot.pool.isNotEmpty() && now - slot.pool.first().loadedAt > AdRetryPolicy.MAX_AD_AGE_MS) {
            slot.pool.removeFirst().ad.destroy()
        }
    }

    private fun requestAd(slot: UnitSlot, adIdNativeReference: String, context: Context) {
        slot.inFlight++
        AdClickDurationTracker.adRequestCalling(context,
            adType = AdType.NATIVE,
            adIdReferenceName = adIdNativeReference
        )
        val appContext = context.applicationContext
        // Placement that the loaded ad ends up in, for impression / revenue events.
        var shownReference = adIdNativeReference
        val builder = AdLoader.Builder(context, slot.adUnitId)
        builder.forNativeAd { newNativeAd: NativeAd ->
            slot.inFlight = (slot.inFlight - 1).coerceAtLeast(0)
            slot.retry.reset()
            AdClickDurationTracker.adRequestMatch(appContext,
                adType = AdType.NATIVE,
                adIdReferenceName = adIdNativeReference
            )
            val consumer = slot.waiters.removeFirstOrNull()
            if (consumer != null) shownReference = consumer.adIdNativeReference
            newNativeAd.setOnPaidEventListener { value ->
                AdClickDurationTracker.adPaidEvent(
                    appContext, AdType.NATIVE, shownReference, value, newNativeAd.responseInfo
                )
            }
            if (consumer != null) {
                AdClickDurationTracker.adShow(appContext,
                    adType = AdType.NATIVE,
                    adIdReferenceName = consumer.adIdNativeReference
                )
                consumer.onLoaded(newNativeAd)
            } else if (slot.pool.size < MAX_POOLED_PER_UNIT) {
                slot.pool.addLast(CachedAd(newNativeAd, AdRetryPolicy.now()))
            } else {
                newNativeAd.destroy()
            }
        }
        builder.withNativeAdOptions(
            NativeAdOptions.Builder().setVideoOptions(
                VideoOptions.Builder().setStartMuted(true).build()
            ).build()
        )

        val adLoader = builder.withAdListener(object : AdListener() {
            override fun onAdClicked() {
                super.onAdClicked()
                AdClickDurationTracker.startTracking(appContext,
                    adType = AdType.NATIVE,
                    adIdReferenceName = shownReference
                )
            }

            override fun onAdImpression() {
                super.onAdImpression()
                AdClickDurationTracker.adImpression(appContext,
                    adType = AdType.NATIVE,
                    adIdReferenceName = shownReference
                )
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                slot.inFlight = (slot.inFlight - 1).coerceAtLeast(0)
                AdClickDurationTracker.adRequestFail(appContext,
                    adType = AdType.NATIVE,
                    adIdReferenceName = adIdNativeReference,
                    errorCode = loadAdError.code,
                    errorMessage = loadAdError.message
                )
                // Only the waiters this request was covering give up.
                while (slot.waiters.size > slot.inFlight) {
                    slot.waiters.removeLast().onFailed()
                }
                if (AdRetryPolicy.isRetryable(loadAdError.code)) {
                    slot.retry.schedule {
                        if (slot.pool.isEmpty() && slot.inFlight == 0) {
                            preLoadNativeAd(adIdNativeReference, appContext, true)
                        }
                    }
                }
            }
        }).build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    /** Drops [onLoaded]/[onFailed] registered by a caller that is no longer interested. */
    internal fun cancelPending(onFailed: () -> Unit) {
        slots.values.forEach { slot -> slot.waiters.removeAll { it.onFailed === onFailed } }
    }
}
