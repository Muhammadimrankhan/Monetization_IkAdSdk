package com.monetization.ikadplugin.firebase_value_fetch

data class AdsConfig(
    val allAppInterstitialAdCountChange: Int = 3,
    val splashTime: Long = 13,
    val colorNativeCTR1: String = "#E74625",
    val colorNativeCTR2: String = "#E74625",
    val colorNativeBg: String = "#EDEDED",
    val colorNativeBgBorderStokes: String = "#505050",
    val colorAdsAttribNative: String = "#000000",
    val interstitialCounterStartSplash: Boolean = true,
    val nativeShimmerBtnColorChange: Boolean = true,
    val nativeButtonThemeColorChange: Boolean = false,
    val nativeAdsAttributionColorChange: Boolean = false,
    val nativeAdsBgColorChange: Boolean = false,
    val nativeButtonRectangle: Boolean = false,
    val allAdsOffEnable: Boolean = false,
    val splashInterstitialCallEnable: Boolean = false,
    val progressLoadingOpenAppEnable: Boolean = true,
    val interstitialPreloadEnable: Boolean = false,
    val interstitialPreloadProgressEnable: Boolean = false,
    val everyNativeCtaColorChangeEnable: Boolean = false,
    val everyNativeCtaColor: List<GradientColors> = emptyList()
)
