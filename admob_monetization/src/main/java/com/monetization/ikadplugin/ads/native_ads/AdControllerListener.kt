package com.monetization.ikadplugin.ads.native_ads

interface AdControllerListener {
    fun onAlreadyAdLoadedShow(string: String)
    fun onAdCalling(string: String)
    fun onAdLoaded(string: String)
    fun onAdFailed(string: String)
    fun onAdPurchased(any: Any)
}