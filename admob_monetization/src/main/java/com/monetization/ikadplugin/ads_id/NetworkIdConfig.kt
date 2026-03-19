package com.monetization.ikadplugin.ads_id

data class NetworkIdConfig(
    val interstitialAds: Map<String, String> = emptyMap(),
    val nativeAdId: Map<String, String> = emptyMap(),
    val bannerAdId: Map<String, String> = emptyMap(),
    val openAppAdId: Map<String, String> = emptyMap()
)
