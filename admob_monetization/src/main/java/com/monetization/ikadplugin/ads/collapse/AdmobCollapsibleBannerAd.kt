package com.monetization.ikadplugin.ads.collapse

import android.app.Activity
import com.google.android.gms.ads.AdView
import com.monetization.ikadplugin.ads.banner.BannerLoader
import com.monetization.ikadplugin.ads_duration_tracker.AdType

/**
 * Loads bottom-anchored collapsible banners. The loaded [AdView] is handed to Compose
 * (`BannerAdSlot` / `IkComposeCollapsibleBannerAd`), which hosts it. Pooling, expiry and retries
 * are shared with the plain banner (see [BannerLoader]).
 */
class AdmobCollapsibleBannerAd private constructor() : BannerLoader(AdType.BANNER, collapsible = true) {

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

    fun hasAd(): Boolean = hasAnyBanner()

    fun hasAdOrLoading(): Boolean = hasAnyBannerOrLoading()

    /** Preloads a collapsible banner so the next [loadCollapsibleBanner] can show it immediately. */
    fun loadAd(
        adReference: String,
        activity: Activity,
        enable: Boolean
    ) = preload(adReference, activity, enable, isRectangleBanner = false)

    fun loadCollapsibleBanner(
        adReference: String,
        activity: Activity,
        enable: Boolean,
        loadNewAd: Boolean = false,
        onLoaded: (AdView) -> Unit,
        onFailed: () -> Unit = {},
    ) = load(adReference, activity, enable, isRectangleBanner = false, loadNewAd, onLoaded, onFailed)
}
