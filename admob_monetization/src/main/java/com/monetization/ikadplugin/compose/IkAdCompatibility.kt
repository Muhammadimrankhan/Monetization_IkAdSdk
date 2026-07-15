package com.monetization.ikadplugin.compose

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.monetization.ikadplugin.ads.FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Every native / banner ad in the app goes through here. Each placement gets its own
 * [NativeAdViewModel] / [BannerAdViewModel], keyed by [placementKey] so that two placements hosted
 * by the same screen (e.g. Home's collapsible banner and the exit-dialog native) don't share state.
 *
 * A slot that is suppressed or fails to fill renders nothing, so it no longer leaves an empty gap.
 */
@Composable
fun InitAdaptiveBannerComposeAd(
    activity: Activity,
    enable: Boolean,
    modifier: Modifier = Modifier,
    placementKey: String,
    loadNewAd: Boolean = false
) {
    val viewModel: BannerAdViewModel = koinViewModel(
        key = placementKey,
        parameters = { parametersOf(placementKey, false) },
    )
    BannerAdSlot(
        activity = activity,
        viewModel = viewModel,
        enable = enable,
        modifier = modifier,
        loadNewAd = loadNewAd,
    )
}


@Composable
fun InitCollapsableBannerComposeAd(
    activity: Activity,
    enable: Boolean,
    modifier: Modifier = Modifier,
    placementKey: String,
    loadNewAd: Boolean = false,
) {

    val viewModel: BannerAdViewModel = koinViewModel(
        key = placementKey,
        parameters = { parametersOf(placementKey, true) },
    )
    BannerAdSlot(
        activity = activity,
        viewModel = viewModel,
        enable = enable,
        modifier = modifier,
        loadNewAd = loadNewAd,
    )
}

@Composable
fun InitNativeComposeAd(
    activity: Activity,
    enable: Boolean,
    modifier: Modifier = Modifier,
    placementKey: String,
    adLayoutViewType: Int = 1,
    loadNewAd: Boolean = false,
    nativeCtaColorAdPosition: Int = -1
) {
    val viewModel: NativeAdViewModel = koinViewModel(
        key = placementKey,
        parameters = { parametersOf(placementKey) },
    )
    NativeAdSlot(
        activity = activity,
        viewModel = viewModel,
        enable = enable,
        modifier = modifier,
        adViewType = adLayoutViewType,
        nativeCtaColorAdPosition = nativeCtaColorAdPosition,
        loadNewAd = loadNewAd,
    )
}


@Composable
fun ShowMustInterstitialComposeAd(
    context: Activity,
    enable: Boolean,
    showAd: Boolean,
    placementKey: String,
    onAdLoaded: () -> Unit = {},
    onDone: (Boolean) -> Unit = {},
) {
    val interstitialAd = rememberIkComposeInterstitialAdAssignContext(
        activity = context,
        adReference = placementKey,
        enable = enable
    )
//    val interstitialAd = rememberIkComposeInterstitialAdPreLoad(
//        context = context,
//        adReference = placementKey,
//        enable = enable,
//        preloadOnStart = INTERSTITIAL_PRE_LOAD_ENABLE,
//    )
//    var adShown by remember(showAd) { mutableStateOf(false) }

    LaunchedEffect(showAd, interstitialAd) {
        if (showAd) {
            interstitialAd.showInterstitialAdEveryClick(
                enable = enable,
                onAdLoaded = onAdLoaded,
                onSplashAdViewGone = { },
                onAdClosed = { onDone(true) },
            )
        }
    }
}

//
//@Composable
//fun HomeTemplateInterstitialAd(
//    activity: Activity,
//    showAd: Boolean,
//    onDone: () -> Unit,
//) {
//    val interstitialAd = rememberIkComposeInterstitialAdPreLoad(
//        context = activity,
//        adReference = HOME_TEMPLATE_INTERSTITIAL_KEY,
//        enable = HOME_TEMPLATE_INTERSTITIAL_ENABLE,
//        preloadOnStart = INTERSTITIAL_PRE_LOAD_ENABLE,
//    )
//
//    LaunchedEffect(showAd, interstitialAd) {
//        if (showAd) {
//            interstitialAd.showInterstitialCounter(
//                enable = HOME_TEMPLATE_INTERSTITIAL_ENABLE,
//                onAdClosed = onDone,
//            )
//        }
//    }
//}

@Composable
fun ShowCounterInterstitialComposeAd(
    context: Activity,
    enable: Boolean,
    showAd: Boolean,
    placementKey: String,
    onAdLoaded: () -> Unit = {},
    onDone: (Boolean) -> Unit = {}
) {
    val interstitialAd = rememberIkComposeInterstitialAdAssignContext(
        activity = context,
        adReference = placementKey,
        enable = enable
    )
//    val interstitialAd = rememberIkComposeInterstitialAdPreLoad(
//        context = context,
//        adReference = placementKey,
//        enable = enable,
//        preloadOnStart = INTERSTITIAL_PRE_LOAD_ENABLE,
//    )

    LaunchedEffect(showAd, interstitialAd) {
        if (showAd) {
            interstitialAd.showInterstitialCounter(
                enable = enable,
                onAdLoaded = onAdLoaded,
                onSplashAdViewGone = { },
                onAdClosed = { onDone(true) },
            )
        }
    }
}


@Composable
fun ShowClickOrBackInterstitialComposeAd(
    isClickOrBackPressAdShow: Boolean,
    context: Activity,
    enable: Boolean,
    showAd: Boolean,
    placementKey: String,
    onAdLoaded: () -> Unit = {},
    onDone: (Boolean) -> Unit = {}
) {

    val interstitialAd = rememberIkComposeInterstitialAdAssignContext(
        activity = context,
        adReference = placementKey,
        enable = enable
    )

    LaunchedEffect(showAd, interstitialAd) {
        if (showAd) {
            interstitialAd.showInterstitialClickOrBackCounter(
                isClickOrBackPress = isClickOrBackPressAdShow,
                enable = enable,
                onAdLoaded = onAdLoaded,
                onSplashAdViewGone = { },
                onAdClosed = { onDone(true) },
            )
        }
    }
}
