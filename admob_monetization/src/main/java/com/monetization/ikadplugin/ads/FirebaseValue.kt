package com.monetization.ikadplugin.ads

import com.monetization.ikadplugin.firebase_value_fetch.GradientColors

object FirebaseValue {

    var REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE = true
    var REWARDED_INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE = true
    var INTERSTITIAL_PRE_LOAD_ENABLE = true
    var INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE = true
    var everyNativeCtaColorChangeEnable = false
    var everyNativeCtaColorList = mutableListOf<GradientColors>()
    var ALL_ADS_OFF_ENABLE = false

    var interstitialCounterStartSplash = true
    var nativeShimmerBtnColorChange = true
    var nativeButtonThemeColorChange = false
    var nativeAdsAttributionColorChange = false
    var nativeAdsBgColorChange = false
    var SPLASH_INTERSTITIAL_CALL_ENABLE = false
    var PROGRESS_LOADING_OPEN_AP_ENABLE = true
    var nativeButtonRectangle = false
    var colorNativeCTR1 = "#E74625"
    var colorNativeCTR2 = "#E74625"
    var colorNativeBg = "#EDEDED"
    var colorNativeBgDarkTheme = "#505050"
    var colorNativeBgBorderStokes = "#505050"
    var colorAdsAttribNative = "#000000"
    var IS_INTER_SHOWING = false
    var allAppInterstitialAdCount: Int = 0
    var allAppInterstitialAdCountChange: Int = 2
    var SPLASH_TIME = 13L
    var darkTheme = false

}
