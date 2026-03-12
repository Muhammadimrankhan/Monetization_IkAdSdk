package com.monetization.ikadplugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.ads.AdKeys.IS_APP_PAUSE
import com.monetization.ikadplugin.ads.AdKeys.activityCheck
import com.monetization.ikadplugin.ads.AdKeys.canShowOpenAd
import com.monetization.ikadplugin.network_instance.IkAdSdk

class IkAdPluginAppClass : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        var appContext: Context? = null
    }

    var currentActivity: Activity? = null

    private var isAdsInitialized = false
    private var isOpenAdInitialized = false

    override fun onCreate() {
        super.onCreate()

        appContext = this
        IkAdSdk.initialize(this)
    }

//    fun initOpenAd(adRef: String, enable: Boolean) {
//        if (!isOpenAdInitialized) {
//            isOpenAdInitialized = true
//            appOpenManager.initOpenAd(adRef,enable,  this)
//        }
//    }


    fun initAds() {
        if (!isAdsInitialized) {
            isAdsInitialized = true

            try {
                registerActivityLifecycleCallbacks(this)
            } catch (_: Exception) {
            }

            try {
                if (!BuildConfig.DEBUG) {
                    try {
                        FirebaseApp.initializeApp(this)
                        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
                    } catch (_: Exception) {
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity
    }

    private fun checkActivity() {
        canShowOpenAd = (activityCheck != "") && currentActivity !is AdActivity
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        checkActivity()
    }

    override fun onActivityResumed(activity: Activity) {
        IS_APP_PAUSE = false
        currentActivity = activity
        checkActivity()
    }


    override fun onActivityPaused(activity: Activity) {
        IS_APP_PAUSE = true
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {
        currentActivity = null
        canShowOpenAd = true
        IS_APP_PAUSE = false
    }

}
