package com.monetization.ikadplugin.ads.collapse

import android.app.Activity
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads.banner.anchoredAdaptiveBannerSize
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference

/**
 * Loads bottom-anchored collapsible banners. The loaded [AdView] is handed to Compose
 * (`BannerAdSlot` / `IkComposeCollapsibleBannerAd`), which hosts it.
 */
class AdmobCollapsibleBannerAd {

    companion object {

        @Volatile
        private var INSTANCE: AdmobCollapsibleBannerAd? = null

        fun getInstance(
        ): AdmobCollapsibleBannerAd {

            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobCollapsibleBannerAd(
                ).also { INSTANCE = it }
            }
        }
    }

    private var bannerAd: AdView? = null
    private var canRequestAd = true

    /** The caller waiting on the in-flight request, if any. */
    private var pendingConsumer: BannerConsumer? = null

    private class BannerConsumer(
        val adReference: String,
        val onLoaded: (AdView) -> Unit,
        val onFailed: () -> Unit,
    )

    fun hasAd(): Boolean {
        return bannerAd != null
    }

    fun hasAdOrLoading(): Boolean {
        return bannerAd != null || !canRequestAd
    }

    private fun canRequest(activity: Activity, enable: Boolean): Boolean =
        !FirebaseValue.ALL_ADS_OFF_ENABLE &&
            enable &&
            GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds &&
            !AdSharedPreference.getInstance(activity).isAppPurchased &&
            InternetController.getInstance(activity).isInternetConnected

    /** Preloads a collapsible banner so the next [loadCollapsibleBanner] can show it immediately. */
    fun loadAd(
        adReference: String,
        activity: Activity,
        enable: Boolean
    ) {
        try {
            if (canRequest(activity, enable) && bannerAd == null && canRequestAd) {
                requestAd(adReference, activity)
            }
        } catch (e: Exception) {
            canRequestAd = true
            bannerAd = null
        }
    }

    /**
     * Hands a collapsible banner to [onLoaded]: the preloaded one if there is one, otherwise a
     * freshly requested one. [onFailed] fires when ads are suppressed or the request fails. With
     * [loadNewAd] the next banner is preloaded once this one is handed out.
     */
    fun loadCollapsibleBanner(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        loadNewAd: Boolean = false,
        onLoaded: (AdView) -> Unit,
        onFailed: () -> Unit = {},
    ) {
        try {
            if (!canRequest(activity, enable)) {
                onFailed()
                return
            }

            val cached = bannerAd
            if (cached != null) {
                bannerAd = null
                AdClickDurationTracker.adShow(activity,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
                onLoaded(cached)
                if (loadNewAd) loadAd(adReference, activity, enable)
                return
            }

            pendingConsumer?.onFailed?.invoke()
            pendingConsumer = BannerConsumer(
                adReference = adReference,
                onLoaded = { adView ->
                    onLoaded(adView)
                    if (loadNewAd) loadAd(adReference, activity, enable)
                },
                onFailed = onFailed,
            )
            if (canRequestAd) requestAd(adReference, activity)
        } catch (e: Exception) {
            canRequestAd = true
            pendingConsumer = null
            onFailed()
        }
    }

    private fun requestAd(adReference: String, activity: Activity) {
        canRequestAd = false
        AdClickDurationTracker.adRequestCalling(activity,
            adType = AdType.BANNER,
            adIdReferenceName = adReference
        )

        val adView = AdView(activity)
        adView.adUnitId = FetchConfig.getBannerId(adReference)
        adView.setAdSize(anchoredAdaptiveBannerSize(activity))

        val extras = Bundle()
        extras.putString("collapsible", "bottom")

        val request = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()

        adView.adListener = object : AdListener() {
            override fun onAdClicked() {
                super.onAdClicked()
                AdClickDurationTracker.startTracking(activity,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
            }

            override fun onAdLoaded() {
                canRequestAd = true
                AdClickDurationTracker.adRequestMatch(activity,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
                val consumer = pendingConsumer
                pendingConsumer = null
                if (consumer != null) {
                    AdClickDurationTracker.adShow(activity,
                        adType = AdType.BANNER,
                        adIdReferenceName = consumer.adReference
                    )
                    consumer.onLoaded(adView)
                } else {
                    bannerAd = adView
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                AdClickDurationTracker.adRequestFail(activity,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
                canRequestAd = true
                bannerAd = null
                val consumer = pendingConsumer
                pendingConsumer = null
                consumer?.onFailed?.invoke()
            }
        }

        adView.loadAd(request)
    }

    /** Drops the callbacks registered by a caller that is no longer interested. */
    internal fun cancelPending(onFailed: () -> Unit) {
        if (pendingConsumer?.onFailed === onFailed) pendingConsumer = null
    }

    fun destroy() {
        bannerAd?.destroy()
        bannerAd = null
    }
}
