package com.monetization.ikadplugin.firebase_value_fetch

data class AdsConfig(
    val allAppInterstitialAdCountChange: Int,
    val splashTime: Long,
    val colorNativeCTR1: String,
    val colorNativeCTR2: String,
    val colorNativeBg: String,
    val colorNativeBgBorderStokes: String,
    val colorAdsAttribNative: String,
    val interstitialCounterStartSplash: Boolean,
    val nativeShimmerBtnColorChange: Boolean,
    val nativeButtonThemeColorChange: Boolean,
    val nativeAdsAttributionColorChange: Boolean,
    val nativeAdsBgColorChange: Boolean,
    val nativeButtonRectangle: Boolean,
    val allAdsOffEnable: Boolean,
    val splashInterstitialCallEnable: Boolean,
    val progressLoadingOpenAppEnable: Boolean,
    val interstitialPreloadEnable: Boolean,
    val interstitialPreloadProgressEnable: Boolean
)
