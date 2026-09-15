package com.monetization.ikadplugin.ads.open_ap_ads

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.monetization.ikadplugin.ads.AdKeys
import com.monetization.ikadplugin.ads.AdLoadingDialog
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.network_instance.IkAdSdk.getCurrentActivity
import com.monetization.ikadplugin.pref.AdSharedPreference
import java.util.Date

class AdmobOpenAppAd : LifecycleObserver, DefaultLifecycleObserver {

    companion object {

        private const val BEFORE_ACTIVITY_SHOW_DELAY = 1500L
        private const val PROGRESS_LOADING_DELAY = 1000L
        private const val ON_START_SHOW_DELAY = 300L

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
    private var isObserverRegistered = false
    private var openAdControllerListener: OpenAdControllerListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isOpenAdShowPending = false
    private var isBeforeActivityShowNotified = false


    fun initOpenAd(
        context: Context,
        adRef: String,
        openAdEnable: Boolean,
        openAdControllerListener: OpenAdControllerListener? = null
    ) {
        this.adRef = adRef
        this.openAdControllerListener = openAdControllerListener
        this.appContext = context
        this.openAdEnable = openAdEnable
        if (!isObserverRegistered) {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
                isObserverRegistered = true
            }.onFailure {
                Log.e("OpenAd", "Failed to add lifecycle observer", it)
                AdClickDurationTracker.error(
                    context,
                    adType = "APP_OPEN",
                    stage = "LIFECYCLE_ADD_OBSERVER",
                    placement = adRef,
                    throwable = it,
                    message = "Failed to register process lifecycle observer."
                )
            }
        }

    }

    fun setOpenAdControllerListener(listener: OpenAdControllerListener?) {
        this.openAdControllerListener = listener
    }

    fun releaseOpenAd() {
        runCatching {
            if (isObserverRegistered) {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
                isObserverRegistered = false
            }
        }.onFailure {
            Log.e("OpenAd", "Failed to remove lifecycle observer", it)
            val ctx = appContext ?: return

            AdClickDurationTracker.error(
                ctx,
                adType = "APP_OPEN",
                stage = "LIFECYCLE_REMOVE_OBSERVER",
                placement = adRef,
                throwable = it,
                message = "Failed to unregister process lifecycle observer."
            )
        }
        mainHandler.removeCallbacksAndMessages(null)
        isOpenAdShowPending = false
        isBeforeActivityShowNotified = false
        hideProgress()
        mAppOpenAd = null
        appContext = null
        openAdControllerListener = null
    }

    fun openAppAdDisableWhenPermissionCheck() {
        AdKeys.isPermissionCheck = true
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        val ctx = appContext ?: return
        try {
            AdKeys.IS_APP_PAUSE = false
            if (!AdKeys.isPermissionCheck && AdKeys.canShowOpenAd && !AdSharedPreference.getInstance(
                    ctx
                ).isAppPurchased && openAdEnable
            ) {
                mainHandler.postDelayed({
                    if (getCurrentActivity() != null) {
                        showOpenAd()
                    } else {
                        Log.e("OpenAd", "Activity still null after delay")
                        AdClickDurationTracker.warn(
                            ctx,
                            adType = "APP_OPEN",
                            stage = "ON_START",
                            placement = adRef,
                            message = "Current activity is null; skipping open ad display."
                        )
                    }
                }, ON_START_SHOW_DELAY)
            } else {
                AdKeys.isPermissionCheck = false
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(
                ctx,
                adType = "APP_OPEN",
                stage = "ON_START",
                placement = adRef,
                throwable = e,
                message = "Exception while handling app onStart for open ad."
            )
        }
    }

    private fun fetchAd() {

        val ctx = appContext ?: return
        if (FirebaseValue.ALL_ADS_OFF_ENABLE || isAdAvailable || !InternetController.getInstance(
                ctx
            ).isInternetConnected || AdSharedPreference.getInstance(ctx).isAppPurchased || AdKeys.IS_APP_PAUSE
        ) {
            return
        }
        if (!canRequestAd) {
            return
        }
        canRequestAd = false
        AdClickDurationTracker.adRequestCalling(
            ctx, adType = AdType.APP_OPEN, adIdReferenceName = adRef
        )
        val adId = FetchConfig.getOpenAppAdId(adRef)
        AppOpenAd.load(
            ctx, adId, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    super.onAdLoaded(appOpenAd)
                    canRequestAd = true
                    mAppOpenAd = appOpenAd
                    loadTime = Date().time
                    AdClickDurationTracker.adRequestMatch(
                        ctx, adType = AdType.APP_OPEN, adIdReferenceName = adRef
                    )
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    AdClickDurationTracker.adRequestFail(
                        ctx, adType = AdType.APP_OPEN, adIdReferenceName = adRef
                    )
                    canRequestAd = true
                    mAppOpenAd = null
                }
            })
    }

    private fun hideShowProgress(activity: Activity) {
        try {
            hideProgress()
            adLoadingDialog = AdLoadingDialog(activity)
            adLoadingDialog?.showAlertDialog()
        } catch (e: Exception) {
            AdClickDurationTracker.warn(
                activity,
                adType = "APP_OPEN",
                stage = "PROGRESS_SHOW",
                placement = adRef,
                message = "Unable to show open-ad progress: ${e.message}"
            )
        }
    }

    private fun hideProgress() {
        try {
            adLoadingDialog?.dismissAlertDialog()
        } catch (e: Exception) {
            val ctx = appContext ?: return
            AdClickDurationTracker.warn(
                ctx,
                adType = "APP_OPEN",
                stage = "PROGRESS_HIDE",
                placement = adRef,
                message = "Unable to hide open-ad progress: ${e.message}"
            )
        }
    }

    private fun setBlackColor() {
        try {
            adLoadingDialog?.setBlackColor()
        } catch (e: Exception) {
            val ctx = appContext ?: return
            AdClickDurationTracker.warn(
                ctx,
                adType = "APP_OPEN",
                stage = "PROGRESS_STYLE",
                placement = adRef,
                message = "Unable to apply open-ad progress styling: ${e.message}"
            )
        }
    }

    private fun showOpenAd() {
        if (!AdKeys.isShowingOpenAd && !isOpenAdShowPending && isAdAvailable) {
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
        if (mAppOpenAd == null) {
            callback.invoke()
            return
        }
        isOpenAdShowPending = true
        if (FirebaseValue.OPEN_AD_BEFORE_ACTIVITY_SHOW_ENABLE && openAdControllerListener != null) {
            notifyBeforeOpenAdActivityShow(mContext)
            mainHandler.postDelayed({
                // The activity the host started is in front by now, so the progress
                // dialog is attached to it and not to the activity captured above.
                val currentActivity = getCurrentActivity()
                if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
                    abortOpenAdShow(callback)
                } else {
                    launchOpenAd(currentActivity, callback)
                }
            }, BEFORE_ACTIVITY_SHOW_DELAY)
        } else {
            launchOpenAd(mContext, callback)
        }
    }

    private fun launchOpenAd(mContext: Activity, callback: () -> Unit) {
        if (FirebaseValue.PROGRESS_LOADING_OPEN_AP_ENABLE) {
            try {
                hideShowProgress(mContext)
                mainHandler.postDelayed({
                    nowShowAd(callback)
                }, PROGRESS_LOADING_DELAY)
            } catch (e: Exception) {
                AdClickDurationTracker.error(
                    mContext,
                    adType = "APP_OPEN",
                    stage = "SHOW_PROGRESS",
                    placement = adRef,
                    throwable = e,
                    message = "Failed showing open-ad loading progress."
                )
                nowShowAd(callback)
            }
        } else {
            nowShowAd(callback)
        }
    }

    /**
     * Resolves the activity again at show time: the one captured when the flow started can be
     * stopped or finished by now, either because the host started its own activity from
     * [OpenAdControllerListener.beforeOpenAdActivityShow] or because the delay outlived it.
     */
    private fun nowShowAd(callback: () -> Unit) {
        val appOpenAd = mAppOpenAd
        val mContext = getCurrentActivity()
        if (appOpenAd == null || mContext == null || mContext.isFinishing || mContext.isDestroyed
            || !canShowOpenAdNow()
        ) {
            abortOpenAdShow(callback)
            return
        }
        if (Build.VERSION.SDK_INT >= 35) {
            appOpenAd.setImmersiveMode(true)
        }
        appOpenAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                super.onAdClicked()

                AdClickDurationTracker.startTracking(
                    mContext, adType = AdType.APP_OPEN, adIdReferenceName = adRef
                )
            }

            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                mAppOpenAd = null
                AdKeys.isShowingOpenAd = false
                isOpenAdShowPending = false
                hideProgress()
                notifyDismissOpenAdCalling(mContext)
                callback.invoke()
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                AdKeys.isShowingOpenAd = true
                isOpenAdShowPending = false
                if (!FirebaseValue.OPEN_AD_BEFORE_ACTIVITY_SHOW_ENABLE) {
                    setBlackColor()
                }
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                super.onAdFailedToShowFullScreenContent(p0)
                AdClickDurationTracker.adRequestFail(
                    mContext, adType = AdType.APP_OPEN, adIdReferenceName = adRef
                )
                hideProgress()
                mAppOpenAd = null
                AdKeys.isShowingOpenAd = false
                isOpenAdShowPending = false
                notifyDismissOpenAdCalling(mContext)
                callback.invoke()
            }
        }
        AdClickDurationTracker.adShow(
            mContext, adType = AdType.APP_OPEN, adIdReferenceName = adRef
        )
        appOpenAd.show(mContext)
    }

    /**
     * Re-checks, right before showing, everything [showOpenAd] checked before the delays,
     * so a stale decision never puts an ad on screen.
     */
    private fun canShowOpenAdNow(): Boolean {
        val ctx = appContext ?: return false
        if (AdKeys.isShowingOpenAd || FirebaseValue.IS_INTER_SHOWING) {
            return false
        }
        if (AdSharedPreference.getInstance(ctx).isAppPurchased) {
            return false
        }
        if (!wasLoadTimeLessThanNHoursAgo()) {
            return false
        }
        return isAppInForeground()
    }

    /**
     * Process level foreground check. [AdKeys.IS_APP_PAUSE] cannot be used here because the
     * activity the host starts behind the ad flips it while the app is still in the foreground.
     */
    private fun isAppInForeground(): Boolean {
        return runCatching {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }.getOrDefault(true)
    }

    /**
     * Gives up on a scheduled show and, when the host was already told to start its activity,
     * tells it the ad is gone so the screen it started is not left waiting.
     */
    private fun abortOpenAdShow(callback: () -> Unit) {
        isOpenAdShowPending = false
        hideProgress()
        if (isBeforeActivityShowNotified) {
            appContext?.let { notifyDismissOpenAdCalling(it) }
        }
        callback.invoke()
    }

    private fun notifyBeforeOpenAdActivityShow(mContext: Context) {
        if (!FirebaseValue.OPEN_AD_BEFORE_ACTIVITY_SHOW_ENABLE) {
            return
        }
        val listener = openAdControllerListener ?: return
        try {
            isBeforeActivityShowNotified = true
            listener.beforeOpenAdActivityShow(true)
        } catch (e: Exception) {
            AdClickDurationTracker.error(
                mContext,
                adType = "APP_OPEN",
                stage = "BEFORE_ACTIVITY_SHOW",
                placement = adRef,
                throwable = e,
                message = "Failed to deliver beforeOpenAdActivityShow callback."
            )
        }
    }

    private fun notifyDismissOpenAdCalling(mContext: Context) {
        isBeforeActivityShowNotified = false
        if (!FirebaseValue.OPEN_AD_BEFORE_ACTIVITY_SHOW_ENABLE) {
            return
        }
        val listener = openAdControllerListener ?: return
        try {
            listener.dismissOpenAdCalling()
        } catch (e: Exception) {
            AdClickDurationTracker.error(
                mContext,
                adType = "APP_OPEN",
                stage = "DISMISS_OPEN_AD",
                placement = adRef,
                throwable = e,
                message = "Failed to deliver dismissOpenAdCalling callback."
            )
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