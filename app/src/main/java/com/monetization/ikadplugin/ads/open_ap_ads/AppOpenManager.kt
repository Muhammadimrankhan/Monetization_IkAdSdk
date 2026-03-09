package com.monetization.ikadplugin.ads.open_ap_ads

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.IkAdPluginAppClass
import com.monetization.ikadplugin.ads.AdKeys
import com.monetization.ikadplugin.ads.AdKeys.IS_APP_PAUSE
import com.monetization.ikadplugin.ads.AdKeys.canShowOpenAd
import com.monetization.ikadplugin.ads.AdLoadingDialog
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads.FirebaseValue.ALL_ADS_OFF_ENABLE
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import java.util.Date

class AppOpenManager(
    private var internetController: InternetController, private val prefHelper: AdSharedPreference
) : LifecycleObserver, DefaultLifecycleObserver {
    private var mAppOpenAd: AppOpenAd? = null
    private var loadTime: Long = 0
    private var canRequestAd = true
    private var openAdEnable = true
    private var adRef = ""
    private var adLoadingDialog: AdLoadingDialog? = null

    private lateinit var appClass: IkAdPluginAppClass

    fun initOpenAd(
        adRef: String, openAdEnable: Boolean, appClass: IkAdPluginAppClass
    ) {
        this.adRef = adRef
        this.appClass = appClass
        this.openAdEnable = openAdEnable
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (_: Exception) {
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        try {
            IS_APP_PAUSE = false
            if (canShowOpenAd && appClass.currentActivity != null && !prefHelper.isAppPurchased && openAdEnable) {
                showOpenAd()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun fetchAd() {
        if (ALL_ADS_OFF_ENABLE || isAdAvailable || !internetController.isInternetConnected || prefHelper.isAppPurchased || IS_APP_PAUSE) {
            return
        }
        if (!canRequestAd) {
            return
        }
        canRequestAd = false
        if (BuildConfig.DEBUG) {
            Toast.makeText(appClass, "Open Ad Called", Toast.LENGTH_SHORT).show()
        }
        val adId = FetchConfig.getOpenAppAdId(adRef)
        AppOpenAd.load(
            appClass, adId, AdRequest.Builder().build(), object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    super.onAdLoaded(appOpenAd)
                    canRequestAd = true
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(appClass, "Open Ad Loaded", Toast.LENGTH_SHORT).show()
                    }
                    mAppOpenAd = appOpenAd
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    canRequestAd = true
                    mAppOpenAd = null
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(appClass, "Open Ad failed", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun hideShowProgress(activity: Activity) {
        try {
            hideProgress(activity)
            adLoadingDialog = AdLoadingDialog(activity)
            adLoadingDialog?.showAlertDialog(activity)
        } catch (_: Exception) {
        }
    }

    private fun hideProgress(activity: Activity) {
        try {
            adLoadingDialog?.dismissAlertDialog(activity)
        } catch (ignored: Exception) {
        }
    }

    private fun setBlackColor() {
        try {
            adLoadingDialog?.setBlackColor()
        } catch (ignored: Exception) {
        }
    }

    private fun showOpenAd() {
        if (!AdKeys.isShowingOpenAd && isAdAvailable) {
            if (!IS_APP_PAUSE && !FirebaseValue.IS_INTER_SHOWING) {
                appClass.currentActivity?.let { mContext ->
                    checkOpenAdProgressAndShowAd(mContext) {
                        fetchAd()
                    }
                }
            }
        } else {
            fetchAd()
        }
    }

    private fun checkOpenAdProgressAndShowAd(mContext: Activity, callback: () -> Unit) {
        if (FirebaseValue.PROGRESS_LOADING_OPEN_AP_ENABLE) {
            try {
                hideShowProgress(mContext)
                Handler(Looper.getMainLooper()).postDelayed({
                    nowShowAd(mContext, callback)
                }, 1000)
            } catch (e: Exception) {
                nowShowAd(mContext, callback)
            }
        } else {
            nowShowAd(mContext, callback)
        }
    }

    private fun nowShowAd(mContext: Activity, callback: () -> Unit) {
        mAppOpenAd?.let {
            if (Build.VERSION.SDK_INT >= 35) {
                it.setImmersiveMode(true)
            }
            it.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    mAppOpenAd = null
                    AdKeys.isShowingOpenAd = false
                    hideProgress(mContext)
                    callback.invoke()
                }

                override fun onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent()
                    AdKeys.isShowingOpenAd = true
                    setBlackColor()
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    super.onAdFailedToShowFullScreenContent(p0)
                    hideProgress(mContext)
                    mAppOpenAd = null
                    callback.invoke()
                    AdKeys.isShowingOpenAd = false
                }
            }
            it.show(mContext)
        }
    }

    private fun wasLoadTimeLessThanNHoursAgo(): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * 4L
    }

    val isAdAvailable: Boolean
        get() = mAppOpenAd != null && wasLoadTimeLessThanNHoursAgo()

}