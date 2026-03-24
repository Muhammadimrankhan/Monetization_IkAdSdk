package com.monetization.ikadplugin.ads.open_ap_ads

import android.app.Activity
import android.content.Context
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
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.ads.AdKeys
import com.monetization.ikadplugin.ads.AdLoadingDialog
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.network_instance.IkAdSdk.getCurrentActivity
import com.monetization.ikadplugin.pref.AdSharedPreference
import java.util.Date

class AdmobOpenAppAd : LifecycleObserver, DefaultLifecycleObserver {

    companion object {

        @Volatile
        private var instance: AdmobOpenAppAd? = null

        fun getInstance(
        ): AdmobOpenAppAd {

            return instance ?: synchronized(this) {
                instance ?: AdmobOpenAppAd().also { instance = it }
            }
        }
    }

    private var mAppOpenAd: AppOpenAd? = null
    private var loadTime: Long = 0
    private var canRequestAd = true
    private var openAdEnable = true
    private var adRef = ""
    private var adLoadingDialog: AdLoadingDialog? = null
    var appContext: Context? = null


    fun initOpenAd(
        context: Activity, adRef: String, openAdEnable: Boolean
    ) {
        this.adRef = adRef
        this.appContext = context
        this.openAdEnable = openAdEnable
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (_: Exception) {
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        try {
            AdKeys.IS_APP_PAUSE = false
            if (AdKeys.canShowOpenAd && getCurrentActivity() != null && !AdSharedPreference.getInstance(
                    appContext!!
                ).isAppPurchased && openAdEnable
            ) {
                showOpenAd()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun fetchAd() {
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || isAdAvailable || !InternetController.getInstance(
                appContext!!
            ).isInternetConnected || AdSharedPreference.getInstance(appContext!!).isAppPurchased || AdKeys.IS_APP_PAUSE
        ) {
            return
        }
        if (!canRequestAd) {
            return
        }
        canRequestAd = false
        if (BuildConfig.DEBUG) {
            Toast.makeText(appContext!!, "Open Ad Called", Toast.LENGTH_SHORT).show()
        }
        val adId = FetchConfig.getOpenAppAdId(adRef)
        AppOpenAd.load(
            appContext!!,
            adId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    super.onAdLoaded(appOpenAd)
                    canRequestAd = true
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(appContext!!, "Open Ad Loaded", Toast.LENGTH_SHORT).show()
                    }
                    mAppOpenAd = appOpenAd
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    canRequestAd = true
                    mAppOpenAd = null
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(appContext!!, "Open Ad failed", Toast.LENGTH_SHORT).show()
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
            if (!AdKeys.IS_APP_PAUSE && !FirebaseValue.IS_INTER_SHOWING) {
                getCurrentActivity()?.let { mContext ->
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