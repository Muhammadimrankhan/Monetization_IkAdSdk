package com.monetization.ikadplugin.ads.interstitial_ads

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.monetization.ikadplugin.BuildConfig
import com.monetization.ikadplugin.ads.AdKeys
import com.monetization.ikadplugin.ads.AdKeys.iapScreenShow
import com.monetization.ikadplugin.ads.AdLoadingDialog
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference

class AdmobInterstitialAd {

    companion object {

        @Volatile
        private var instance: AdmobInterstitialAd? = null

        fun getInstance(
        ): AdmobInterstitialAd {

            return instance ?: synchronized(this) {
                instance ?: AdmobInterstitialAd(
                ).also { instance = it }
            }
        }
    }

    private val handlerAd: Handler = Handler(Looper.getMainLooper())
    private var isExitAppCall = false
    private var canRequestAd = true
    private var admobInterAd: InterstitialAd? = null
    private var mInterstitialControllerListener: InterstitialControllerListener? = null
    private var isHandlerRunning = false
    private var isHandlerRunningInstant = false
    private val runnableInstant = Runnable {
        if (mInterstitialControllerListener != null && isHandlerRunningInstant) {
            canRequestAd = true
            adLoadingDialog?.dismissAlertDialog()
            isHandlerRunningInstant = false
            try {
                mInterstitialControllerListener?.onAdClosed()
            } catch (_: Exception) {
            }
        }
    }

    private fun startHandlerInstant() {
        isHandlerRunningInstant = true
        handlerAd.postDelayed(runnableInstant, 6000)
    }

    private val runnable = Runnable {
        if (mInterstitialControllerListener != null && isHandlerRunning) {
            adLoadingDialog?.dismissAlertDialog()
            isHandlerRunning = false
            canRequestAd = true
            mInterstitialControllerListener?.onAdLoaded()
            mInterstitialControllerListener?.onAdClosed()
        }
    }
    private var adLoadingDialog: AdLoadingDialog? = null
    private var isPauseDone = false

    fun resetSplash() {
        isExitAppCall = false
        mInterstitialControllerListener = null
        isPauseDone = false
        isHandlerRunning = false
    }

    fun hasAd(): Boolean {
        return (admobInterAd != null)
    }

    fun pauseAd() {
        isPauseDone = true
        removeCallBacks()
    }

    private fun startHandler() {
        if (!isHandlerRunning) {
            isHandlerRunning = true
            handlerAd.postDelayed(runnable, 13000)
        }
    }

    fun removeCallBacks() {
        try {
            isHandlerRunning = false
            handlerAd.removeCallbacks(runnable)
        } catch (_: Exception) {
        }
    }

    private fun closeHandler(delay: Long) {
        handlerAd.postDelayed(
            {
                mInterstitialControllerListener?.onAdClosed()
            }, delay
        )
    }


    fun onDestroy() {
        removeCallBacks()
    }


    fun resumeAd(
        activity: Activity, enable: Boolean
    ) {
        if (isPauseDone && !FirebaseValue.IS_INTER_SHOWING) {
            isPauseDone = false
            if (!isHandlerRunning) {
                handlerAd.postDelayed({
                    try {
                        showSplashInterstitial(activity, enable)
                    } catch (ignored: Exception) {
                    }
                }, 1000)
            }
//            else {
//                removeCallBacks()
//                 closeHandler(1000)
//            }
        }
//        else {
//                removeCallBacks()
//                closeHandler(1000)
//        }
    }

    fun initAdMobSplash(
        adId: String,
        context: Activity,
        enable: Boolean,
        interstitialControllerListener: InterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || !GoogleMobileAdsConsentManager.getInstance(context).canRequestAds || !enable || !InternetController.getInstance(
                context
            ).isInternetConnected || AdSharedPreference.getInstance(context).isAppPurchased
        ) {
            closeHandler(1000)
            return
        }
        isExitAppCall = false
        isPauseDone = false
        isHandlerRunning = false
        loadAdForSplash(adId, context)

    }

    private fun loadAdForSplash(
        adIdReferenceName: String, context: Activity
    ) {
        try {
            if (InternetController.getInstance(context).isInternetConnected) {
                if (admobInterAd != null) {
                    mInterstitialControllerListener?.onAdLoaded()
                    showSplashInterstitial(context, true)
                } else {
                    if (!canRequestAd) {
                        return
                    }
                    canRequestAd = false
                    if (!isHandlerRunning) {
                        startHandler()
                    }
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(context, "splash admob inter Called", Toast.LENGTH_SHORT)
                            .show()
                    }
                    val adId = FetchConfig.getInterstitialId(adIdReferenceName)
                    InterstitialAd.load(
                        context,
                        adId,
                        AdRequest.Builder().build(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(p0: InterstitialAd) {
                                super.onAdLoaded(p0)
                                canRequestAd = true
                                admobInterAd = p0
                                if (BuildConfig.DEBUG) {
                                    Toast.makeText(
                                        context, "splash admob inter Loaded", Toast.LENGTH_SHORT
                                    ).show()
                                }
                                mInterstitialControllerListener?.onAdLoaded()
                                if (FirebaseValue.SPLASH_INTERSTITIAL_CALL_ENABLE) {
                                    if (isHandlerRunning) {
                                        showSplashInterstitial(
                                            context, true
                                        )
                                    }
                                } else {
                                    showSplashInterstitial(
                                        context, true
                                    )
                                }
                            }

                            override fun onAdFailedToLoad(p0: LoadAdError) {
                                super.onAdFailedToLoad(p0)
                                canRequestAd = true
                                if (BuildConfig.DEBUG) {
                                    Toast.makeText(
                                        context, "splash admob inter failed", Toast.LENGTH_SHORT
                                    ).show()
                                }
                                mInterstitialControllerListener?.onAdLoaded()

                                if (isHandlerRunning) {
                                    removeCallBacks()
                                    mInterstitialControllerListener?.onAdClosed()

                                }

                            }
                        })
                }
            } else {
                mInterstitialControllerListener?.onAdLoaded()
                closeHandler(1000)
            }
        } catch (e: Exception) {
            mInterstitialControllerListener?.onAdLoaded()
            closeHandler(1000)
        }
    }


    fun showSplashInterstitial(
        activity: Activity, enable: Boolean
    ) {
        if (isHandlerRunning) {
            removeCallBacks()
        }
        if (isPauseDone || AdSharedPreference.getInstance(activity).isAppPurchased || !enable || (admobInterAd == null && !InternetController.getInstance(
                activity
            ).isInternetConnected) || AdKeys.IS_APP_PAUSE || AdKeys.isShowingOpenAd
        ) {
            mInterstitialControllerListener?.onAdClosed()
        } else if (admobInterAd != null) {
            loadingProgress(activity)
            handlerAd.postDelayed({
                try {
                    setAdmobFullScreen(activity, true, "")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 1000)
        } else {
            mInterstitialControllerListener?.onAdClosed()
        }
    }


    private fun showAdmobAd(activity: Activity) {
        try {
            if (admobInterAd != null && !InternetController.getInstance(activity).isVPNConnected && !AdKeys.IS_APP_PAUSE && !AdKeys.isShowingOpenAd) {
                admobInterAd?.show(activity)
            } else {
                mInterstitialControllerListener?.onAdClosed()
            }
        } catch (e: Exception) {
            mInterstitialControllerListener?.onAdClosed()
        }
    }

    private fun loadNewAd(context: Activity) {
//            loadAd(context)
    }

    fun initAdMob(context: Activity, enable: Boolean) {
        isExitAppCall = false
        if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE || !enable || !InternetController.getInstance(
                context
            ).isInternetConnected
        ) {
            return
        }
        loadNewAd(context)
    }

    fun isExitAppCall() {
        isExitAppCall = true
    }

    private fun loadAd(adIdReferenceName: String, mContext: Activity) {
        try {
            if (!AdSharedPreference.getInstance(mContext).isAppPurchased && InternetController.getInstance(
                    mContext
                ).isInternetConnected
            ) {
                if (admobInterAd != null) {
                    return
                }
                if (!canRequestAd) {
                    return
                }
                canRequestAd = false
                if (BuildConfig.DEBUG) {
                    Toast.makeText(mContext, "Interstitial Called", Toast.LENGTH_SHORT).show()
                }
                val adId = FetchConfig.getInterstitialId(adIdReferenceName)

                if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    startHandlerInstant()
                    loadingProgress(mContext)
                }
                InterstitialAd.load(
                    mContext,
                    adId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(p0: InterstitialAd) {
                            super.onAdLoaded(p0)
                            canRequestAd = true
                            admobInterAd = p0
                            if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                                setAdmobFullScreen(activity = mContext, false, "")
                            }
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    mContext, "Interstitial Admob Loaded", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            canRequestAd = true
                            admobInterAd = null
                            if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                                mInterstitialControllerListener?.onAdClosed()
                            }
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(mContext, "Admob inter failed", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    })
            } else {
                canRequestAd = true
                adLoadingDialog?.dismissAlertDialog()
                if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    mInterstitialControllerListener?.onAdClosed()
                }
            }
        } catch (e: Exception) {
            canRequestAd = true
            onAdFailed()
        }
    }


    fun preLoadAd(
        adIdReferenceName: String, mContext: AppCompatActivity
    ) {
        try {
            if (!AdSharedPreference.getInstance(mContext).isAppPurchased && InternetController.getInstance(
                    mContext
                ).isInternetConnected
            ) {
                if (admobInterAd != null) {
                    return
                }
                if (!canRequestAd) {
                    return
                }
                canRequestAd = false
                if (BuildConfig.DEBUG) {
                    Toast.makeText(mContext, "Interstitial Called", Toast.LENGTH_SHORT).show()
                }
                val adId = FetchConfig.getInterstitialId(adIdReferenceName)

                InterstitialAd.load(
                    mContext,
                    adId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(p0: InterstitialAd) {
                            super.onAdLoaded(p0)
                            canRequestAd = true
                            admobInterAd = p0
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(
                                    mContext, "Interstitial Admob Loaded", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            canRequestAd = true
                            admobInterAd = null
                            if (BuildConfig.DEBUG) {
                                Toast.makeText(mContext, "Admob inter failed", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    })
            } else {
                canRequestAd = true
            }
        } catch (e: Exception) {
            canRequestAd = true
        }
    }

    fun removeCallBacksInstant() {
        try {
            isHandlerRunningInstant = false
            handlerAd.removeCallbacks(runnableInstant)
        } catch (ignored: Exception) {
        }
    }

    private fun onAdFailed() {
        if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
            adLoadingDialog?.dismissAlertDialog()
            removeCallBacksInstant()
            mInterstitialControllerListener?.onAdClosed()
        }
    }

    fun showInterstitial(
        adIdString: String,
        activity: Activity,
        enable: Boolean,
        interstitialControllerListener: InterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || !GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds || AdSharedPreference.getInstance(
                activity
            ).isAppPurchased || !enable || !InternetController.getInstance(activity).isInternetConnected || AdKeys.IS_APP_PAUSE || AdKeys.isShowingOpenAd
        ) {
            if (enable) {
                FirebaseValue.allAppInterstitialAdCount++
            }
            interstitialControllerListener.onAdClosed()
        } else {
            if (FirebaseValue.interstitialCounterStartSplash) {
                startCounterSplashExecute(activity, adIdString)
            } else {
                startCounterMainExecute(activity, adIdString)
            }
        }
    }

    fun showInterstitialEveryClick(
        adIdString: String,
        activity: Activity,
        enableAds: Boolean,
        interstitialControllerListener: InterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || !GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds || AdSharedPreference.getInstance(
                activity
            ).isAppPurchased || !enableAds || !InternetController.getInstance(activity).isInternetConnected || AdKeys.IS_APP_PAUSE || AdKeys.isShowingOpenAd
        ) {
            interstitialControllerListener.onAdClosed()
        } else {
            if (admobInterAd != null) {
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    if (FirebaseValue.INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE) {
                        loadingProgress(activity)
                        handlerAd.postDelayed({
                            setAdmobFullScreen(activity, false, adIdString)
                        }, 1000)
                    } else {
                        setAdmobFullScreen(activity, false, adIdString)
                    }
                } else {
                    loadingProgress(activity)
                    handlerAd.postDelayed({
                        setAdmobFullScreen(activity, false, adIdString)
                    }, 1000)
                }
            } else {
                canRequestAd = true
                admobInterAd = null
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    mInterstitialControllerListener?.onAdClosed()
                }
                loadAd(adIdString, activity)
            }

        }
    }


    fun showInterstitialIfSplashFail(
        adIdString: String,
        activity: Activity,
        enableAds: Boolean,
        interstitialControllerListener: InterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || !GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds || AdSharedPreference.getInstance(
                activity
            ).isAppPurchased || !enableAds || !InternetController.getInstance(activity).isInternetConnected || AdKeys.IS_APP_PAUSE || AdKeys.isShowingOpenAd
        ) {
            interstitialControllerListener.onAdClosed()
        } else {
            if (admobInterAd != null) {
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    if (FirebaseValue.INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE) {
                        loadingProgress(activity)
                        handlerAd.postDelayed({
                            setAdmobFullScreen(activity, false, adIdString)
                        }, 1000)
                    } else {
                        setAdmobFullScreen(activity, false, adIdString)
                    }
                } else {
                    loadingProgress(activity)
                    handlerAd.postDelayed({
                        setAdmobFullScreen(activity, false, adIdString)
                    }, 1000)
                }
            } else {
                canRequestAd = true
                admobInterAd = null
                mInterstitialControllerListener?.onAdClosed()
            }

        }
    }

    fun android15Support() {
        if (Build.VERSION.SDK_INT >= 35) {
            admobInterAd?.setImmersiveMode(true)
        }
    }

    private fun startCounterSplashExecute(
        activity: Activity, adIdString: String
    ) {
        if (FirebaseValue.allAppInterstitialAdCount >= FirebaseValue.allAppInterstitialAdCountChange) {
            FirebaseValue.allAppInterstitialAdCount = 0
            if (admobInterAd != null) {
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    if (FirebaseValue.INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE) {
                        loadingProgress(activity)
                        handlerAd.postDelayed({
                            setAdmobFullScreen(activity, false, adIdString)
                        }, 1000)
                    } else {
                        setAdmobFullScreen(activity, false, adIdString)
                    }
                } else {
                    loadingProgress(activity)
                    handlerAd.postDelayed({
                        setAdmobFullScreen(activity, false, adIdString)
                    }, 1000)
                }
            } else {
                if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadAd(adIdString, activity)
                } else {
                    mInterstitialControllerListener?.onAdClosed()
                }
            }
        } else {
            FirebaseValue.allAppInterstitialAdCount++
            mInterstitialControllerListener?.onAdClosed()
            if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                loadAd(adIdString, activity)
            }
        }
    }

    private fun startCounterMainExecute(
        activity: Activity, adIdString: String
    ) {
        if (FirebaseValue.allAppInterstitialAdCount == 0 || FirebaseValue.allAppInterstitialAdCount >= FirebaseValue.allAppInterstitialAdCountChange) {
            FirebaseValue.allAppInterstitialAdCount = 1
            if (admobInterAd != null) {
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    if (FirebaseValue.INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE) {
                        loadingProgress(activity)
                        handlerAd.postDelayed({
                            setAdmobFullScreen(activity, false, adIdString)
                        }, 1000)
                    } else {
                        setAdmobFullScreen(activity, false, adIdString)
                    }
                } else {
                    loadingProgress(activity)
                    handlerAd.postDelayed({
                        setAdmobFullScreen(activity, false, adIdString)
                    }, 1000)
                }
            } else {
                if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadAd(adIdString, activity)
                } else {
                    mInterstitialControllerListener?.onAdClosed()
                }
            }
        } else {
            FirebaseValue.allAppInterstitialAdCount++
            mInterstitialControllerListener?.onAdClosed()
            if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                loadAd(adIdString, activity)
            }
        }
    }


    private fun loadingProgress(activity: Activity) {
        adLoadingDialog?.dismissAlertDialog()
        adLoadingDialog = null
        adLoadingDialog = AdLoadingDialog(activity)
        adLoadingDialog?.showAlertDialog()
    }

    private fun setAdmobFullScreen(
        activity: Activity, isFromSplash: Boolean, adIdString: String
    ) {
        android15Support()
        adLoadingDialog?.dismissAlertDialog()

        admobInterAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                FirebaseValue.IS_INTER_SHOWING = false
                admobInterAd = null
                adLoadingDialog?.dismissAlertDialog()
                mInterstitialControllerListener?.onAdClosed()
                if (iapScreenShow) {
                    iapScreenShow = false
                    mInterstitialControllerListener?.onIapShow()
                }
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadAd(adIdString, activity)
                }
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                FirebaseValue.IS_INTER_SHOWING = true
                mInterstitialControllerListener?.onSplashAdViewGone()
                adLoadingDialog?.dismissAlertDialog()
                admobInterAd = null
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                super.onAdFailedToShowFullScreenContent(p0)
                admobInterAd = null
                FirebaseValue.IS_INTER_SHOWING = false
                adLoadingDialog?.dismissAlertDialog()
                mInterstitialControllerListener?.onAdClosed()
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadAd(adIdString, activity)
                }
            }
        }

        showAdmobAd(activity)
    }

}