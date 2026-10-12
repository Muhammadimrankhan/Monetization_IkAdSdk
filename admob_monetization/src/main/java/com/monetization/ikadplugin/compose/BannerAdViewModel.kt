package com.monetization.ikadplugin.compose

import android.app.Activity
import com.google.android.gms.ads.AdView
import com.monetization.ikadplugin.ads.banner.AdmobBannerAd
import com.monetization.ikadplugin.ads.collapse.AdmobCollapsibleBannerAd

/**
 * Owns the state and the loaded [AdView] of one banner placement, identified by [placementKey]
 * (e.g. `main_collapse_banner`).
 *
 * [collapsible] picks the underlying ad class: the collapsible banner (anchored, with the collapse
 * arrow) or the plain adaptive / rectangle banner.
 */
class BannerAdViewModel(
    placementKey: String,
    private val collapsible: Boolean,
) : InlineAdViewModel<AdView>(placementKey) {

    override val bindsToActivity: Boolean = true

    fun load(
        activity: Activity,
        enable: Boolean,
        isRectangleBanner: Boolean = false,
        loadNewAd: Boolean = false,
    ) {
        requestLoad(
            requestKey = listOf(enable, isRectangleBanner, loadNewAd),
            activity = activity,
            allowed = canRequestInlineAd(activity, enable),
        ) { onLoaded, onFailed ->
            if (collapsible) {
                AdmobCollapsibleBannerAd.getInstance().loadCollapsibleBanner(
                    adReference = placementKey,
                    activity = activity,
                    enable = enable,
                    loadNewAd = loadNewAd,
                    onLoaded = onLoaded,
                    onFailed = onFailed,
                )
            } else {
                AdmobBannerAd.getInstance().loadBanner(
                    adReference = placementKey,
                    activity = activity,
                    enable = enable,
                    isRectangleBanner = isRectangleBanner,
                    loadNewAd = loadNewAd,
                    onLoaded = onLoaded,
                    onFailed = onFailed,
                )
            }
        }
    }

    override fun destroyAd(ad: AdView) {
        ad.destroy()
    }

    override fun cancelPendingRequest(onFailed: () -> Unit) {
        if (collapsible) {
            AdmobCollapsibleBannerAd.getInstance().cancelPending(onFailed)
        } else {
            AdmobBannerAd.getInstance().cancelPending(onFailed)
        }
    }

    override fun onCleared() {
        // Also drops whatever the collapsible class may still be holding as a preload.
        if (collapsible) AdmobCollapsibleBannerAd.getInstance().destroy()
        super.onCleared()
    }
}
