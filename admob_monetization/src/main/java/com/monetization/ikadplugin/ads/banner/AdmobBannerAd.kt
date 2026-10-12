package com.monetization.ikadplugin.ads.banner

import android.app.Activity
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.subscription.SubscriptionConstant.isDebug

/**
 * Loads adaptive / medium-rectangle banners. The loaded [AdView] is handed to Compose
 * (`BannerAdSlot` / `IkComposeBannerAd`), which hosts it.
 */
class AdmobBannerAd {

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

    private var canRequestAd = true
    private var bannerAdView: AdView? = null

    /** The caller waiting on the in-flight request, if any. */
    private var pendingConsumer: BannerConsumer? = null

    private class BannerConsumer(
        val adReference: String,
        val onLoaded: (AdView) -> Unit,
        val onFailed: () -> Unit,
    )

    fun hasBannerOrLoading(): Boolean {
        return bannerAdView != null || !canRequestAd
    }

    fun hasBanner(): Boolean {
        return bannerAdView != null
    }

    private fun canRequest(activity: Activity, enable: Boolean): Boolean =
        !FirebaseValue.ALL_ADS_OFF_ENABLE &&
            GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds &&
            enable &&
            !AdSharedPreference.getInstance(activity).isAppPurchased &&
            InternetController.getInstance(activity).isInternetConnected

    /** Preloads a banner so the next [loadBanner] call can show it immediately. */
    fun loadBannerAd(
        adReference: String,
        context: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean
    ) {
        try {
            if (canRequest(context, enable) && bannerAdView == null && canRequestAd) {
                requestAd(adReference, context, isRectangleBanner)
            }
        } catch (e: Exception) {
            bannerAdView = null
            canRequestAd = true
        }
    }

    /**
     * Hands a banner to [onLoaded]: the preloaded one if there is one, otherwise a freshly
     * requested one. [onFailed] fires when ads are suppressed or the request fails. With
     * [loadNewAd] the next banner is preloaded once this one is handed out.
     */
    fun loadBanner(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean = false,
        loadNewAd: Boolean = false,
        onLoaded: (AdView) -> Unit,
        onFailed: () -> Unit = {},
    ) {
        try {
            if (!canRequest(activity, enable)) {
                onFailed()
                return
            }

            val cached = bannerAdView
            if (cached != null) {
                bannerAdView = null
                AdClickDurationTracker.adShow(activity,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
                onLoaded(cached)
                if (loadNewAd) loadBannerAd(adReference, activity, enable, isRectangleBanner)
                return
            }

            pendingConsumer?.onFailed?.invoke()
            pendingConsumer = BannerConsumer(
                adReference = adReference,
                onLoaded = { adView ->
                    onLoaded(adView)
                    if (loadNewAd) loadBannerAd(adReference, activity, enable, isRectangleBanner)
                },
                onFailed = onFailed,
            )
            if (canRequestAd) requestAd(adReference, activity, isRectangleBanner)
        } catch (e: Exception) {
            canRequestAd = true
            pendingConsumer = null
            onFailed()
        }
    }

    private fun requestAd(adReference: String, context: Activity, isRectangleBanner: Boolean) {
        canRequestAd = false
        AdClickDurationTracker.adRequestCalling(context,
            adType = AdType.BANNER,
            adIdReferenceName = adReference
        )

        val adView = AdView(context)
        adView.adUnitId = FetchConfig.getBannerId(adReference)
        adView.setAdSize(
            if (isRectangleBanner) AdSize.MEDIUM_RECTANGLE
            else anchoredAdaptiveBannerSize(context)
        )

        adView.adListener = object : AdListener() {
            override fun onAdClicked() {
                super.onAdClicked()
                AdClickDurationTracker.startTracking(context,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
            }

            override fun onAdLoaded() {
                super.onAdLoaded()
                canRequestAd = true
                AdClickDurationTracker.adRequestMatch(context,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
                if (isDebug) {
                    Toast.makeText(context, "banner loaded", Toast.LENGTH_SHORT).show()
                }
                val consumer = pendingConsumer
                pendingConsumer = null
                if (consumer != null) {
                    AdClickDurationTracker.adShow(context,
                        adType = AdType.BANNER,
                        adIdReferenceName = consumer.adReference
                    )
                    consumer.onLoaded(adView)
                } else {
                    bannerAdView = adView
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                canRequestAd = true
                bannerAdView = null
                AdClickDurationTracker.adRequestFail(context,
                    adType = AdType.BANNER,
                    adIdReferenceName = adReference
                )
                if (isDebug) {
                    Toast.makeText(
                        context,
                        "banner failed: ${error.code}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                val consumer = pendingConsumer
                pendingConsumer = null
                consumer?.onFailed?.invoke()
            }
        }

        adView.loadAd(AdRequest.Builder().build())
    }

    /** Drops the callbacks registered by a caller that is no longer interested. */
    internal fun cancelPending(onFailed: () -> Unit) {
        if (pendingConsumer?.onFailed === onFailed) pendingConsumer = null
    }
}
