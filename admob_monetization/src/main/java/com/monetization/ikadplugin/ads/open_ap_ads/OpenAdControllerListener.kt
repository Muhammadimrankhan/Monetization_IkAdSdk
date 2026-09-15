package com.monetization.ikadplugin.ads.open_ap_ads

interface OpenAdControllerListener {

    /**
     * Called from [AdmobOpenAppAd] when the app open ad has just been shown
     * (AdMob onAdShowedFullScreenContent), and only when the remote config flag
     * openAdBeforeActivityShowEnable (FirebaseValue.OPEN_AD_BEFORE_ACTIVITY_SHOW_ENABLE)
     * is true.
     *
     * Use it to start any activity behind the open app ad, so that the user lands on
     * that screen once the ad is dismissed.
     *
     * @param isActivityShow true when the host app should show its activity behind the ad.
     */
    fun beforeOpenAdActivityShow(isActivityShow: Boolean)

}