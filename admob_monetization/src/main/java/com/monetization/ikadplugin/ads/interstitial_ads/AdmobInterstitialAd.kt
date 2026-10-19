package com.monetization.ikadplugin.ads.interstitial_ads

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.monetization.ikadplugin.ads.AdKeys
import com.monetization.ikadplugin.ads.AdKeys.iapScreenShow
import com.monetization.ikadplugin.ads.AdLoadingDialog
import com.monetization.ikadplugin.ads.AdRetryPolicy
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.network_instance.IkAdSdk
import com.monetization.ikadplugin.pref.AdSharedPreference
import java.lang.ref.WeakReference

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
        set(value) {
            field = value
            if (value != null) adLoadedAt = AdRetryPolicy.now()
        }
    private var adLoadedAt = 0L
    private val retryPolicy = AdRetryPolicy()

    /**
     * True from [initAdMobSplash] until the splash flow ends (timeout, close, show). An ad that
     * arrives after the window closed is kept for the next placement, never shown over the screen
     * the user has already moved on to.
     */
    private var splashWindowOpen = false
    private var mInterstitialControllerListener: InterstitialControllerListener? = null
    private var isHandlerRunning = false
    private var adIdReference = ""
    private var isHandlerRunningInstant = false
    private var canDispatchTerminalCallback = false
    private val runnableInstant = Runnable {
        if (mInterstitialControllerListener != null && isHandlerRunningInstant) {
            canRequestAd = true
            adLoadingDialog?.dismissAlertDialog()
            isHandlerRunningInstant = false
            notifyAdClosedOnce()
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
            notifyAdClosedOnce()
            activityRef?.get()?.let {
                AdClickDurationTracker.adRequestDurationEnd(
                    it, adType = AdType.INTERSTITIAL, adIdReferenceName = "Splash"
                )
            }

        }
    }
    private var adLoadingDialog: AdLoadingDialog? = null
    private var isPauseDone = false

    private fun markTerminalPending() {
        canDispatchTerminalCallback = true
    }

    private fun notifyAdClosedOnce() {
        splashWindowOpen = false
        if (!canDispatchTerminalCallback) return
        canDispatchTerminalCallback = false
        mInterstitialControllerListener?.onAdClosed()
    }

    fun resetSplash() {
        isExitAppCall = false
        mInterstitialControllerListener = null
        isPauseDone = false
        isHandlerRunning = false
        splashWindowOpen = false
    }

    fun hasAd(): Boolean {
        dropExpiredAd()
        return (admobInterAd != null)
    }

    /** Forgets a loaded ad that is too old to show reliably. */
    private fun dropExpiredAd() {
        if (admobInterAd != null && AdRetryPolicy.now() - adLoadedAt > AdRetryPolicy.MAX_AD_AGE_MS) {
            admobInterAd = null
        }
    }

    /** Consent / ads-off / purchase / network gate shared by every load path. */
    private fun canLoad(context: Activity): Boolean =
        !FirebaseValue.ALL_ADS_OFF_ENABLE &&
            GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
            !AdSharedPreference.getInstance(context).isAppPurchased &&
            InternetController.getInstance(context).isInternetConnected

    /** Retries a failed background preload with backoff, for transient errors only. */
    private fun scheduleRetry(errorCode: Int, adIdReferenceName: String, context: Activity) {
        if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE || !AdRetryPolicy.isRetryable(errorCode)) return
        val ref = WeakReference(context)
        retryPolicy.schedule {
            val retryActivity = ref.get() ?: IkAdSdk.getCurrentActivity()
            if (retryActivity != null && !retryActivity.isFinishing && !retryActivity.isDestroyed) {
                preLoadAd(adIdReferenceName, retryActivity)
            }
        }
    }

    fun pauseAd() {
        isPauseDone = true
        removeCallBacks()
    }

    private var activityRef: WeakReference<Activity>? = null
    private fun startHandler() {
        if (!isHandlerRunning) {
            isHandlerRunning = true
            handlerAd.postDelayed(runnable, FirebaseValue.SPLASH_TIME.coerceAtLeast(1L) * 1000L)
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
                notifyAdClosedOnce()
            }, delay
        )
    }


    fun onDestroy() {
        removeCallBacks()
    }


    fun resumeAd(
        activity: Activity, enable: Boolean
    ) {
        if (isPauseDone && splashWindowOpen && !FirebaseValue.IS_INTER_SHOWING) {
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
        markTerminalPending()
        dropExpiredAd()
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
        splashWindowOpen = true
        activityRef = WeakReference(context)
        dropExpiredAd()
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
                    adIdReference = adIdReferenceName
                    if (!isHandlerRunning) {
                        startHandler()
                    }
                    AdClickDurationTracker.adRequestCalling(
                        context, adType = AdType.INTERSTITIAL, adIdReferenceName = adIdReferenceName
                    )

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
                                adIdReference = adIdReferenceName
                                retryPolicy.reset()
                                // Show only inside the splash window. A late ad (timeout already
                                // fired, or the app is paused and resumeAd() will show it) stays
                                // cached for the next placement.
                                if (splashWindowOpen && !isPauseDone) {
                                    mInterstitialControllerListener?.onAdLoaded()
                                    showSplashInterstitial(
                                        context, true
                                    )
                                }
                                AdClickDurationTracker.adRequestMatch(
                                    context,
                                    adType = AdType.INTERSTITIAL,
                                    adIdReferenceName = adIdReferenceName
                                )
                            }

                            override fun onAdFailedToLoad(p0: LoadAdError) {
                                super.onAdFailedToLoad(p0)
                                canRequestAd = true
                                if (isHandlerRunning) {
                                    removeCallBacks()
                                    notifyAdClosedOnce()

                                }
                                AdClickDurationTracker.adRequestFail(
                                    context,
                                    adType = AdType.INTERSTITIAL,
                                    adIdReferenceName = adIdReferenceName,
                                    errorCode = p0.code,
                                    errorMessage = p0.message
                                )

                            }
                        })
                }
            } else {
                closeHandler(1000)
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(
                context,
                adType = "INTERSTITIAL",
                stage = "SPLASH_LOAD",
                placement = adIdReferenceName,
                throwable = e,
                message = "Splash interstitial load flow failed."
            )
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
            notifyAdClosedOnce()
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
            notifyAdClosedOnce()
        }
    }


    private fun showAdmobAd(activity: Activity) {
        try {
            if (admobInterAd != null && !AdKeys.IS_APP_PAUSE && !AdKeys.isShowingOpenAd) {
                AdClickDurationTracker.adShow(
                    activity, adType = AdType.INTERSTITIAL, adIdReferenceName = adIdReference
                )
                admobInterAd?.show(activity)
            } else {
                notifyAdClosedOnce()
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(
                activity,
                adType = "INTERSTITIAL",
                stage = "SHOW",
                placement = adIdReference,
                throwable = e,
                message = "Interstitial show failed."
            )
            notifyAdClosedOnce()
        }
    }


    fun isExitAppCall() {
        isExitAppCall = true
    }

    private fun loadAd(adIdReferenceName: String, mContext: Activity) {
        try {
            if (canLoad(mContext)) {
                dropExpiredAd()
                if (admobInterAd != null) {
                    return
                }
                if (!canRequestAd) {
                    return
                }
                canRequestAd = false
                adIdReference = adIdReferenceName

                AdClickDurationTracker.adRequestCalling(
                    mContext, adType = AdType.INTERSTITIAL, adIdReferenceName = adIdReferenceName
                )
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
                            adIdReference = adIdReferenceName
                            retryPolicy.reset()
                            if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                                setAdmobFullScreen(activity = mContext, false, adIdReferenceName)
                            }
                            AdClickDurationTracker.adRequestMatch(
                                mContext,
                                adType = AdType.INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName
                            )
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            canRequestAd = true
                            admobInterAd = null
                            if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                                notifyAdClosedOnce()
                            }
                            AdClickDurationTracker.adRequestFail(
                                mContext,
                                adType = AdType.INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName,
                                errorCode = p0.code,
                                errorMessage = p0.message
                            )
                            scheduleRetry(p0.code, adIdReferenceName, mContext)

                        }
                    })
            } else {
                canRequestAd = true
                adLoadingDialog?.dismissAlertDialog()
                if (!FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    notifyAdClosedOnce()
                }
            }
        } catch (e: Exception) {
            canRequestAd = true
            AdClickDurationTracker.error(
                mContext,
                adType = "INTERSTITIAL",
                stage = "LOAD",
                placement = adIdReferenceName,
                throwable = e,
                message = "Interstitial load flow failed."
            )
            onAdFailed()
        }
    }

    private fun loadAdInstant(adIdReferenceName: String, mContext: Activity) {
        try {
            if (canLoad(mContext)) {
                dropExpiredAd()
                if (admobInterAd != null) {
                    return
                }
                if (!canRequestAd) {
                    return
                }
                canRequestAd = false
                adIdReference = adIdReferenceName

                AdClickDurationTracker.adRequestCalling(
                    mContext, adType = AdType.INTERSTITIAL, adIdReferenceName = adIdReferenceName
                )
                val adId = FetchConfig.getInterstitialId(adIdReferenceName)

                startHandlerInstant()
                loadingProgress(mContext)

                InterstitialAd.load(
                    mContext,
                    adId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(p0: InterstitialAd) {
                            super.onAdLoaded(p0)
                            canRequestAd = true
                            admobInterAd = p0
                            adIdReference = adIdReferenceName
                            retryPolicy.reset()
                            if (isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                                setAdmobFullScreen(activity = mContext, false, adIdReferenceName)
                            }
                            AdClickDurationTracker.adRequestMatch(
                                mContext,
                                adType = AdType.INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName
                            )
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            canRequestAd = true
                            admobInterAd = null
                            if (isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                                notifyAdClosedOnce()
                            }
                            AdClickDurationTracker.adRequestFail(
                                mContext,
                                adType = AdType.INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName,
                                errorCode = p0.code,
                                errorMessage = p0.message
                            )
                            scheduleRetry(p0.code, adIdReferenceName, mContext)

                        }
                    })
            } else {
                canRequestAd = true
                adLoadingDialog?.dismissAlertDialog()
                notifyAdClosedOnce()
            }
        } catch (e: Exception) {
            canRequestAd = true
            AdClickDurationTracker.error(
                mContext,
                adType = "INTERSTITIAL",
                stage = "LOAD",
                placement = adIdReferenceName,
                throwable = e,
                message = "Interstitial load flow failed."
            )
            adLoadingDialog?.dismissAlertDialog()
            removeCallBacksInstant()
            notifyAdClosedOnce()
        }
    }


    fun preLoadAd(
        adIdReferenceName: String, mContext: Activity
    ) {
        try {
            if (canLoad(mContext)) {
                dropExpiredAd()
                if (admobInterAd != null) {
                    return
                }
                if (!canRequestAd) {
                    return
                }
                canRequestAd = false
                adIdReference = adIdReferenceName

                AdClickDurationTracker.adRequestCalling(
                    mContext, adType = AdType.INTERSTITIAL, adIdReferenceName = adIdReferenceName
                )
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
                            retryPolicy.reset()

                            AdClickDurationTracker.adRequestMatch(
                                mContext,
                                adType = AdType.INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName
                            )
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            AdClickDurationTracker.adRequestFail(
                                mContext,
                                adType = AdType.INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName,
                                errorCode = p0.code,
                                errorMessage = p0.message
                            )
                            canRequestAd = true
                            admobInterAd = null
                            scheduleRetry(p0.code, adIdReferenceName, mContext)
                        }
                    })
            } else {
                canRequestAd = true
            }
        } catch (e: Exception) {
            canRequestAd = true
            AdClickDurationTracker.error(
                mContext,
                adType = "INTERSTITIAL",
                stage = "PRELOAD",
                placement = adIdReferenceName,
                throwable = e,
                message = "Interstitial preload flow failed."
            )
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
            notifyAdClosedOnce()
        }
    }

    fun resetInterstitialCounter() {
        FirebaseValue.allAppInterstitialAdCount = 0
    }

    fun counterUpdateInterstitialCounter(counterEnable: Boolean) {
        if(counterEnable){
            FirebaseValue.allAppInterstitialAdCount++
        }
    }

    fun showInterstitial(
        adIdString: String,
        activity: Activity,
        enable: Boolean,
        interstitialControllerListener: InterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        markTerminalPending()
        dropExpiredAd()
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

    fun showInterstitialPreLoad(
        adIdString: String,
        activity: Activity,
        enable: Boolean,
        interstitialControllerListener: InterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        markTerminalPending()
        dropExpiredAd()
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
                startCounterSplashExecutePreLoad(activity, adIdString)
            } else {
                startCounterMainExecutePreLoad(activity, adIdString)
            }
        }
    }

    fun showInterstitialClickAndBack(
        isBackPressAdShow: Boolean,
        adIdString: String,
        activity: Activity,
        enable: Boolean,
        interstitialControllerListener: InterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        markTerminalPending()
        dropExpiredAd()
        if (!isBackPressAdShow || FirebaseValue.ALL_ADS_OFF_ENABLE || !GoogleMobileAdsConsentManager.getInstance(
                activity
            ).canRequestAds || AdSharedPreference.getInstance(
                activity
            ).isAppPurchased || !enable || !InternetController.getInstance(activity).isInternetConnected || AdKeys.IS_APP_PAUSE || AdKeys.isShowingOpenAd
        ) {
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
        markTerminalPending()
        dropExpiredAd()
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
                    notifyAdClosedOnce()
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
        markTerminalPending()
        dropExpiredAd()
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
                notifyAdClosedOnce()
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadAd(adIdString, activity)
                }
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
                    // The preload failed or expired: start the next one now so the following
                    // eligible tap has an ad.
                    loadAd(adIdString, activity)
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
                    // The preload failed or expired: start the next one now so the following
                    // eligible tap has an ad.
                    loadAd(adIdString, activity)
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

    private fun startCounterSplashExecutePreLoad(
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
                loadAdInstant(adIdString, activity)
            }
        } else {
            FirebaseValue.allAppInterstitialAdCount++
            mInterstitialControllerListener?.onAdClosed()
            if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                loadAd(adIdString, activity)
            }
        }
    }

    private fun startCounterMainExecutePreLoad(
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
                loadAdInstant(adIdString, activity)
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
        // Placement the ad is shown at (the splash passes ""), used for events and the next preload.
        val placement = adIdString.ifEmpty { adIdReference }
        val appContext = activity.applicationContext

        admobInterAd?.let { ad ->
            ad.setOnPaidEventListener { value ->
                AdClickDurationTracker.adPaidEvent(
                    appContext, AdType.INTERSTITIAL, placement, value, ad.responseInfo
                )
            }
        }
        admobInterAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                super.onAdClicked()
                AdClickDurationTracker.startTracking(
                    activity, adType = AdType.INTERSTITIAL, adIdReferenceName = placement
                )
            }

            override fun onAdImpression() {
                super.onAdImpression()
                AdClickDurationTracker.adImpression(
                    appContext, adType = AdType.INTERSTITIAL, adIdReferenceName = placement
                )
            }

            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                FirebaseValue.IS_INTER_SHOWING = false
                admobInterAd = null
                adLoadingDialog?.dismissAlertDialog()
                notifyAdClosedOnce()
                if (iapScreenShow) {
                    iapScreenShow = false
                    mInterstitialControllerListener?.onIapShow()
                }
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadAd(placement, activity)
                }
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                FirebaseValue.IS_INTER_SHOWING = true
                splashWindowOpen = false
                mInterstitialControllerListener?.onSplashAdViewGone()
                adLoadingDialog?.dismissAlertDialog()
                admobInterAd = null
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                super.onAdFailedToShowFullScreenContent(p0)
                AdClickDurationTracker.adShowFail(
                    appContext,
                    adType = AdType.INTERSTITIAL,
                    adIdReferenceName = placement,
                    errorCode = p0.code,
                    errorMessage = p0.message
                )
                admobInterAd = null
                FirebaseValue.IS_INTER_SHOWING = false
                adLoadingDialog?.dismissAlertDialog()
                notifyAdClosedOnce()
                if (FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadAd(placement, activity)
                }
            }
        }

        showAdmobAd(activity)
    }

}