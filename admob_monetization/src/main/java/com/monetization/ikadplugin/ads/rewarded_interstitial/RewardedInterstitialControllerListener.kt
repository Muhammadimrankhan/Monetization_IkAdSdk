package com.monetization.ikadplugin.ads.rewarded_interstitial

interface RewardedInterstitialControllerListener {
    fun onAdClosed()
    fun onAdLoaded()
    fun onAdFailed()
    fun onIapShow()
    fun onUserEarnedReward(rewardType: String, rewardAmount: Int) {}
}