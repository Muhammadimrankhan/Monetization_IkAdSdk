package com.monetization.ikadplugin.compose

import android.app.Activity
import android.view.View
import com.google.android.gms.ads.AdView
import com.monetization.ikadplugin.ads.banner.AdmobBannerAd
import com.monetization.ikadplugin.ads.collapse.AdmobCollapsibleBannerAd

/**
 * Owns the state and the cached ad view of one banner placement, identified by [placementKey]
 * (e.g. `main_collapse_banner`).
 *
 * [collapsible] picks the underlying ad class: the collapsible banner (anchored, with the collapse
 * arrow) or the plain adaptive / rectangle banner.
 */
class BannerAdViewModel(
    placementKey: String,
    private val collapsible: Boolean,
) : InlineAdViewModel(placementKey, AdView::class.java) {

    fun load(
        activity: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean = false,
        loadNewAd: Boolean = false,
    ) {
        val requestKey = listOf(enable, isRectangleBanner, loadNewAd)
        requestLoad(
            requestKey = requestKey,
            activity = activity,
            allowed = canRequestInlineAd(activity, enable),
        ) { adFrame ->
            if (collapsible) {
                AdmobCollapsibleBannerAd.getInstance().populateAd(
                    adReference = placementKey,
                    activity = activity,
                    enable = enable,
                    adFrame = adFrame,
                    loadNewAd = loadNewAd,
                )
            } else {
                AdmobBannerAd.getInstance().populateBannerAd(
                    adReference = placementKey,
                    context = activity,
                    enable = enable,
                    isRectangleBanner = isRectangleBanner,
                    adLayout = adFrame,
                    loadNewAd = loadNewAd,
                )
            }
        }
    }

    override fun destroyAdView(adView: View) {
        (adView as? AdView)?.destroy()
    }

    override fun onCleared() {
        // Also drops whatever the collapsible class may still be holding as a preload.
        if (collapsible) AdmobCollapsibleBannerAd.getInstance().destroy()
        super.onCleared()
    }
}
