package com.monetization.ikadplugin.ads_duration_tracker

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.monetization.ikadplugin.ads.AdKeys
import com.monetization.ikadplugin.subscription.SubscriptionConstant.isDebug

object AdClickDurationTracker : Application.ActivityLifecycleCallbacks {

    private const val EVENT_AD_CLICK_RETURN_DURATION = "ad_click_return_duration"
    private const val EVENT_AD_CLICK = "ad_click"
    private const val EVENT_AD_REQUEST_FAIL = "ad_request_fail"
    private const val EVENT_AD_REQUEST_CALLING = "ad_request_calling"
    private const val EVENT_AD_REQUEST_DURATION = "ad_request_duration_end"
    private const val EVENT_AD_REQUEST_WARN = "ad_request_warning"
    private const val EVENT_AD_REQUEST_ERROR = "ad_request_error"
    private const val EVENT_AD_REQUEST_MATCH = "ad_request_Loaded"
    private const val EVENT_AD_USER_EARN_REWARDED = "user_earn_rewarded_value"
    private const val EVENT_AD_SHOW = "ad_show"
    private const val EVENT_AD_IMPRESSION = "ad_impression_ik"
    private const val EVENT_AD_PAID = "ad_impression_revenue"
    private const val EVENT_AD_SHOW_FAIL = "ad_show_fail"
    private const val TAG = "IkAdSdk"

    private const val PARAM_AD_TYPE = "ad_type"
    private const val PARAM_AD_ID_REF = "ad_id_ref"
    private const val PARAM_DURATION_MILLIS = "duration_millis"
    private const val PARAM_DURATION_SECONDS = "duration_seconds"
    private const val PARAM_ERROR_CODE = "error_code"
    private const val PARAM_ERROR_MESSAGE = "error_message"

    /**
     * Optional guard to avoid logging fake/instant resumes.
     * Example: banner/native click may not always leave the app.
     */
    private const val MIN_VALID_DURATION_MILLIS = 500L

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var isRegistered = false

    private var clickStartTimeMillis: Long = 0L
    private var waitingForAppResume = false

    private var clickedAdType: String = ""
    private var clickedAdIdReferenceName: String = ""


    fun init(application: Application) {
        if (isRegistered) return
        firebaseAnalytics = FirebaseAnalytics.getInstance(application)
        application.registerActivityLifecycleCallbacks(this)

        isRegistered = true
    }

    fun startTracking(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = ""
    ) {

        clickStartTimeMillis = SystemClock.elapsedRealtime()
        waitingForAppResume = true
        if (adType == AdType.BANNER || adType == AdType.NATIVE) {
            AdKeys.skipNextOpenAd = true
        }

        clickedAdType = adType.firebaseName
        clickedAdIdReferenceName = adIdReferenceName

        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_CLICK\nAd Type: ${adType.firebaseName}\nAd Ref: $adIdReferenceName",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_CLICK,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, clickedAdType)
                    putString(PARAM_AD_ID_REF, clickedAdIdReferenceName)
                }
            )
        }
    }

    private fun logDurationIfNeeded(context: Context) {
        if (!waitingForAppResume || clickStartTimeMillis <= 0L) return

        val durationMillis = SystemClock.elapsedRealtime() - clickStartTimeMillis

        if (durationMillis < MIN_VALID_DURATION_MILLIS) {
            reset()
            return
        }

        val durationSeconds = durationMillis / 1000
        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_CLICK_RETURN_DURATION" +
                        "\nAd Type: ${clickedAdType}" +
                        "\nAd Ref: $clickedAdIdReferenceName" +
                        "\ndurationMillis: ${durationMillis}" +
                        "\ndurationSeconds: ${durationSeconds}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_CLICK_RETURN_DURATION,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, clickedAdType)
                    putString(PARAM_AD_ID_REF, clickedAdIdReferenceName)
                    putLong(PARAM_DURATION_MILLIS, durationMillis)
                    putLong(PARAM_DURATION_SECONDS, durationSeconds)
                }
            )
        }
        reset()
    }

    fun reset() {
        clickStartTimeMillis = 0L
        waitingForAppResume = false
        clickedAdType = ""
        clickedAdIdReferenceName = ""
    }

    fun adRequestFail(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = "",
        errorCode: Int? = null,
        errorMessage: String? = null
    ) {
        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_REQUEST_FAIL\nAd Type: ${adType.firebaseName}\nAd Ref: $adIdReferenceName\nCode: $errorCode ${errorMessage.orEmpty()}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_REQUEST_FAIL,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                    errorCode?.let { putInt(PARAM_ERROR_CODE, it) }
                    errorMessage?.let { putString(PARAM_ERROR_MESSAGE, it.take(100)) }
                }
            )
        }
    }

    /** A loaded full-screen ad that AdMob could not show (expired, already shown, no window...). */
    fun adShowFail(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = "",
        errorCode: Int? = null,
        errorMessage: String? = null
    ) {
        if (isDebug) {
            Log.d(TAG, "$EVENT_AD_SHOW_FAIL ${adType.firebaseName} $adIdReferenceName $errorCode $errorMessage")
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_SHOW_FAIL,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                    errorCode?.let { putInt(PARAM_ERROR_CODE, it) }
                    errorMessage?.let { putString(PARAM_ERROR_MESSAGE, it.take(100)) }
                }
            )
        }
    }

    /**
     * AdMob counted an impression. Show rate = this event ÷ [adRequestMatch]; [adShow] only means
     * the SDK handed the ad over or called show().
     */
    fun adImpression(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = ""
    ) {
        if (isDebug) {
            Log.d(TAG, "$EVENT_AD_IMPRESSION ${adType.firebaseName} $adIdReferenceName")
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_IMPRESSION,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                }
            )
        }
    }

    /** Impression-level revenue reported by `setOnPaidEventListener`. */
    fun adPaidEvent(
        context: Context,
        adType: AdType,
        adIdReferenceName: String,
        adValue: AdValue,
        responseInfo: ResponseInfo?
    ) {
        val value = adValue.valueMicros / 1_000_000.0
        val adSource = responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty()
        if (isDebug) {
            Log.d(TAG, "$EVENT_AD_PAID ${adType.firebaseName} $adIdReferenceName $value ${adValue.currencyCode} $adSource")
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_PAID,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                    putDouble(FirebaseAnalytics.Param.VALUE, value)
                    putString(FirebaseAnalytics.Param.CURRENCY, adValue.currencyCode)
                    putInt("precision", adValue.precisionType)
                    putString("ad_source", adSource)
                }
            )
        }
    }

    fun adRequestCalling(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = ""
    ) {
        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_REQUEST_CALLING\nAd Type: ${adType.firebaseName}\nAd Ref: $adIdReferenceName",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_REQUEST_CALLING,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                }
            )
        }
    }

   fun adRequestDurationEnd(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = ""
    ) {

        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_REQUEST_DURATION\nAd Type: ${adType.firebaseName}\nAd Ref: $adIdReferenceName",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_REQUEST_DURATION,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                }
            )
        }
    }

    fun adRequestMatch(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = ""
    ) {
        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_REQUEST_MATCH\nAd Type: ${adType.firebaseName}\nAd Ref: $adIdReferenceName",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_REQUEST_MATCH,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                }
            )
        }
    }

    fun adShow(
        context: Context,
        adType: AdType,
        adIdReferenceName: String = ""
    ) {

        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_SHOW\nAd Type: ${adType.firebaseName}\nAd Ref: $adIdReferenceName",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_SHOW,
                Bundle().apply {
                    putString(PARAM_AD_TYPE, adType.firebaseName)
                    putString(PARAM_AD_ID_REF, adIdReferenceName)
                }
            )
        }
    }

    fun warn(
        context: Context,
        adType: String,
        stage: String,
        placement: String,
        message: String
    ) {

        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_REQUEST_WARN\n[$adType][$stage][$placement] $message",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_REQUEST_WARN,
                Bundle().apply {
                    putString("adType", adType)
                    putString("stage", stage)
                    putString("placement", placement)
                    putString("message", message)
                }
            )
        }
    }

    fun error(
        context: Context,
        adType: String,
        stage: String,
        placement: String,
        throwable: Throwable? = null,
        message: String
    ) {

        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_REQUEST_ERROR\n[$adType][$stage][$placement] $message ${throwable?.message}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_REQUEST_ERROR,
                Bundle().apply {
                    putString("adType", adType)
                    putString("stage", stage)
                    putString("placement", placement)
                    putString("message", message)
                    putString("throwable", throwable?.message)
                }
            )
        }
    }

    fun userEarnedRewardedValue(
        context: Context,
        rewardType: String,
        rewardAmount: Int
    ) {
        if (isDebug) {
            Toast.makeText(
                context,
                "$EVENT_AD_USER_EARN_REWARDED\nrewardType: ${rewardType}\nrewardAmount: $rewardAmount",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            firebaseAnalytics?.logEvent(
                EVENT_AD_USER_EARN_REWARDED,
                Bundle().apply {
                    putString("rewardType", rewardType)
                    putInt("rewardAmount", rewardAmount)
                }
            )
        }
    }

    override fun onActivityResumed(activity: Activity) {
        logDurationIfNeeded(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

enum class AdType(val firebaseName: String) {
    INTERSTITIAL("interstitial"),
    APP_OPEN("app_open"),
    BANNER("banner"),
    NATIVE("native"),
    REWARDED("rewarded"),
    REWARDED_INTERSTITIAL("rewarded_interstitial")
}