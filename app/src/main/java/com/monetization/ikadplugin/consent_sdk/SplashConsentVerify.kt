package com.monetization.ikadplugin.consent_sdk

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.IkAdPluginAppClass
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SplashConsentVerify(
    private val consentManager: GoogleMobileAdsConsentManager,
    private val prefHelper: AdSharedPreference,
    private val internetController: InternetController
) {
    fun startSplash(
        context: Activity, consentVerifyID: String = "", consentCallback: (Any) -> Unit
    ) {
        if (!prefHelper.isAppPurchased && internetController.isInternetConnected) {
            consentManager.gatherConsent(context) { error ->
                if (error == null && consentManager.canRequestAds) {
                    //Applovin consent
//                        AppLovinPrivacySettings.setHasUserConsent(
//                            googleMobileAdsConsentManager.canRequestAds
//                        )
                    //Liftoff consent
//                        VunglePrivacySettings.setGDPRStatus(true, "v1.0.0")
//                        VunglePrivacySettings.setCCPAStatus(true)
//                        //Mintegral consent
//                        var sdk = MBridgeSDKFactory.getMBridgeSDK()
//                        sdk.setConsentStatus(this, MBridgeConstans.IS_SWITCH_ON)
//                        sdk.setDoNotTrackStatus(this, false)
                }
                consentCallback.invoke(consentManager.canRequestAds)
            }

            if (BuildConfig.DEBUG) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder().setTestDeviceIds(listOf(consentVerifyID)).build()
                )
            }
        } else {
            consentCallback.invoke(consentManager.canRequestAds)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                (IkAdPluginAppClass.appContext as IkAdPluginAppClass).initAds()
            } catch (_: Exception) {
            }
        }
    }
}