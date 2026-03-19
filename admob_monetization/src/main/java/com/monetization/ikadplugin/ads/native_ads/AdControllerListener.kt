package com.monetization.ikadplugin.ads.native_ads

interface AdControllerListener {
    fun onAdLoaded()
    fun onAdImpression()
    fun onAdFailed()
    fun onPopulateAd(any: Any)
    fun resetRequesting()
}