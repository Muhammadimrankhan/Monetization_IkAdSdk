package com.monetization.ikadplugin.network_instance

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.monetization.ikadplugin.ads.banner.AdmobBannerAd
import com.monetization.ikadplugin.ads.collapse.AdmobCollapsibleBannerAd
import com.monetization.ikadplugin.ads.interstitial_ads.AdmobInterstitialAd
import com.monetization.ikadplugin.ads.native_ads.AdmobNativeAd
import com.monetization.ikadplugin.ads.open_ap_ads.AdmobOpenAppAd
import com.monetization.ikadplugin.ads.rewarded_interstitial.AdmobRewardedInterstitialAd
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
    private const val TAG = "IkAdSdk"

    /** Longest the splash waits for MobileAds (and mediation adapters) to initialize. */
    private const val INIT_WAIT_MS = 3000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null

    private var isInitStarted = false

    /** True once MobileAds reported that initialization (including mediation adapters) finished. */
    @Volatile
    var isMobileAdsInitialized = false
        private set
    private val initListeners = mutableListOf<() -> Unit>()

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

    val rewardedInterstitialController by lazy {
        AdmobRewardedInterstitialAd.getInstance()
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

    /**
     * Gathers consent, initializes MobileAds and then calls [consentCallback] with whether ads may
     * be requested. The callback waits for initialization (at most [INIT_WAIT_MS]) so mediation
     * adapters are ready for the first request.
     *
     * [consentVerifyID] is the hashed device id printed in logcat; in debug builds it marks the device
     * as an ads test device and as a UMP test device (EEA debug geography).
     */
    fun startSplash(
        context: Activity, consentVerifyID: String = "", consentCallback: (Any) -> Unit
    ) {
        if (isDebug && consentVerifyID.isNotBlank()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(listOf(consentVerifyID)).build()
            )
        }

        val consentManager = GoogleMobileAdsConsentManager.getInstance(context)
        if (isConsentCheck) {
            consentCallback.invoke(consentManager.canRequestAds)
            return
        }

        // Consent given in an earlier session: start initializing now, in parallel with the
        // consent refresh, so the splash ad request goes out sooner.
        initMobileSdk(context.applicationContext)

        if (!AdSharedPreference.getInstance(context).isAppPurchased && InternetController.getInstance(context).isInternetConnected
        ) {
            consentManager.gatherConsent(context, consentVerifyID) { _ ->
                if (context.isFinishing || context.isDestroyed) return@gatherConsent
                initMobileSdk(context.applicationContext)
                if (consentManager.canRequestAds) {
                    isConsentCheck = true
                }
                deliverAfterInit(consentManager.canRequestAds, consentCallback)
            }
        } else {
            deliverAfterInit(consentManager.canRequestAds, consentCallback)
        }
    }

    private fun deliverAfterInit(canRequestAds: Boolean, consentCallback: (Any) -> Unit) {
        if (!canRequestAds || isMobileAdsInitialized) {
            consentCallback.invoke(canRequestAds)
            return
        }
        var delivered = false
        val deliver: () -> Unit = {
            if (!delivered) {
                delivered = true
                consentCallback.invoke(canRequestAds)
            }
        }
        whenInitialized(deliver)
        mainHandler.postDelayed({ deliver() }, INIT_WAIT_MS)
    }

    /** Runs [block] on the main thread once MobileAds has initialized (immediately if it has). */
    fun whenInitialized(block: () -> Unit) {
        synchronized(initListeners) {
            if (!isMobileAdsInitialized) {
                initListeners += block
                return
            }
        }
        mainHandler.post(block)
    }

    /** Initializes MobileAds once, and only when consent allows requesting ads. */
    fun initMobileSdk(context: Context) {
        if (!GoogleMobileAdsConsentManager.getInstance(context).canRequestAds) return
        synchronized(this) {
            if (isInitStarted) return
            isInitStarted = true
        }
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MobileAds.initialize(appContext) { status ->
                    if (isDebug) {
                        status.adapterStatusMap.forEach { (name, adapter) ->
                            Log.d(TAG, "Adapter $name: ${adapter.initializationState} ${adapter.description}")
                        }
                    }
                    onInitialized()
                }
                try {
                    AudienceNetworkAds.initialize(context)
                } catch (_: Exception) {
                }
            } catch (t: Throwable) {
                // Ads can still load without the callback; never keep waiters hanging.
                Log.e(TAG, "MobileAds.initialize failed", t)
                synchronized(this@IkAdSdk) { isInitStarted = false }
                onInitialized()
            }
        }
    }

    private fun onInitialized() {
        val listeners = synchronized(initListeners) {
            isMobileAdsInitialized = true
            initListeners.toList().also { initListeners.clear() }
        }
        mainHandler.post { listeners.forEach { it() } }
    }
}
