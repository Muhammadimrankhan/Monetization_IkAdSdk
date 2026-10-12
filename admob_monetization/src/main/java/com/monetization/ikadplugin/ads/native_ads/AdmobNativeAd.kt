package com.monetization.ikadplugin.ads.native_ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
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
 * At most one request is in flight at a time. A preloaded ad is cached until someone asks for one;
 * a caller that asks while a request is in flight is handed that ad when it arrives.
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
    }

    private var canRequestAd = true
    private var largeAndSmallNativeAd: NativeAd? = null

    /** The caller waiting on the in-flight request, if any. */
    private var pendingConsumer: NativeAdConsumer? = null

    private class NativeAdConsumer(
        val adIdNativeReference: String,
        val onLoaded: (NativeAd) -> Unit,
        val onFailed: () -> Unit,
    )

    fun onDestroyNativeAd(context: Context) {
        try {
            largeAndSmallNativeAd?.destroy()
        } catch (e: Exception) {
            AdClickDurationTracker.warn(context,
                adType = "NATIVE",
                stage = "DESTROY",
                placement = "unknown",
                message = "Failed destroying native ad: ${e.message}"
            )
        } finally {
            largeAndSmallNativeAd = null
        }
    }

    fun hasLargeAdOrLoading(): Boolean {
        return (largeAndSmallNativeAd != null || !canRequestAd)
    }

    fun hasLargeAd(): Boolean {
        return largeAndSmallNativeAd != null
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
            if (canRequest(context, enable) && largeAndSmallNativeAd == null && canRequestAd) {
                requestAd(adIdNativeReference, context)
            }
        } catch (e: Exception) {
            canRequestAd = true
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
     * Hands a native ad to [onLoaded]: the preloaded one if there is one, otherwise a freshly
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

            val cached = largeAndSmallNativeAd
            if (cached != null) {
                largeAndSmallNativeAd = null
                AdClickDurationTracker.adShow(context,
                    adType = AdType.NATIVE,
                    adIdReferenceName = adIdNativeReference
                )
                onLoaded(cached)
                if (loadNewAd) preLoadNativeAd(adIdNativeReference, context, enable)
                return
            }

            // A newer caller takes over the in-flight request; the one it replaces gives up.
            pendingConsumer?.onFailed?.invoke()
            pendingConsumer = NativeAdConsumer(
                adIdNativeReference = adIdNativeReference,
                onLoaded = { ad ->
                    onLoaded(ad)
                    if (loadNewAd) preLoadNativeAd(adIdNativeReference, context, enable)
                },
                onFailed = onFailed,
            )
            if (canRequestAd) requestAd(adIdNativeReference, context)
        } catch (e: Exception) {
            canRequestAd = true
            pendingConsumer = null
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

    private fun requestAd(adIdNativeReference: String, context: Context) {
        canRequestAd = false
        AdClickDurationTracker.adRequestCalling(context,
            adType = AdType.NATIVE,
            adIdReferenceName = adIdNativeReference
        )
        val adId = FetchConfig.getNativeId(adIdNativeReference)
        val builder = AdLoader.Builder(context, adId)
        builder.forNativeAd { newNativeAd: NativeAd ->
            canRequestAd = true
            AdClickDurationTracker.adRequestMatch(context,
                adType = AdType.NATIVE,
                adIdReferenceName = adIdNativeReference
            )
            val consumer = pendingConsumer
            pendingConsumer = null
            if (consumer != null) {
                AdClickDurationTracker.adShow(context,
                    adType = AdType.NATIVE,
                    adIdReferenceName = consumer.adIdNativeReference
                )
                consumer.onLoaded(newNativeAd)
            } else {
                largeAndSmallNativeAd = newNativeAd
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
                AdClickDurationTracker.startTracking(context,
                    adType = AdType.NATIVE,
                    adIdReferenceName = adIdNativeReference
                )
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                AdClickDurationTracker.adRequestFail(context,
                    adType = AdType.NATIVE,
                    adIdReferenceName = adIdNativeReference
                )
                canRequestAd = true
                largeAndSmallNativeAd = null
                val consumer = pendingConsumer
                pendingConsumer = null
                consumer?.onFailed?.invoke()
            }
        }).build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    /** Drops [onLoaded]/[onFailed] registered by a caller that is no longer interested. */
    internal fun cancelPending(onFailed: () -> Unit) {
        if (pendingConsumer?.onFailed === onFailed) pendingConsumer = null
    }
}
