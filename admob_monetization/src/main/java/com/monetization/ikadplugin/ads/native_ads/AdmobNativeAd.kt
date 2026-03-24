package com.monetization.ikadplugin.ads.native_ads

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads.NativeViewPopulate
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.BuildConfig

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


    fun hasLargeAdOrLoading(): Boolean {
        return (largeAndSmallNativeAd != null || !canRequestAd)
    }

    fun hasLargeAd(): Boolean {
        return largeAndSmallNativeAd != null
    }

    fun loadNativeAd(
        adIdNativeReference: String, adLayout: LinearLayout, context: Context, enable: Boolean
    ) {
        try {
            if (!FirebaseValue.ALL_ADS_OFF_ENABLE && GoogleMobileAdsConsentManager.getInstance(context).canRequestAds && enable && !AdSharedPreference.getInstance(context).isAppPurchased && InternetController.getInstance(context).isInternetConnected) {
                if (largeAndSmallNativeAd == null) {
                    if (!canRequestAd) {
                        return
                    }
                    canRequestAd = false
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(context, "large native ad calling", Toast.LENGTH_SHORT)
                            .show()
                    }
                    val adId = FetchConfig.getNativeId(adIdNativeReference)
                    val builder = AdLoader.Builder(
                        context, adId
                    )
                    builder.forNativeAd { newNativeAd: NativeAd ->
                        canRequestAd = true
                        if (BuildConfig.DEBUG) {
                            Toast.makeText(
                                context, "native ad loaded", Toast.LENGTH_SHORT
                            ).show()
                        }
                        largeAndSmallNativeAd = newNativeAd
                    }
                    builder.withNativeAdOptions(
                        NativeAdOptions.Builder().setVideoOptions(
                            VideoOptions.Builder().setStartMuted(true).build()
                        ).build()
                    )

                    val adLoader = builder.withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            canRequestAd = true
                            largeAndSmallNativeAd = null
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    context,
                                    "large native load failed ==> code " + loadAdError.code,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            adLayout.removeAllViews()
                            adLayout.visibility = View.GONE
                        }
                    }).build()
                    adLoader.loadAd(AdRequest.Builder().build())
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

//    populateCallback: (Any) -> Unit
//    populateCallback.invoke(it)

    fun populateNativeAd(
        adLayout: LinearLayout,
        adViewType: Int,
        adIdNativeReference: String,
        context: Context,
        enable: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = false
    ) {
        if (enable && !AdSharedPreference.getInstance(context).isAppPurchased && largeAndSmallNativeAd != null) {
            largeAndSmallNativeAd?.let {
                try {
                    NativeViewPopulate.addLargeNativeView(context, adFrame, it, adViewType)
                    largeAndSmallNativeAd = null
                    if (loadNewAd) {
                        loadNativeAd(adIdNativeReference, adLayout, context, enable)
                    }
                } catch (_: Exception) {

                }
            }
        } else {
            loadAndShowNativeAd(adIdNativeReference, adLayout, context, enable, adViewType)
        }
    }

    private fun loadAndShowNativeAd(
        adIdNativeReference: String,
        adLayout: LinearLayout,
        context: Context,
        enable: Boolean,
        adViewType: Int,
        loadNewAd: Boolean = false
    ) {
        try {
            if (!FirebaseValue.ALL_ADS_OFF_ENABLE && GoogleMobileAdsConsentManager.getInstance(context).canRequestAds && enable && !AdSharedPreference.getInstance(context).isAppPurchased && InternetController.getInstance(context).isInternetConnected) {
                if (largeAndSmallNativeAd == null) {
                    if (!canRequestAd) {
                        return
                    }
                    canRequestAd = false
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(context, "large native ad calling", Toast.LENGTH_SHORT)
                            .show()
                    }
                    val adId = FetchConfig.getNativeId(adIdNativeReference)
                    val builder = AdLoader.Builder(
                        context, adId
                    )
                    builder.forNativeAd { newNativeAd: NativeAd ->
                        canRequestAd = true
                        if (BuildConfig.DEBUG) {
                            Toast.makeText(
                                context, "native ad loaded", Toast.LENGTH_SHORT
                            ).show()
                        }
                        largeAndSmallNativeAd = newNativeAd
                        largeAndSmallNativeAd?.let {
                            NativeViewPopulate.addLargeNativeView(context, adLayout, it, adViewType)
                            largeAndSmallNativeAd = null
                            if (loadNewAd) {
                                loadNativeAd(adIdNativeReference, adLayout, context, enable)
                            }
                        }
                    }
                    builder.withNativeAdOptions(
                        NativeAdOptions.Builder().setVideoOptions(
                            VideoOptions.Builder().setStartMuted(true).build()
                        ).build()
                    )

                    val adLoader = builder.withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            canRequestAd = true
                            largeAndSmallNativeAd = null
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    context,
                                    "large native load failed ==> code " + loadAdError.code,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            adLayout.removeAllViews()
                            adLayout.visibility = View.GONE
                        }
                    }).build()
                    adLoader.loadAd(AdRequest.Builder().build())
                } else {
                    largeAndSmallNativeAd?.let {
                        NativeViewPopulate.addLargeNativeView(context, adLayout, it, adViewType)
                        largeAndSmallNativeAd = null
                        if (loadNewAd) {
                            loadNativeAd(adIdNativeReference, adLayout, context, enable)
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