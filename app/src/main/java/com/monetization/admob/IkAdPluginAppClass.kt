package com.monetization.admob

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.monetization.ikadplugin.ads.AdKeys.IS_APP_PAUSE
import com.monetization.ikadplugin.ads.AdKeys.canShowOpenAd
import com.monetization.ikadplugin.ads.open_ap_ads.OpenAdControllerListener
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.network_instance.IkAdSdk

class IkAdPluginAppClass : Application(), Application.ActivityLifecycleCallbacks {

    private var isAdsInitialized = false
    private var isOpenAdInitialized = false

    fun initOpenAd(
        adRef: String, enable: Boolean,
        openAdControllerListener: OpenAdControllerListener
    ) {
        if (!isOpenAdInitialized) {
            isOpenAdInitialized = true
            IkAdSdk.openAppAdController.initOpenAd(this, adRef, enable)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AdClickDurationTracker.init(this)
    }

    fun initFirst() {
        if (!isAdsInitialized) {
            isAdsInitialized = true
            registerActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        IkAdSdk.setCurrentActivity(activity)
    }

    private fun checkActivity(activity: Activity) {
        canShowOpenAd = activity !is MainActivity
    }

    override fun onActivityStarted(activity: Activity) {
        IkAdSdk.setCurrentActivity(activity)
        checkActivity(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        IS_APP_PAUSE = false
        IkAdSdk.setCurrentActivity(activity)
        checkActivity(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        IS_APP_PAUSE = true
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (IkAdSdk.getCurrentActivity() === activity) {
            IkAdSdk.setCurrentActivity(null)
        }
        canShowOpenAd = true
        IS_APP_PAUSE = false
    }
}