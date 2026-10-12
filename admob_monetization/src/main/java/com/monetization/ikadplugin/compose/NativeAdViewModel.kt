package com.monetization.ikadplugin.compose

import android.app.Activity
import com.google.android.gms.ads.nativead.NativeAd
import com.monetization.ikadplugin.ads.native_ads.AdmobNativeAd

/**
 * Owns the state and the loaded ad of one native placement, identified by [placementKey]
 * (e.g. `language_native`).
 *
 * Create one per placement — two placements sharing a ViewModel would share a single [state] and
 * the same ad.
 */
class NativeAdViewModel(
    placementKey: String,
) : InlineAdViewModel<NativeAd>(placementKey) {

    fun load(
        activity: Activity,
        enable: Boolean,
        loadNewAd: Boolean = false,
    ) {
        requestLoad(
            requestKey = listOf(enable, loadNewAd),
            activity = activity,
            allowed = canRequestInlineAd(activity, enable),
        ) { onLoaded, onFailed ->
            AdmobNativeAd.getInstance().loadNativeAd(
                adIdNativeReference = placementKey,
                context = activity,
                enable = enable,
                loadNewAd = loadNewAd,
                onLoaded = onLoaded,
                onFailed = onFailed,
            )
        }
    }

    override fun destroyAd(ad: NativeAd) {
        ad.destroy()
    }

    override fun cancelPendingRequest(onFailed: () -> Unit) {
        AdmobNativeAd.getInstance().cancelPending(onFailed)
    }
}
