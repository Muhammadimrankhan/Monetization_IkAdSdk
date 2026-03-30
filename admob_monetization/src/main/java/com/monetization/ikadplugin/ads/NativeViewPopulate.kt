package com.monetization.ikadplugin.ads

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowMetrics
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.monetization.ikadplugin.R
import com.monetization.ikadplugin.ads.FirebaseValue.everyNativeCtaColorList
import com.monetization.ikadplugin.ads.FirebaseValue.nativeAdsAttributionColorChange
import com.monetization.ikadplugin.ads.FirebaseValue.nativeAdsBgColorChange
import com.monetization.ikadplugin.ads.FirebaseValue.nativeButtonRectangle
import com.monetization.ikadplugin.ads.NativeAdLayouts.getNativeAdLayout

object NativeViewPopulate {
    private fun populateUnifiedNativeAdView(
        nativeAd: NativeAd, adView: NativeAdView, adViewType: Int
    ) {
        if (adViewType >= 3) {
            val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
            adView.mediaView = mediaView
            mediaView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    try {
                        if (child !is ImageView) {
                            return
                        }
                        child.adjustViewBounds = true
                        child.scaleType = ImageView.ScaleType.CENTER_CROP
                    } catch (_: Exception) {
                    }
                }

                override fun onChildViewRemoved(parent: View?, child: View?) {
                }

            })

        }
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        val button = adView.findViewById<Button>(R.id.ad_call_to_action)
        val adText = adView.findViewById<TextView>(R.id.ads_text_ads)
        if (nativeAdsAttributionColorChange) {
            adText.setTextColor(FirebaseValue.colorAdsAttribNative.toColorInt())
        }
        adView.callToActionView = button

        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.callToActionView = button
        if (FirebaseValue.everyNativeCtaColorChangeEnable) {
            val gradient = everyNativeCtaColorList.random()
            val colors = intArrayOf(gradient.start, gradient.end)
            val gd = GradientDrawable()
            gd.orientation = GradientDrawable.Orientation.LEFT_RIGHT
            gd.colors = colors
            gd.shape = GradientDrawable.RECTANGLE
            if (nativeButtonRectangle) {
                gd.cornerRadius = 10f
            } else {
                gd.cornerRadius = 80f
            }
            button.background = gd
        } else {
            if (FirebaseValue.nativeButtonThemeColorChange) {
                val color = FirebaseValue.colorNativeCTR1.toColorInt()
                val color2 = FirebaseValue.colorNativeCTR2.toColorInt()
                val colors = intArrayOf(color, color2)
                val gd = GradientDrawable()
                gd.orientation = GradientDrawable.Orientation.LEFT_RIGHT
                gd.colors = colors
                gd.shape = GradientDrawable.RECTANGLE
                if (nativeButtonRectangle) {
                    gd.cornerRadius = 10f
                } else {
                    gd.cornerRadius = 80f
                }
                button.background = gd
            }
        }


        (adView.headlineView as TextView).text = nativeAd.headline
        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.GONE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView).text = nativeAd.body
        }
        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.GONE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button).text = nativeAd.callToAction
        }
        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView).setImageDrawable(
                nativeAd.icon?.drawable
            )
            adView.iconView?.visibility = View.VISIBLE
        }
        adView.setNativeAd(nativeAd)
    }

    fun addLargeNativeView(
        context: Context, adFrame: LinearLayout, ad: NativeAd, adViewType: Int
    ) {
        val adView = getNativeAdLayout(
            adViewType, context
        )
        try {
            adView.parent?.let { pt ->
                (pt as ViewGroup).removeAllViews()
            }
        } catch (_: Exception) {
        }
        if (nativeAdsBgColorChange) {
            val bg = adView.findViewById<LinearLayout>(R.id.bg_native)
            val colors = intArrayOf(
                FirebaseValue.colorNativeBg.toColorInt(), FirebaseValue.colorNativeBg.toColorInt()
            )
            val gd = GradientDrawable()
            gd.orientation = GradientDrawable.Orientation.LEFT_RIGHT
            gd.colors = colors
            gd.shape = GradientDrawable.RECTANGLE
            gd.cornerRadius = 20f
            gd.setStroke(2, FirebaseValue.colorNativeBgBorderStokes.toColorInt())
            bg.background = gd

        }
        populateUnifiedNativeAdView(
            ad, adView.findViewById(R.id.ad_view), adViewType
        )

        adFrame.visibility = View.VISIBLE
        try {
            adFrame.removeAllViews()
        } catch (_: Exception) {
        }
        adFrame.addView(adView)
    }

    fun getAdSize(activity: Activity): AdSize {
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
}