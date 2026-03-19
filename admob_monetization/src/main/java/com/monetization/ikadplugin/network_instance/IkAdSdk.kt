package com.monetization.ikadplugin.network_instance

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.IkAdPluginAppClass
import com.monetization.ikadplugin.ads.banner.AdmobBannerAd
import com.monetization.ikadplugin.ads.collapse.AdmobCollapsibleBannerAd
import com.monetization.ikadplugin.ads.interstitial_ads.AdmobInterstitialAd
import com.monetization.ikadplugin.ads.native_ads.AdmobNativeAd
import com.monetization.ikadplugin.ads.open_ap_ads.AdmobOpenAppAd
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object IkAdSdk {

    private lateinit var consentManager: GoogleMobileAdsConsentManager
    private lateinit var prefHelper: AdSharedPreference
    private lateinit var internetController: InternetController

    fun initialize(context: Context) {
        consentManager = GoogleMobileAdsConsentManager(context)
        prefHelper = AdSharedPreference(context)
        internetController = InternetController(context)
    }

    val nativeController: AdmobNativeAd by lazy {
         AdmobNativeAd.getInstance(
            consentManager, prefHelper, internetController
        )
    }

    val interstitialController by lazy {
        AdmobInterstitialAd.getInstance(
            consentManager, prefHelper, internetController
        )
    }

    val bannerController by lazy {
        AdmobBannerAd.getInstance(
            consentManager, prefHelper, internetController
        )
    }

    val collapseBannerController by lazy {
        AdmobCollapsibleBannerAd.getInstance(
            consentManager, prefHelper, internetController
        )
    }

    val openAppAd by lazy {
        AdmobOpenAppAd.getInstance(
            internetController, prefHelper
        )
    }

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
//                        sdk.setConsentStatus(context, MBridgeConstans.IS_SWITCH_ON)
//                        sdk.setDoNotTrackStatus(context, false)
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