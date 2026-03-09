package com.monetization.ikadplugin.ads.native_ads

import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.ads.FirebaseValue.ALL_ADS_OFF_ENABLE
import com.monetization.ikadplugin.ads.NativeViewPopulate.addLargeNativeView
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference

class NativeAdController(
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager,
    private val prefHelper: AdSharedPreference,
    private val internetController: InternetController
) {
    private var canRequestLargeAd = true
    private var largeAndSmallNativeAd: NativeAd? = null

    private var adControllerListener: AdControllerListener? = null


    fun hasLargeAdOrLoading(): Boolean {
        return (largeAndSmallNativeAd != null || !canRequestLargeAd)
    }

    fun hasLargeAd(): Boolean {
        return largeAndSmallNativeAd != null
    }


    fun setNativeControllerListener(listener: AdControllerListener?) {
        adControllerListener?.resetRequesting()
        adControllerListener = listener
    }

    fun loadNativeOutSide(
        adIdNativeReference: String,
        context: Context, enable: Boolean
    ) {
        setNativeControllerListener(null)
        loadNativeAd(adIdNativeReference, context, enable)
    }

    private fun loadNativeAd(
        adIdNativeReference: String,
      context: Context, enable: Boolean
    ) {
        try {
            if (!ALL_ADS_OFF_ENABLE && googleMobileAdsConsentManager.canRequestAds && enable && !prefHelper.isAppPurchased && internetController.isInternetConnected) {
                if (largeAndSmallNativeAd == null) {
                    if (!canRequestLargeAd) {
                        return
                    }
                    canRequestLargeAd = false
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(context, "large native ad calling", Toast.LENGTH_SHORT)
                            .show()
                    }
                    val adId = FetchConfig.getNativeId(adIdNativeReference)
                    val builder = AdLoader.Builder(
                        context, adId
                    )
                    builder.forNativeAd { newNativeAd: NativeAd ->
                        canRequestLargeAd = true
                        if (BuildConfig.DEBUG) {
                            Toast.makeText(
                                context, "native ad loaded", Toast.LENGTH_SHORT
                            ).show()
                        }
                        largeAndSmallNativeAd = newNativeAd
                        adControllerListener?.onAdLoaded()

                    }
                    builder.withNativeAdOptions(
                        NativeAdOptions.Builder().setVideoOptions(
                            VideoOptions.Builder().setStartMuted(true).build()
                        ).build()
                    )

                    val adLoader = builder.withAdListener(object : AdListener() {
                        override fun onAdImpression() {
                            super.onAdImpression()
                            adControllerListener?.onAdImpression()
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            canRequestLargeAd = true
                            largeAndSmallNativeAd = null
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    context,
                                    "large native load failed ==> code " + loadAdError.code,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            adControllerListener?.onAdFailed()
                        }
                    }).build()
                    adLoader.loadAd(AdRequest.Builder().build())
                }
            } else {
                adControllerListener?.onAdFailed()
            }
        } catch (e: Exception) {
            adControllerListener?.onAdFailed()
        }
    }


    fun populateNativeAd(
        adViewType: Int,
        adIdNativeReference: String,
        context: Context,
        enable: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = true,
        populateCallback: (Any) -> Unit
    ) {
        if (enable && !prefHelper.isAppPurchased && largeAndSmallNativeAd != null) {
            largeAndSmallNativeAd?.let {
                try {
                    addLargeNativeView(context, adFrame, it, adViewType)
                    populateCallback.invoke(it)
                    largeAndSmallNativeAd = null
                    if (loadNewAd) {
                        loadNativeAd(adIdNativeReference, context, enable)
                    }
                } catch (_: Exception) {

                }
            }
        } else {
            loadNativeAd(adIdNativeReference, context, enable)
        }
    }


    fun loadNewNativeAd(
        adIdNativeReference: String,
        context: Context,
        enable: Boolean,
    ) {
        setNativeControllerListener(null)
        loadNativeAd(adIdNativeReference, context, enable)
    }


}


