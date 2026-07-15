package com.monetization.ikadplugin.compose

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders the native ad owned by [viewModel].
 *
 * Leaving the screen and returning shows the *same* loaded ad again — the view lives in the
 * ViewModel, so there is no shimmer and no new ad request. If the last attempt failed, coming back
 * requests a fresh ad. While the slot is hidden (suppressed or unfilled) nothing is emitted at all,
 * so it takes up no height.
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

    LaunchedEffect(activity, enable, adViewType, nativeCtaColorAdPosition, loadNewAd) {
        viewModel.load(
            activity = activity,
            enable = enable,
            adViewType = adViewType,
            nativeCtaColorAdPosition = nativeCtaColorAdPosition,
            loadNewAd = loadNewAd,
        )
    }

    if (state.isVisible) {
        AndroidView(
            modifier = modifier,
            factory = { viewModel.frameToAttach(activity) },
        )
    }
}

/**
 * Renders the banner ad owned by [viewModel]. Same reuse rules as [NativeAdSlot]: a loaded banner
 * survives navigation untouched, a failed one is retried on the next visit.
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

    LaunchedEffect(activity, enable, isRectangleBanner, loadNewAd) {
        viewModel.load(
            activity = activity,
            enable = enable,
            isRectangleBanner = isRectangleBanner,
            loadNewAd = loadNewAd,
        )
    }

    if (state.isVisible) {
        AndroidView(
            modifier = modifier,
            factory = { viewModel.frameToAttach(activity) },
        )
    }
}
