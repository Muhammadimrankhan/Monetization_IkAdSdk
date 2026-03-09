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
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.ads.FirebaseValue.ALL_ADS_OFF_ENABLE
import com.monetization.ikadplugin.ads.banner.BaseBannerActivity
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig

abstract class BaseCollapsableBannerActivity : BaseBannerActivity() {
    private var bannerAd: AdView? = null
    private var adFrame: LinearLayout? = null

    fun destroyCollapsableBannerAd() {
        bannerAd?.destroy()
        try {
            adFrame?.removeAllViews()
        } catch (_: Exception) {
        }
        bannerAd = null
    }

    private fun getAdSize(activity: Activity): AdSize {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val bounds: Rect = windowMetrics.bounds
            val adWidthPixels = bounds.width()
            val density: Float = activity.resources.displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        } else {
            val display = activity.windowManager.defaultDisplay
            val outMetrics = DisplayMetrics()
            display.getMetrics(outMetrics)
            val widthPixels = outMetrics.widthPixels.toFloat()
            val density = outMetrics.density
            val adWidth = (widthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        }
    }

    fun loadCollapsableBannerAd(adRef: String,
        isAdEnabled: Boolean, adFrame: LinearLayout
    ) {
        if (ALL_ADS_OFF_ENABLE || !isAdEnabled || prefHelper.isAppPurchased || (!internetController.isInternetConnected && bannerAd == null)) {
            adFrame.let {
                it.visibility = View.GONE
                it.removeAllViews()
            }
        } else {
            adFrame.let {
                it.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                if (bannerAd == null) {
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(this, "collapse banner ad calling", Toast.LENGTH_SHORT)
                            .show()
                    }
                    bannerAdController.addShimmerLayout(
                        false, it, this
                    )
                    val adId = FetchConfig.getBannerId(adRef)

                    val collapseBannerAd = AdView(this).apply {
                        this.adUnitId = adId
                        this.setAdSize(getAdSize(this@BaseCollapsableBannerActivity))
                        this.loadAd(
                            AdRequest.Builder()
                                .addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply {
                                    putString("collapsible", "bottom")
                                }).build()
                        )
                    }
                    collapseBannerAd.adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            if (isFinishing || isDestroyed || isChangingConfigurations) {
                                collapseBannerAd.destroy()
                                return
                            }
                            collapseBannerAd.adListener = object : AdListener() {}
                            bannerAd = collapseBannerAd
                            adFrame.visibility = View.VISIBLE
                            adFrame.removeAllViews()
                            adFrame.addView(bannerAd)
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    this@BaseCollapsableBannerActivity,
                                    "collapse banner ad loaded",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            if (isFinishing || isDestroyed || isChangingConfigurations) {
                                collapseBannerAd.destroy()
                                return
                            }
                            bannerAd = null
                            adFrame.removeAllViews()
                            adFrame.visibility = View.GONE
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    this@BaseCollapsableBannerActivity,
                                    "collapse banner load failed ==> code " + p0.code,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAd?.resume()
    }

    override fun onPause() {
        bannerAd?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        destroyCollapsableBannerAd()
        super.onDestroy()
    }
}