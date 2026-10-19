package com.monetization.ikadplugin.ads

object AdKeys {
    var canShowOpenAd = true
    var isPermissionCheck = false
    var IS_APP_PAUSE = false
    var isShowingOpenAd = false
    var iapScreenShow = false

    /**
     * Set when the user clicks a banner or native ad. Coming back from the ad's landing page must not
     * be met with an app open ad, so the next foreground skips it once.
     */
    var skipNextOpenAd = false

}
