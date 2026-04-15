package com.monetization.ikadplugin.ads.open_ap_ads

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.monetization.ikadplugin.ads.interstitial_ads.InterstitialControllerListener
import com.monetization.ikadplugin.ads.native_ads.AdControllerListener
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
    private var appContext: Context? = null

    private var openAdControllerListener: OpenAdControllerListener? = null

    fun initOpenAd(
        context: Context, adRef: String, openAdEnable: Boolean,
        openAdControllerListener: OpenAdControllerListener
    ) {
        this.openAdControllerListener = openAdControllerListener
        this.adRef = adRef
        this.appContext = context
        this.openAdEnable = openAdEnable
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }.onFailure {
            openAdControllerListener.onAdInitFailed("Failed to add lifecycle observer")
            Log.e("OpenAd", "Failed to add lifecycle observer", it)
        }

    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        try {
            if(appContext==null){
                openAdControllerListener?.onContextNotFound("appContext_is_null")
            }
            AdKeys.IS_APP_PAUSE = false
            if (AdKeys.canShowOpenAd && !AdSharedPreference.getInstance(
                    appContext!!
                ).isAppPurchased && openAdEnable
            ) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (getCurrentActivity() != null) {
                        showOpenAd()
                    } else {
                        openAdControllerListener?.onStartActivityFailed("Activity still null after delay")
                        Log.e("OpenAd", "Activity still null after delay")
                    }
                }, 300)
            }
        } catch (e: Exception) {
            openAdControllerListener?.onStartActivityException(e.printStackTrace().toString())
        }
    }

    private fun fetchAd() {
        if(appContext==null){
            openAdControllerListener?.onContextNotFound("appContext_is_null")
        }
        val ctx = appContext ?: return
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || isAdAvailable || !InternetController.getInstance(
                ctx
            ).isInternetConnected || AdSharedPreference.getInstance(ctx).isAppPurchased || AdKeys.IS_APP_PAUSE
        ) {
            openAdControllerListener?.onAdPurchased("purchased")
            return
        }
        if (!canRequestAd) {
            return
        }
        canRequestAd = false
        openAdControllerListener?.onAdCalling("Open_Ad_Called")

        val adId = FetchConfig.getOpenAppAdId(adRef)
        AppOpenAd.load(
            ctx,
            adId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    super.onAdLoaded(appOpenAd)
                    canRequestAd = true
                    openAdControllerListener?.onAdLoaded("Open_Ad_Loaded")
                    mAppOpenAd = appOpenAd
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    canRequestAd = true
                    mAppOpenAd = null
                    openAdControllerListener?.onAdFailed("Open_Ad_Failed")
                }
            })
    }

    private fun hideShowProgress(activity: Activity) {
        try {
            hideProgress()
            adLoadingDialog = AdLoadingDialog(activity)
            adLoadingDialog?.showAlertDialog()
        } catch (_: Exception) {
        }
    }

    private fun hideProgress() {
        try {
            adLoadingDialog?.dismissAlertDialog()
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
                    hideProgress()
                    callback.invoke()
                }

                override fun onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent()
                    AdKeys.isShowingOpenAd = true
                    setBlackColor()
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    super.onAdFailedToShowFullScreenContent(p0)
                    hideProgress()
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