package com.monetization.ikadplugin.ads.rewarded_interstitial

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.monetization.ikadplugin.ads.AdKeys
import com.monetization.ikadplugin.ads.AdKeys.iapScreenShow
import com.monetization.ikadplugin.ads.AdLoadingDialog
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference

class AdmobRewardedInterstitialAd {

    companion object {

        @Volatile
        private var instance: AdmobRewardedInterstitialAd? = null

        fun getInstance(
        ): AdmobRewardedInterstitialAd {

            return instance ?: synchronized(this) {
                instance ?: AdmobRewardedInterstitialAd(
                ).also { instance = it }
            }
        }
    }

    private val handlerAd: Handler = Handler(Looper.getMainLooper())
    private var isExitAppCall = false
    private var canRequestAd = true
    private var admobInterAd: RewardedInterstitialAd? = null
    private var mInterstitialControllerListener: RewardedInterstitialControllerListener? = null
    private var adIdReference = ""
    private var isHandlerRunningInstant = false
    private var canDispatchTerminalCallback = false
    private val runnableInstant = Runnable {
        if (mInterstitialControllerListener != null && isHandlerRunningInstant) {
            canRequestAd = true
            adLoadingDialog?.dismissAlertDialog()
            isHandlerRunningInstant = false
            try {
                notifyAdClosedOnce()
            } catch (_: Exception) {
            }
        }
    }

    private fun startHandlerInstant() {
        isHandlerRunningInstant = true
        handlerAd.postDelayed(runnableInstant, 6000)
    }


    private var adLoadingDialog: AdLoadingDialog? = null

    private fun markTerminalPending() {
        canDispatchTerminalCallback = true
    }

    private fun notifyAdClosedOnce() {
        if (!canDispatchTerminalCallback) return
        canDispatchTerminalCallback = false
        mInterstitialControllerListener?.onAdClosed()
    }


    fun hasAd(): Boolean {
        return (admobInterAd != null)
    }

    private fun showAdmobAd(activity: Activity) {
        try {
            if (admobInterAd != null && !AdKeys.IS_APP_PAUSE && !AdKeys.isShowingOpenAd) {
                admobInterAd?.show(activity) { rewardItem ->
                    isRewardComplete = true
                    // Handle the reward.

                    val rewardAmount = rewardItem.amount
                    val rewardType = rewardItem.type
                    mInterstitialControllerListener?.onUserEarnedReward(rewardType, rewardAmount)
                    AdClickDurationTracker.userEarnedRewardedValue(
                        activity, rewardType, rewardAmount
                    )
                }
                AdClickDurationTracker.adShow(
                    activity,
                    adType = AdType.REWARDED_INTERSTITIAL,
                    adIdReferenceName = adIdReference
                )
            } else {
                notifyAdClosedOnce()
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(
                activity,
                adType = "REWARDED_INTERSTITIAL",
                stage = "SHOW",
                placement = adIdReference,
                throwable = e,
                message = "Rewarded interstitial show failed."
            )
            notifyAdClosedOnce()
        }
    }


    fun isExitAppCall() {
        isExitAppCall = true
    }

    private fun loadRewardedAd(adIdReferenceName: String, mContext: Activity) {
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
                adIdReference = adIdReferenceName

                AdClickDurationTracker.adRequestCalling(
                    mContext,
                    adType = AdType.REWARDED_INTERSTITIAL,
                    adIdReferenceName = adIdReferenceName
                )
                val adId = FetchConfig.getRewardedInterstitialId(adIdReferenceName)

                if (!FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    startHandlerInstant()
                    loadingProgress(mContext)
                }
                RewardedInterstitialAd.load(
                    mContext,
                    adId,
                    AdRequest.Builder().build(),
                    object : RewardedInterstitialAdLoadCallback() {
                        override fun onAdLoaded(p0: RewardedInterstitialAd) {
                            super.onAdLoaded(p0)
                            canRequestAd = true
                            admobInterAd = p0
                            adIdReference = adIdReferenceName
                            if (!FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                                setAdmobFullScreen(activity = mContext, "")
                            }
                            AdClickDurationTracker.adRequestMatch(
                                mContext,
                                adType = AdType.REWARDED_INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName
                            )
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            canRequestAd = true
                            admobInterAd = null
                            if (!FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
                                adLoadingDialog?.dismissAlertDialog()
                                removeCallBacksInstant()
                            }
                            canDispatchTerminalCallback = false
                            mInterstitialControllerListener?.onAdFailed()
                            AdClickDurationTracker.adRequestFail(
                                mContext,
                                adType = AdType.REWARDED_INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName
                            )

                        }
                    })
            } else {
                canRequestAd = true
                adLoadingDialog?.dismissAlertDialog()
                if (!FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    notifyAdClosedOnce()
                }
            }
        } catch (e: Exception) {
            canRequestAd = true
            AdClickDurationTracker.error(
                mContext,
                adType = "REWARDED_INTERSTITIAL",
                stage = "LOAD",
                placement = adIdReferenceName,
                throwable = e,
                message = "Rewarded interstitial load flow failed."
            )
            onAdFailed()
        }
    }


    fun preLoadAd(
        adIdReferenceName: String,
        mContext: AppCompatActivity,
        interstitialControllerListener: RewardedInterstitialControllerListener
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
                adIdReference = adIdReferenceName

                AdClickDurationTracker.adRequestCalling(
                    mContext,
                    adType = AdType.REWARDED_INTERSTITIAL,
                    adIdReferenceName = adIdReferenceName
                )
                val adId = FetchConfig.getRewardedInterstitialId(adIdReferenceName)

                RewardedInterstitialAd.load(
                    mContext,
                    adId,
                    AdRequest.Builder().build(),
                    object : RewardedInterstitialAdLoadCallback() {
                        override fun onAdLoaded(p0: RewardedInterstitialAd) {
                            super.onAdLoaded(p0)
                            canRequestAd = true
                            admobInterAd = p0

                            AdClickDurationTracker.adRequestMatch(
                                mContext,
                                adType = AdType.REWARDED_INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName
                            )
                        }

                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            AdClickDurationTracker.adRequestFail(
                                mContext,
                                adType = AdType.REWARDED_INTERSTITIAL,
                                adIdReferenceName = adIdReferenceName
                            )
                            canDispatchTerminalCallback = false
                            interstitialControllerListener.onAdFailed()
                            canRequestAd = true
                            admobInterAd = null
                        }
                    })
            } else {
                canRequestAd = true
            }
        } catch (e: Exception) {
            canRequestAd = true
            AdClickDurationTracker.error(
                mContext,
                adType = "REWARDED_INTERSTITIAL",
                stage = "PRELOAD",
                placement = adIdReferenceName,
                throwable = e,
                message = "Rewarded interstitial preload flow failed."
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
        if (!FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE && isHandlerRunningInstant) {
            adLoadingDialog?.dismissAlertDialog()
            removeCallBacksInstant()
            notifyAdClosedOnce()
        }
    }

    fun showRewardedInterstitial(
        adIdString: String,
        activity: Activity,
        enable: Boolean,
        interstitialControllerListener: RewardedInterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        markTerminalPending()
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

    fun showRewardedInterstitialEveryClick(
        adIdString: String,
        activity: Activity,
        enableAds: Boolean,
        interstitialControllerListener: RewardedInterstitialControllerListener
    ) {
        mInterstitialControllerListener = interstitialControllerListener
        markTerminalPending()
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || !GoogleMobileAdsConsentManager.getInstance(activity).canRequestAds || AdSharedPreference.getInstance(
                activity
            ).isAppPurchased || !enableAds || !InternetController.getInstance(activity).isInternetConnected || AdKeys.IS_APP_PAUSE || AdKeys.isShowingOpenAd
        ) {
            interstitialControllerListener.onAdClosed()
        } else {
            if (admobInterAd != null) {
                if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE) {
                        loadingProgress(activity)
                        handlerAd.postDelayed({
                            setAdmobFullScreen(activity, adIdString)
                        }, 1000)
                    } else {
                        setAdmobFullScreen(activity, adIdString)
                    }
                } else {
                    loadingProgress(activity)
                    handlerAd.postDelayed({
                        setAdmobFullScreen(activity, adIdString)
                    }, 1000)
                }
            } else {
                canRequestAd = true
                admobInterAd = null
                if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    notifyAdClosedOnce()
                }
                loadRewardedAd(adIdString, activity)
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
                if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE) {
                        loadingProgress(activity)
                        handlerAd.postDelayed({
                            setAdmobFullScreen(activity, adIdString)
                        }, 1000)
                    } else {
                        setAdmobFullScreen(activity, adIdString)
                    }
                } else {
                    loadingProgress(activity)
                    handlerAd.postDelayed({
                        setAdmobFullScreen(activity, adIdString)
                    }, 1000)
                }
            } else {
                if (!FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadRewardedAd(adIdString, activity)
                } else {
                    mInterstitialControllerListener?.onAdClosed()
                }
            }
        } else {
            FirebaseValue.allAppInterstitialAdCount++
            mInterstitialControllerListener?.onAdClosed()
            if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                loadRewardedAd(adIdString, activity)
            }
        }
    }

    private fun startCounterMainExecute(
        activity: Activity, adIdString: String
    ) {
        if (FirebaseValue.allAppInterstitialAdCount == 0 || FirebaseValue.allAppInterstitialAdCount >= FirebaseValue.allAppInterstitialAdCountChange) {
            FirebaseValue.allAppInterstitialAdCount = 1
            if (admobInterAd != null) {
                if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE) {
                        loadingProgress(activity)
                        handlerAd.postDelayed({
                            setAdmobFullScreen(activity, adIdString)
                        }, 1000)
                    } else {
                        setAdmobFullScreen(activity, adIdString)
                    }
                } else {
                    loadingProgress(activity)
                    handlerAd.postDelayed({
                        setAdmobFullScreen(activity, adIdString)
                    }, 1000)
                }
            } else {
                if (!FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadRewardedAd(adIdString, activity)
                } else {
                    mInterstitialControllerListener?.onAdClosed()
                }
            }
        } else {
            FirebaseValue.allAppInterstitialAdCount++
            mInterstitialControllerListener?.onAdClosed()
            if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                loadRewardedAd(adIdString, activity)
            }
        }
    }


    private fun loadingProgress(activity: Activity) {
        adLoadingDialog?.dismissAlertDialog()
        adLoadingDialog = null
        adLoadingDialog = AdLoadingDialog(activity)
        adLoadingDialog?.showAlertDialog()
    }

    var isRewardComplete = false

    private fun setAdmobFullScreen(
        activity: Activity, adIdString: String
    ) {
        android15Support()
        adLoadingDialog?.dismissAlertDialog()
        isRewardComplete = false
        admobInterAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                super.onAdClicked()
                AdClickDurationTracker.startTracking(
                    activity,
                    adType = AdType.REWARDED_INTERSTITIAL,
                    adIdReferenceName = adIdReference
                )
            }

            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                FirebaseValue.IS_INTER_SHOWING = false
                admobInterAd = null
                adLoadingDialog?.dismissAlertDialog()
                mInterstitialControllerListener?.onAdRewardGranted(isRewardComplete)
                notifyAdClosedOnce()
                if (iapScreenShow) {
                    iapScreenShow = false
                    mInterstitialControllerListener?.onIapShow()
                }
                if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadRewardedAd(adIdString, activity)
                }
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                FirebaseValue.IS_INTER_SHOWING = true
                adLoadingDialog?.dismissAlertDialog()
                admobInterAd = null
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                super.onAdFailedToShowFullScreenContent(p0)
                AdClickDurationTracker.adRequestFail(
                    activity,
                    adType = AdType.REWARDED_INTERSTITIAL,
                    adIdReferenceName = adIdReference
                )
                admobInterAd = null
                FirebaseValue.IS_INTER_SHOWING = false
                adLoadingDialog?.dismissAlertDialog()
                canDispatchTerminalCallback = false
                mInterstitialControllerListener?.onAdFailed()
                if (FirebaseValue.REWARDED_INTERSTITIAL_PRE_LOAD_ENABLE) {
                    loadRewardedAd(adIdString, activity)
                }
            }
        }

        showAdmobAd(activity)
    }


}