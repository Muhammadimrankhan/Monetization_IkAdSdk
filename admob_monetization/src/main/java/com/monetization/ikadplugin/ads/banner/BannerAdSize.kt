package com.monetization.ikadplugin.ads.banner

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import com.google.android.gms.ads.AdSize

/**
 * Full-width large anchored adaptive banner size for [activity]'s current window — Google's
 * recommended anchored size, which replaces the deprecated current-orientation one.
 */
internal fun anchoredAdaptiveBannerSize(activity: Activity): AdSize {
    val adWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = activity.windowManager.currentWindowMetrics.bounds
        (bounds.width() / activity.resources.displayMetrics.density).toInt()
    } else {
        val outMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getMetrics(outMetrics)
        (outMetrics.widthPixels / outMetrics.density).toInt()
    }
    return AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, adWidth)
}
