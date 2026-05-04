package com.monetization.ikadplugin.ads.banner

import com.monetization.ikadplugin.BuildConfig
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads.NativeShimmerEffect.addShimmerLayout
import com.monetization.ikadplugin.ads.NativeViewPopulate
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.subscription.SubscriptionConstant.isDebug

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

    fun hasBannerOrLoading(): Boolean {
        return bannerAdView != null || !canRequestAd
    }

    fun hasBanner(): Boolean {
        return bannerAdView != null
    }

    fun loadBannerAd(
        adReference: String,
        context: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean
    ) {
        try {
            if (!FirebaseValue.ALL_ADS_OFF_ENABLE &&
                GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
                enable &&
                !AdSharedPreference.getInstance(context).isAppPurchased &&
                InternetController.getInstance(context).isInternetConnected
            ) {

                if (bannerAdView == null) {

                    if (!canRequestAd) return

                    canRequestAd = false

                    if (isDebug) {
                        Toast.makeText(context, "banner ad calling", Toast.LENGTH_SHORT).show()
                    }

                    val adId = FetchConfig.getBannerId(adReference)

                    val bannerSize =
                        if (isRectangleBanner) AdSize.MEDIUM_RECTANGLE
                        else NativeViewPopulate.getAdSize(context)

                    val adView = AdView(context)
                    adView.adUnitId = adId
                    adView.setAdSize(bannerSize)

                    adView.adListener = object : AdListener() {

                        override fun onAdClicked() {
                            super.onAdClicked()

                            AdClickDurationTracker.startTracking(
                                adType = AdType.BANNER,
                                adIdReferenceName = adReference
                            )
                        }
                        override fun onAdLoaded() {
                            super.onAdLoaded()

                            canRequestAd = true
                            bannerAdView = adView
                            AdClickDurationTracker.adRequestMatch(
                                adType = AdType.BANNER,
                                adIdReferenceName = adReference
                            )
                            if (isDebug) {
                                Toast.makeText(context, "banner loaded", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            super.onAdFailedToLoad(error)

                            canRequestAd = true
                            bannerAdView = null
                            AdClickDurationTracker.adRequestFail(
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
                        }
                    }

                    adView.loadAd(AdRequest.Builder().build())
                }

            }
        } catch (e: Exception) {
            bannerAdView = null
            canRequestAd = true
        }
    }

    fun populateBannerAd(
        adReference: String,
        context: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean,
        adLayout: LinearLayout,
        loadNewAd: Boolean = false
    ) {

        if (enable && !AdSharedPreference.getInstance(context).isAppPurchased && bannerAdView != null) {

            bannerAdView?.let {
                try {
                    adLayout.visibility = View.VISIBLE
                    it.parent?.let { parent ->
                        (parent as ViewGroup).removeAllViews()
                    }
                    adLayout.visibility = View.VISIBLE
                    adLayout.removeAllViews()
                    adLayout.addView(it)
                    bannerAdView = null
                    AdClickDurationTracker.adShow(
                        adType = AdType.BANNER,
                        adIdReferenceName = adReference
                    )
                    if (loadNewAd) {
                        loadBannerAd(adReference, context, enable, isRectangleBanner)
                    }

                } catch (_: Exception) {
                }
            }

        } else {

            loadAndShowBannerAd(
                adReference,
                context,
                enable,
                isRectangleBanner,
                adLayout,
                loadNewAd
            )
        }
    }

     fun loadAndShowBannerAd(
        adReference: String,
        context: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean,
        adLayout: LinearLayout,
        loadNewAd: Boolean = false
    ) {

        try {

            if (!FirebaseValue.ALL_ADS_OFF_ENABLE &&
                GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
                enable &&
                !AdSharedPreference.getInstance(context).isAppPurchased &&
                InternetController.getInstance(context).isInternetConnected
            ) {

                if (bannerAdView == null) {

                    if (!canRequestAd) return

                    canRequestAd = false
                    addShimmerLayout(adLayout, 2, context)

                    val adId = FetchConfig.getBannerId(adReference)

                    val bannerSize =
                        if (isRectangleBanner) AdSize.MEDIUM_RECTANGLE
                        else NativeViewPopulate.getAdSize(context)

                    val adView = AdView(context)
                    adView.adUnitId = adId
                    adView.setAdSize(bannerSize)

                    adView.adListener = object : AdListener() {
                        override fun onAdClicked() {
                            super.onAdClicked()

                            AdClickDurationTracker.startTracking(
                                adType = AdType.BANNER,
                                adIdReferenceName = adReference
                            )
                        }

                        override fun onAdLoaded() {

                            canRequestAd = true
                            bannerAdView = adView

                            bannerAdView?.let {

                                it.parent?.let { parent ->
                                    (parent as ViewGroup).removeAllViews()
                                }

                                adLayout.visibility = View.VISIBLE
                                adLayout.removeAllViews()
                                adLayout.addView(it)
                                AdClickDurationTracker.adShow(
                                    adType = AdType.BANNER,
                                    adIdReferenceName = adReference
                                )
                                bannerAdView = null

                                if (loadNewAd) {
                                    loadBannerAd(adReference, context, enable, isRectangleBanner)
                                }
                            }
                            AdClickDurationTracker.adRequestMatch(
                                adType = AdType.BANNER,
                                adIdReferenceName = adReference
                            )
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {

                            canRequestAd = true
                            bannerAdView = null
                            AdClickDurationTracker.adRequestFail(
                                adType = AdType.BANNER,
                                adIdReferenceName = adReference
                            )

                            adLayout.removeAllViews()
                            adLayout.visibility = View.GONE
                        }
                    }

                    adView.loadAd(AdRequest.Builder().build())

                } else {

                    bannerAdView?.let {
                        adLayout.visibility = View.VISIBLE
                        adLayout.removeAllViews()
                        adLayout.addView(it)
                        AdClickDurationTracker.adShow(
                            adType = AdType.BANNER,
                            adIdReferenceName = adReference
                        )
                        bannerAdView = null

                        if (loadNewAd) {
                            loadBannerAd(adReference, context, enable, isRectangleBanner)
                        }
                    }
                }

            } else {

                adLayout.removeAllViews()
                adLayout.visibility = View.GONE
            }

        } catch (e: Exception) {

            adLayout.removeAllViews()
            adLayout.visibility = View.GONE
        }
    }
}