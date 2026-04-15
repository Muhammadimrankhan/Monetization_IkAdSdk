package com.monetization.ikadplugin.ads.collapse

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowMetrics
import android.widget.LinearLayout
import android.widget.Toast
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.BuildConfig

class AdmobCollapsibleBannerAd{

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

    fun hasAd(): Boolean {
        return bannerAd != null
    }

    fun hasAdOrLoading(): Boolean {
        return bannerAd != null || !canRequestAd
    }

    private fun getAdSize(activity: Activity): AdSize {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val bounds: Rect = windowMetrics.bounds
            val adWidthPixels = bounds.width()

            val density = activity.resources.displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()

            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)

        } else {

            val display = activity.windowManager.defaultDisplay
            val outMetrics = DisplayMetrics()
            display.getMetrics(outMetrics)

            val widthPixels = outMetrics.widthPixels.toFloat()
            val density = outMetrics.density
            val adWidth = (widthPixels / density).toInt()

            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        }
    }

    fun loadAd(
        adReference: String,
        activity: Activity,
        enable: Boolean
    ) {

        try {

            if (FirebaseValue.ALL_ADS_OFF_ENABLE ||
                !enable ||
                AdSharedPreference.getInstance(activity).isAppPurchased ||
                !InternetController.getInstance(activity).isInternetConnected
            ) return

            if (bannerAd == null) {

                if (!canRequestAd) return

                canRequestAd = false

                if (BuildConfig.DEBUG) {
                    Toast.makeText(activity, "collapsible banner calling", Toast.LENGTH_SHORT).show()
                }

                val adId = FetchConfig.getBannerId(adReference)

                val adView = AdView(activity)
                adView.adUnitId = adId
                adView.setAdSize(getAdSize(activity))

                val extras = Bundle()
                extras.putString("collapsible", "bottom")

                val request = AdRequest.Builder()
                    .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                    .build()

                adView.adListener = object : AdListener() {

                    override fun onAdLoaded() {

                        canRequestAd = true
                        bannerAd = adView

                        if (BuildConfig.DEBUG) {
                            Toast.makeText(activity, "collapse banner loaded", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {

                        canRequestAd = true
                        bannerAd = null

                        if (BuildConfig.DEBUG) {
                            Toast.makeText(
                                activity,
                                "collapse banner failed ${error.code}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                adView.loadAd(request)
            }

        } catch (e: Exception) {

            canRequestAd = true
            bannerAd = null
        }
    }

    fun populateAd(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = false
    ) {

        if (enable && !AdSharedPreference.getInstance(activity).isAppPurchased && bannerAd != null) {

            bannerAd?.let {

                try {

                    adFrame.visibility = View.VISIBLE

                    it.parent?.let { parent ->
                        (parent as ViewGroup).removeAllViews()
                    }

                    adFrame.removeAllViews()
                    adFrame.visibility = View.VISIBLE
                    adFrame.addView(it)

                    bannerAd = null

                    if (loadNewAd) {
                        loadAd(adReference, activity, enable)
                    }

                } catch (_: Exception) {
                }
            }
        } else {
            loadAndShowAd(adReference, activity, enable, adFrame)
        }
    }

    private fun loadAndShowAd(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        adFrame: LinearLayout
    ) {

        try {

            if (FirebaseValue.ALL_ADS_OFF_ENABLE ||
                !enable ||
                !GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds ||
                AdSharedPreference.getInstance(activity).isAppPurchased ||
                !InternetController.getInstance(activity).isInternetConnected
            ) {
                adFrame.removeAllViews()
                adFrame.visibility = View.GONE
                return
            }

            if (!canRequestAd) return

            canRequestAd = false

            val adId = FetchConfig.getBannerId(adReference)

            val adView = AdView(activity)
            adView.adUnitId = adId
            adView.setAdSize(getAdSize(activity))

            val extras = Bundle()
            extras.putString("collapsible", "bottom")

            val request = AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()

            adView.adListener = object : AdListener() {

                override fun onAdLoaded() {
                    canRequestAd = true
                    adFrame.visibility = View.VISIBLE
                    adFrame.removeAllViews()
                    adFrame.addView(adView)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    canRequestAd = true
                    adFrame.removeAllViews()
                    adFrame.visibility = View.GONE
                }
            }

            adView.loadAd(request)

        } catch (e: Exception) {
            adFrame.removeAllViews()
            adFrame.visibility = View.GONE
        }
    }

    fun destroy() {
        bannerAd?.destroy()
        bannerAd = null
    }
}