package com.monetization.ikadplugin.firebase_value_fetch

import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_id.NetworkIdConfig

object FetchConfig {
    fun assignRemoteConfigValues(adsConfig: AdsConfig) {
        FirebaseValue.everyNativeCtaColorList.clear()
        FirebaseValue.allAppInterstitialAdCountChange = adsConfig.allAppInterstitialAdCountChange
        FirebaseValue.SPLASH_TIME = adsConfig.splashTime
        FirebaseValue.colorNativeCTR1 = adsConfig.colorNativeCTR1
        FirebaseValue.colorNativeCTR2 = adsConfig.colorNativeCTR2
        FirebaseValue.colorNativeBg = adsConfig.colorNativeBg
        FirebaseValue.colorNativeBgDarkTheme = adsConfig.colorNativeBgDarkTheme
        FirebaseValue.colorNativeBgBorderStokes = adsConfig.colorNativeBgBorderStokes
        FirebaseValue.colorAdsAttribNative = adsConfig.colorAdsAttribNative
        FirebaseValue.interstitialCounterStartSplash = adsConfig.interstitialCounterStartSplash
        FirebaseValue.nativeShimmerBtnColorChange = adsConfig.nativeShimmerBtnColorChange
        FirebaseValue.nativeButtonThemeColorChange = adsConfig.nativeButtonThemeColorChange
        FirebaseValue.nativeAdsAttributionColorChange = adsConfig.nativeAdsAttributionColorChange
        FirebaseValue.nativeAdsBgColorChange = adsConfig.nativeAdsBgColorChange
        FirebaseValue.nativeButtonRectangle = adsConfig.nativeButtonRectangle
        FirebaseValue.ALL_ADS_OFF_ENABLE = adsConfig.allAdsOffEnable
        FirebaseValue.PROGRESS_LOADING_OPEN_AP_ENABLE = adsConfig.progressLoadingOpenAppEnable
        FirebaseValue.SPLASH_INTERSTITIAL_CALL_ENABLE = adsConfig.splashInterstitialCallEnable
        FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE = adsConfig.interstitialPreloadEnable
        FirebaseValue.INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE =
            adsConfig.interstitialPreloadProgressEnable
        FirebaseValue.everyNativeCtaColorChangeEnable = adsConfig.everyNativeCtaColorChangeEnable
        FirebaseValue.everyNativeCtaColorList.addAll(adsConfig.everyNativeCtaColor)
        FirebaseValue.darkTheme = adsConfig.darkTheme

    }

    private var networkIdConfig: NetworkIdConfig = NetworkIdConfig(
        interstitialAds = emptyMap(),
        nativeAdId = emptyMap(),
        bannerAdId = emptyMap(),
        openAppAdId = emptyMap()
    )
//    private  var networkIdConfig: NetworkIdConfig? = null

    fun initializeNetWorkId(networkId: NetworkIdConfig) {
        networkIdConfig = networkId
    }

    fun getInterstitialId(referenceName: String): String {
        return networkIdConfig.interstitialAds[referenceName]
            ?: networkIdConfig.interstitialAds["default"] ?: ""
    }

    fun getNativeId(referenceName: String): String {
        return networkIdConfig.nativeAdId[referenceName] ?: networkIdConfig.nativeAdId["default"]
        ?: ""
    }

    fun getBannerId(referenceName: String): String {
        return networkIdConfig.bannerAdId[referenceName] ?: networkIdConfig.bannerAdId["default"]
        ?: ""
    }

    fun getOpenAppAdId(referenceName: String): String {
        return networkIdConfig.openAppAdId[referenceName] ?: networkIdConfig.openAppAdId["default"]
        ?: ""
    }

}
