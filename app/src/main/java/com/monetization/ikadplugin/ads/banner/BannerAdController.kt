package com.monetization.ikadplugin.ads.banner

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdSize.MEDIUM_RECTANGLE
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.imageview.ShapeableImageView
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.R
import com.monetization.ikadplugin.ads.FirebaseValue.ALL_ADS_OFF_ENABLE
import com.monetization.ikadplugin.ads.NativeViewPopulate.getAdSize
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.ads.native_ads.AdControllerListener
import com.monetization.ikadplugin.ads.shimmer_effect.ShimmerFrameLayout
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.pref.AdSharedPreference

class BannerAdController(
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager,
    private val prefHelper: AdSharedPreference,
    private val internetController: InternetController,
) {
    private var canRequestBannerAd = true
    private var adView: AdView? = null
    private var adControllerListener: AdControllerListener? = null
    private var bannerSize: AdSize? = null

    fun setAdControllerListener(listener: AdControllerListener?) {
        adControllerListener?.resetRequesting()
        adControllerListener = listener
    }

    fun loadNewBannerAd(
        isRectangleBanner: Boolean,
        context: Activity, enable: Boolean, adRef: String
    ) {
        setAdControllerListener(null)
        loadBannerAd(isRectangleBanner, context, enable, adRef)
    }

    private fun loadBannerAd(
        isRectangleBanner: Boolean,
        context: Activity,
        enable: Boolean,
        adRef: String
    ) {
        try {
            if (!ALL_ADS_OFF_ENABLE && googleMobileAdsConsentManager.canRequestAds && enable && !prefHelper.isAppPurchased && internetController.isInternetConnected) {
                if (adView == null) {
                    if (!canRequestBannerAd) {
                        return
                    }
                    canRequestBannerAd = false
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(context, "banner ad calling", Toast.LENGTH_SHORT).show()
                    }
                    val adId = FetchConfig.getBannerId(adRef)

                    if (isRectangleBanner) {
                        bannerSize = MEDIUM_RECTANGLE
                    } else {
                        bannerSize = getAdSize(context)
                    }
//                    if (!::bannerSize.isInitialized) {
//                        bannerSize = getAdSize(context)
//                    }
                    val bannerAd = AdView(context).apply {
                        this.adUnitId = adId
                        this.setAdSize(bannerSize!!)
                        this.loadAd(AdRequest.Builder().build())
                    }
                    bannerAd.adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            canRequestBannerAd = true
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(context, "banner ad loaded", Toast.LENGTH_SHORT)
                                    .show()
                            }
                            adView = bannerAd
                            adControllerListener?.onAdLoaded()
                            bannerSize = null
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            canRequestBannerAd = true
                            adView = null
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    context,
                                    "banner load failed ==> code " + p0.code,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            bannerSize = null
                            adControllerListener?.onAdFailed()
                        }
                    }
                }
            } else {
                adControllerListener?.onAdFailed()
                canRequestBannerAd = true
                adView = null
                bannerSize = null

            }
        } catch (ignored: Exception) {
        }
    }

    private fun getSmallNativeAd(
        context: Context
    ): View {
        return LayoutInflater.from(context).inflate(R.layout.small_button_native_layout, null)
    }

    private fun getLargeNativeAd(
        context: Context
    ): View {
        return LayoutInflater.from(context).inflate(R.layout.large_native_layout, null)
    }

    fun addShimmerLayout(
        isRectangleBanner: Boolean, adFrame: LinearLayout, context: Context
    ) {
        val shimmerNewView: View = if (isRectangleBanner) {
            getLargeNativeAd(context)
        } else {
            getSmallNativeAd(context)
        }
        setShimmerColorOnView(context, shimmerNewView)

        val shimmerContainer = LayoutInflater.from(context)
            .inflate(R.layout.shimmer_layout, null) as ShimmerFrameLayout
        try {
            shimmerContainer.parent?.let { parent ->
                (parent as ViewGroup).removeAllViews()
            }
        } catch (_: Exception) {
        }
        adFrame.visibility = View.VISIBLE
        try {
            adFrame.removeAllViews()
        } catch (_: Exception) {
        }
        shimmerContainer.addView(
            shimmerNewView
        )
        adFrame.addView(shimmerContainer)
    }

    private fun setShimmerColorOnView(
        context: Context, shimmerNewView: View
    ) {
        try {
            val shimmerColor = ContextCompat.getColor(context, R.color.shimmer)
            val adButton = shimmerNewView.findViewById<AppCompatButton>(R.id.ad_call_to_action)
            val icon = shimmerNewView.findViewById<ShapeableImageView>(R.id.ad_app_icon)
            val adText = shimmerNewView.findViewById<TextView>(R.id.ads_text_ads)
            val textLayout = shimmerNewView.findViewById<LinearLayout>(R.id.text_layout)
            adButton.background = ContextCompat.getDrawable(context, R.drawable.shimmer_new_ads_btn)
            icon.setBackgroundColor(shimmerColor)
            textLayout.setBackgroundColor(shimmerColor)
            adText.setBackgroundColor(shimmerColor)
        } catch (ignore: Exception) {
        }
    }

    fun populateBannerAd(
        isRectangleBanner: Boolean,
        adRef: String,
        context: Activity,
        enable: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = false
    ) {
        try {
            if (enable && !prefHelper.isAppPurchased && adView != null) {
                adView?.let {
                    try {
                        adFrame.visibility = View.VISIBLE
                        try {
                            it.parent?.let { parent ->
                                (parent as ViewGroup).removeAllViews()
                            }
                            adFrame.removeAllViews()
                        } catch (_: Exception) {
                        }
                        adFrame.addView(it)
                        adControllerListener?.onPopulateAd(it)
                        adView = null
                        if (loadNewAd) {
                            loadBannerAd(isRectangleBanner, context, enable, adRef)
                        }
                    } catch (_: Exception) {
                    }
                }
            } else {
                loadBannerAd(isRectangleBanner, context, enable, adRef)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}