package com.monetization.ikadplugin.compose

import android.app.Activity
import com.google.android.gms.ads.nativead.NativeAdView
import com.monetization.ikadplugin.ads.native_ads.AdmobNativeAd

/**
 * Owns the state and the cached ad view of one native placement, identified by [placementKey]
 * (e.g. `language_native`).
 *
 * Create one per placement — two placements sharing a ViewModel would share a single [state] and
 * fight over the same ad frame.
 */
class NativeAdViewModel(
    placementKey: String,
) : InlineAdViewModel(placementKey, NativeAdView::class.java) {

    fun load(
        activity: Activity,
        enable: Boolean,
        adViewType: Int = 1,
        nativeCtaColorAdPosition: Int = -1,
        loadNewAd: Boolean = false,
    ) {
        val requestKey = listOf(enable, adViewType, nativeCtaColorAdPosition, loadNewAd)
        requestLoad(
            requestKey = requestKey,
            activity = activity,
            allowed = canRequestInlineAd(activity, enable),
        ) { adFrame ->
            AdmobNativeAd.getInstance().populateNativeAd(
                adViewType = adViewType,
                adIdNativeReference = placementKey,
                context = activity,
                enable = enable,
                adFrame = adFrame,
                loadNewAd = loadNewAd,
                nativeCtaColorAdPosition = nativeCtaColorAdPosition,
            )
        }
    }
}
