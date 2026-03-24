package com.monetization.ikadplugin.network_instance

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.monetization.ikadplugin.BuildConfig
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
import java.lang.ref.WeakReference

object IkAdSdk {
    private var activityRef: WeakReference<Activity>? = null

    fun setCurrentActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun getCurrentActivity(): Activity? {
        return activityRef?.get()
    }

    val nativeController: AdmobNativeAd by lazy {
        AdmobNativeAd.getInstance()
    }

    val interstitialController by lazy {
        AdmobInterstitialAd.getInstance()
    }

    val bannerController by lazy {
        AdmobBannerAd.getInstance()
    }

    val collapseBannerController by lazy {
        AdmobCollapsibleBannerAd.getInstance()
    }

    val openAppAd by lazy {
        AdmobOpenAppAd.getInstance()
    }

    fun startSplash(
        context: Activity, consentVerifyID: String = "", consentCallback: (Any) -> Unit
    ) {
        var consentManager = GoogleMobileAdsConsentManager.getInstance(context)
        if (!AdSharedPreference.getInstance(context).isAppPurchased && InternetController.getInstance(
                context
            ).isInternetConnected
        ) {
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
                initMobileSdk(context)
                consentCallback.invoke(consentManager.canRequestAds)
            }

            if (BuildConfig.DEBUG) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder().setTestDeviceIds(listOf(consentVerifyID)).build()
                )
            }
        } else {
            initMobileSdk(context)
            consentCallback.invoke(consentManager.canRequestAds)
        }
    }

    fun initMobileSdk(context: Activity) {
        if (GoogleMobileAdsConsentManager.getInstance(context).canRequestAds) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    MobileAds.initialize(context)
                } catch (_: Exception) {
                } catch (_: ClassNotFoundException) {
                } catch (_: NoClassDefFoundError) {
                } catch (_: NoSuchMethodError) {
                } catch (_: VerifyError) {
                } catch (_: OutOfMemoryError) {
                }
            }

        }
    }
}