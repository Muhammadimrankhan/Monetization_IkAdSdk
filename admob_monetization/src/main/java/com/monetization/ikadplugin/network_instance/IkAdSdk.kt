package com.monetization.ikadplugin.network_instance

import android.app.Activity
import android.content.Context
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
import com.monetization.ikadplugin.one_time_purchase.ProductsPurchaseHelper
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.subscription.SubscriptionConstant.isDebug
import com.monetization.ikadplugin.subscription.SubscriptionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object IkAdSdk {
    private var activityRef: WeakReference<Activity>? = null

    fun setCurrentActivity(activity: Activity?) {
        activityRef = if (activity != null) {
            WeakReference(activity)
        } else {
            null   // clear reference
        }
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

    val oneTimePurchaseController by lazy {
        ProductsPurchaseHelper.getInstance()
    }

    val subscriptionController by lazy {
        SubscriptionHelper.getInstance()
    }

    val openAppAdController by lazy {
        AdmobOpenAppAd.getInstance()
    }

    var isConsentCheck = false
    fun startSplash(
        context: Activity, consentVerifyID: String = "", consentCallback: (Any) -> Unit
    ) {

        val consentManager = GoogleMobileAdsConsentManager.getInstance(context)
        if (isConsentCheck) {
            consentCallback.invoke(consentManager.canRequestAds)
            return
        }
        if (!AdSharedPreference.getInstance(context).isAppPurchased && InternetController.getInstance(context).isInternetConnected
        ) {
            consentManager.gatherConsent(context) { error ->
                if (context.isFinishing || context.isDestroyed) return@gatherConsent
                initMobileSdk(context.applicationContext)
                if (consentManager.canRequestAds) {
                    isConsentCheck = true
                }
                consentCallback.invoke(consentManager.canRequestAds)
            }

            if (isDebug) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder().setTestDeviceIds(listOf(consentVerifyID)).build()
                )
            }
        } else {
            initMobileSdk(context.applicationContext)
            consentCallback.invoke(consentManager.canRequestAds)
        }

    }

    fun initMobileSdk(context: Context) {
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