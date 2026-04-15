package com.monetization.ikadplugin.ads.open_ap_ads

interface OpenAdControllerListener {
    fun onContextNotFound(string: String)
    fun onAdCalling(string: String)
    fun onAdLoaded(string: String)
    fun onAdFailed(string: String)
    fun onAdInitFailed(string: String)
    fun onStartActivityException(string: String)
    fun onStartActivityFailed(string: String)
    fun onAdPurchased(any: Any)
}