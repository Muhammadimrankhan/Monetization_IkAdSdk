package com.monetization.ikadplugin.compose

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders the native ad owned by [viewModel] with the [adViewType] template (see
 * [NativeAdTemplate]).
 *
 * - As soon as the slot is composed, the shimmer shows (unless ads can't be requested at all).
 * - When the ad loads, the shimmer is replaced by the ad.
 * - When the ad fails, is suppressed or times out, the shimmer disappears and the slot takes no
 *   height.
 *
 * Leaving the screen and returning shows the *same* loaded ad again — it lives in the ViewModel,
 * so there is no shimmer and no new ad request. If the last attempt failed, coming back requests a
 * fresh ad.
 */
@Composable
fun NativeAdSlot(
    activity: Activity,
    viewModel: NativeAdViewModel,
    enable: Boolean,
    modifier: Modifier = Modifier,
    adViewType: Int = 1,
    nativeCtaColorAdPosition: Int = -1,
    loadNewAd: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val nativeAd by viewModel.ad.collectAsState()

    LaunchedEffect(activity, enable, loadNewAd) {
        viewModel.load(
            activity = activity,
            enable = enable,
            loadNewAd = loadNewAd,
        )
    }

    val ad = nativeAd
    when {
        state == AdSlotState.Loaded && ad != null -> NativeAdTemplate(
            nativeAd = ad,
            adViewType = adViewType,
            modifier = modifier,
            nativeCtaColorAdPosition = nativeCtaColorAdPosition,
        )

        showShimmer(state, activity, enable) -> NativeAdShimmer(adViewType, modifier)
    }
}

/**
 * Renders the banner ad owned by [viewModel], with a shimmer while it loads. Same rules as
 * [NativeAdSlot]: shimmer first, then the banner — or nothing if it fails. A loaded banner survives
 * navigation untouched, a failed one is retried on the next visit.
 */
@Composable
fun BannerAdSlot(
    activity: Activity,
    viewModel: BannerAdViewModel,
    enable: Boolean,
    modifier: Modifier = Modifier,
    isRectangleBanner: Boolean = false,
    loadNewAd: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val adView by viewModel.ad.collectAsState()

    LaunchedEffect(activity, enable, isRectangleBanner, loadNewAd) {
        viewModel.load(
            activity = activity,
            enable = enable,
            isRectangleBanner = isRectangleBanner,
            loadNewAd = loadNewAd,
        )
    }

    val banner = adView
    when {
        state == AdSlotState.Loaded && banner != null -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            key(banner) {
                AndroidView(
                    factory = {
                        // The AdView outlives any single composition; when the screen is re-entered
                        // it may still be parented by the previous (discarded) AndroidView node.
                        banner.also { (it.parent as? ViewGroup)?.removeView(it) }
                    },
                )
            }
        }

        showShimmer(state, activity, enable) -> NativeAdShimmer(
            // Medium rectangle is 250dp tall, so use the large template's shimmer for it.
            adViewType = if (isRectangleBanner) 4 else 2,
            modifier = modifier,
        )
    }
}

/**
 * Loading → shimmer. Idle (first frame, before the load effect has run) → shimmer too, but only
 * when an ad can actually be requested, so a suppressed slot never flashes a shimmer.
 */
@Composable
private fun showShimmer(state: AdSlotState, activity: Activity, enable: Boolean): Boolean {
    val canRequest = remember(activity, enable) { canRequestInlineAd(activity, enable) }
    return when (state) {
        AdSlotState.Loading -> true
        AdSlotState.Idle -> canRequest
        else -> false
    }
}
