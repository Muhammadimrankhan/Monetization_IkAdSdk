package com.monetization.ikadplugin.ads.banner

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.monetization.ikadplugin.ads.AdRetryPolicy
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.subscription.SubscriptionConstant.isDebug
import java.lang.ref.WeakReference

/**
 * Loads banners and hands the loaded [AdView] to Compose (`BannerAdSlot`), which hosts it.
 *
 * Shared by the plain banner ([AdmobBannerAd]) and the collapsible one (`AdmobCollapsibleBannerAd`):
 * banners are pooled per ad unit and size, every waiting slot gets its own request, and a pooled
 * banner is only handed out on the Activity it was created with and while it is fresh.
 */
open class BannerLoader internal constructor(
    private val adType: AdType,
    private val collapsible: Boolean,
) {

    private class CachedBanner(
        val adView: AdView,
        val activity: WeakReference<Activity>,
        val loadedAt: Long,
    )

    private class BannerConsumer(
        val adReference: String,
        val onLoaded: (AdView) -> Unit,
        val onFailed: () -> Unit,
    )

    private class BannerSlot {
        var cached: CachedBanner? = null
        val waiters = ArrayDeque<BannerConsumer>()
        var inFlight = 0
        val retry = AdRetryPolicy()
    }

    private val slots = mutableMapOf<String, BannerSlot>()

    private fun slotFor(adReference: String, isRectangleBanner: Boolean): BannerSlot {
        val key = FetchConfig.getBannerId(adReference) + if (isRectangleBanner) "#mrec" else "#adaptive"
        return slots.getOrPut(key) { BannerSlot() }
    }

    internal fun hasAnyBannerOrLoading(): Boolean =
        slots.values.any { it.cached != null || it.inFlight > 0 }

    internal fun hasAnyBanner(): Boolean = slots.values.any { it.cached != null }

    private fun canRequest(activity: Activity, enable: Boolean): Boolean =
        !FirebaseValue.ALL_ADS_OFF_ENABLE &&
            GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds &&
            enable &&
            !AdSharedPreference.getInstance(activity).isAppPurchased &&
            InternetController.getInstance(activity).isInternetConnected

    /** Takes the pooled banner if it is fresh and belongs to [activity]; destroys it otherwise. */
    private fun takeCached(slot: BannerSlot, activity: Activity): AdView? {
        val cached = slot.cached ?: return null
        slot.cached = null
        val usable = cached.activity.get() === activity &&
            AdRetryPolicy.now() - cached.loadedAt < AdRetryPolicy.MAX_AD_AGE_MS
        if (usable) return cached.adView
        cached.adView.destroy()
        return null
    }

    /** Preloads a banner so the next [loadBanner] on the same Activity can show it immediately. */
    internal fun preload(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean
    ) {
        try {
            if (!canRequest(activity, enable)) return
            val slot = slotFor(adReference, isRectangleBanner)
            val cached = slot.cached
            if (cached != null && cached.activity.get() !== activity) {
                slot.cached = null
                cached.adView.destroy()
            }
            if (slot.cached == null && slot.inFlight == 0) {
                requestAd(slot, adReference, activity, isRectangleBanner)
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(activity,
                adType = adType.firebaseName.uppercase(),
                stage = "PRELOAD",
                placement = adReference,
                throwable = e,
                message = "Banner preload flow failed."
            )
        }
    }

    /**
     * Hands a banner to [onLoaded]: the pooled one if it is usable, otherwise a freshly requested
     * one. [onFailed] fires when ads are suppressed or the request fails. With [loadNewAd] the next
     * banner is preloaded once this one is handed out.
     */
    internal fun load(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean,
        loadNewAd: Boolean,
        onLoaded: (AdView) -> Unit,
        onFailed: () -> Unit,
    ) {
        try {
            if (!canRequest(activity, enable)) {
                onFailed()
                return
            }

            val slot = slotFor(adReference, isRectangleBanner)
            val cached = takeCached(slot, activity)
            if (cached != null) {
                AdClickDurationTracker.adShow(activity,
                    adType = adType,
                    adIdReferenceName = adReference
                )
                onLoaded(cached)
                if (loadNewAd) preload(adReference, activity, enable, isRectangleBanner)
                return
            }

            slot.waiters.addLast(
                BannerConsumer(
                    adReference = adReference,
                    onLoaded = { adView ->
                        onLoaded(adView)
                        if (loadNewAd) preload(adReference, activity, enable, isRectangleBanner)
                    },
                    onFailed = onFailed,
                )
            )
            if (slot.waiters.size > slot.inFlight) {
                slot.retry.cancel()
                requestAd(slot, adReference, activity, isRectangleBanner)
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(activity,
                adType = adType.firebaseName.uppercase(),
                stage = "LOAD_AND_SHOW",
                placement = adReference,
                throwable = e,
                message = "Banner load-and-show flow failed."
            )
            onFailed()
        }
    }

    private fun requestAd(
        slot: BannerSlot,
        adReference: String,
        activity: Activity,
        isRectangleBanner: Boolean
    ) {
        slot.inFlight++
        AdClickDurationTracker.adRequestCalling(activity,
            adType = adType,
            adIdReferenceName = adReference
        )

        val appContext = activity.applicationContext
        val activityRef = WeakReference(activity)
        var shownReference = adReference

        val adView = AdView(activity)
        adView.adUnitId = FetchConfig.getBannerId(adReference)
        adView.setAdSize(
            if (isRectangleBanner) AdSize.MEDIUM_RECTANGLE
            else anchoredAdaptiveBannerSize(activity)
        )
        adView.setOnPaidEventListener { value ->
            AdClickDurationTracker.adPaidEvent(
                appContext, adType, shownReference, value, adView.responseInfo
            )
        }

        adView.adListener = object : AdListener() {
            override fun onAdClicked() {
                super.onAdClicked()
                AdClickDurationTracker.startTracking(appContext,
                    adType = adType,
                    adIdReferenceName = shownReference
                )
            }

            override fun onAdImpression() {
                super.onAdImpression()
                AdClickDurationTracker.adImpression(appContext,
                    adType = adType,
                    adIdReferenceName = shownReference
                )
            }

            override fun onAdLoaded() {
                super.onAdLoaded()
                // AdView also calls this on every automatic refresh; only the first load is ours.
                if (adView.tag == LOADED_TAG) return
                adView.tag = LOADED_TAG
                slot.inFlight = (slot.inFlight - 1).coerceAtLeast(0)
                slot.retry.reset()
                AdClickDurationTracker.adRequestMatch(appContext,
                    adType = adType,
                    adIdReferenceName = adReference
                )
                if (isDebug) {
                    Toast.makeText(appContext, "banner loaded", Toast.LENGTH_SHORT).show()
                }
                val consumer = slot.waiters.removeFirstOrNull()
                if (consumer != null) {
                    shownReference = consumer.adReference
                    AdClickDurationTracker.adShow(appContext,
                        adType = adType,
                        adIdReferenceName = consumer.adReference
                    )
                    consumer.onLoaded(adView)
                } else if (slot.cached == null && activityRef.get() != null) {
                    slot.cached = CachedBanner(adView, activityRef, AdRetryPolicy.now())
                } else {
                    adView.destroy()
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                // A failed automatic refresh keeps showing the previous creative; nothing to do.
                if (adView.tag == LOADED_TAG) return
                slot.inFlight = (slot.inFlight - 1).coerceAtLeast(0)
                AdClickDurationTracker.adRequestFail(appContext,
                    adType = adType,
                    adIdReferenceName = adReference,
                    errorCode = error.code,
                    errorMessage = error.message
                )
                if (isDebug) {
                    Toast.makeText(
                        appContext,
                        "banner failed: ${error.code}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                adView.destroy()
                while (slot.waiters.size > slot.inFlight) {
                    slot.waiters.removeLast().onFailed()
                }
                if (AdRetryPolicy.isRetryable(error.code)) {
                    slot.retry.schedule {
                        val retryActivity = activityRef.get()
                        if (retryActivity != null && !retryActivity.isFinishing && !retryActivity.isDestroyed &&
                            slot.cached == null && slot.inFlight == 0
                        ) {
                            preload(adReference, retryActivity, true, isRectangleBanner)
                        }
                    }
                }
            }
        }

        val request = if (collapsible) {
            val extras = Bundle()
            extras.putString("collapsible", "bottom")
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
        } else {
            AdRequest.Builder().build()
        }
        adView.loadAd(request)
    }

    /** Drops the callbacks registered by a caller that is no longer interested. */
    internal fun cancelPending(onFailed: () -> Unit) {
        slots.values.forEach { slot -> slot.waiters.removeAll { it.onFailed === onFailed } }
    }

    /** Destroys every pooled banner and stops pending retries. */
    fun destroy() {
        slots.values.forEach { slot ->
            slot.retry.cancel()
            slot.cached?.adView?.destroy()
            slot.cached = null
        }
    }

    private companion object {
        const val LOADED_TAG = "ik_banner_loaded"
    }
}

/** Adaptive / medium-rectangle banners. */
class AdmobBannerAd private constructor() : BannerLoader(AdType.BANNER, collapsible = false) {

    companion object {
        @Volatile
        private var INSTANCE: AdmobBannerAd? = null

        fun getInstance(
        ): AdmobBannerAd {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobBannerAd(
                ).also { INSTANCE = it }
            }
        }
    }

    fun hasBannerOrLoading(): Boolean = hasAnyBannerOrLoading()

    fun hasBanner(): Boolean = hasAnyBanner()

    /** Preloads a banner so the next [loadBanner] call on the same Activity can show it immediately. */
    fun loadBannerAd(
        adReference: String,
        context: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean
    ) = preload(adReference, context, enable, isRectangleBanner)

    fun loadBanner(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean = false,
        loadNewAd: Boolean = false,
        onLoaded: (AdView) -> Unit,
        onFailed: () -> Unit = {},
    ) = load(adReference, activity, enable, isRectangleBanner, loadNewAd, onLoaded, onFailed)
}
