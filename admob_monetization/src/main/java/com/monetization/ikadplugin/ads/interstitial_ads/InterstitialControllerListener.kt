package com.monetization.ikadplugin.ads.interstitial_ads

interface InterstitialControllerListener {
    fun onAdClosed()
    fun onAdLoaded()
    fun onSplashAdViewGone()
    fun onIapShow()
    fun onUserEarnedReward(rewardType: String, rewardAmount: Int) {}
}