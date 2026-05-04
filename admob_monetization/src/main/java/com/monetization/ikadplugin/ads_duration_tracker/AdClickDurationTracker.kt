package com.monetization.ikadplugin.ads_duration_tracker

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.google.firebase.analytics.FirebaseAnalytics

object AdClickDurationTracker : Application.ActivityLifecycleCallbacks {

    private const val EVENT_AD_CLICK_RETURN_DURATION = "ad_click_return_duration"
    private const val EVENT_AD_CLICK = "ad_click"
    private const val EVENT_AD_REQUEST_FAIL = "ad_request_fail"
    private const val EVENT_AD_REQUEST_MATCH = "ad_request_match"
    private const val EVENT_AD_SHOW = "ad_show"

    private const val PARAM_AD_TYPE = "ad_type"
    private const val PARAM_AD_ID_REF = "ad_id_ref"
    private const val PARAM_DURATION_MILLIS = "duration_millis"
    private const val PARAM_DURATION_SECONDS = "duration_seconds"

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
        adType: AdType,
        adIdReferenceName: String = ""
    ) {

        clickStartTimeMillis = SystemClock.elapsedRealtime()
        waitingForAppResume = true

        clickedAdType = adType.firebaseName
        clickedAdIdReferenceName = adIdReferenceName
        firebaseAnalytics?.logEvent(
            EVENT_AD_CLICK,
            Bundle().apply {
                putString(PARAM_AD_TYPE, clickedAdType)
                putString(PARAM_AD_ID_REF, clickedAdIdReferenceName)
            }
        )
    }

    private fun logDurationIfNeeded() {
        if (!waitingForAppResume || clickStartTimeMillis <= 0L) return

        val durationMillis = SystemClock.elapsedRealtime() - clickStartTimeMillis

        if (durationMillis < MIN_VALID_DURATION_MILLIS) {
            reset()
            return
        }

        val durationSeconds = durationMillis / 1000

        firebaseAnalytics?.logEvent(
            EVENT_AD_CLICK_RETURN_DURATION,
            Bundle().apply {
                putString(PARAM_AD_TYPE, clickedAdType)
                putString(PARAM_AD_ID_REF, clickedAdIdReferenceName)
                putLong(PARAM_DURATION_MILLIS, durationMillis)
                putLong(PARAM_DURATION_SECONDS, durationSeconds)
            }
        )

        reset()
    }

    fun reset() {
        clickStartTimeMillis = 0L
        waitingForAppResume = false
        clickedAdType = ""
        clickedAdIdReferenceName = ""
    }

    fun adRequestFail(
        adType: AdType,
        adIdReferenceName: String = ""
    ) {
        reset()
        firebaseAnalytics?.logEvent(
            EVENT_AD_REQUEST_FAIL,
            Bundle().apply {
                putString(PARAM_AD_TYPE, adType.firebaseName)
                putString(PARAM_AD_ID_REF, adIdReferenceName)
            }
        )
    }
    fun adRequestMatch(
        adType: AdType,
        adIdReferenceName: String = ""
    ) {
        firebaseAnalytics?.logEvent(
            EVENT_AD_REQUEST_MATCH,
            Bundle().apply {
                putString(PARAM_AD_TYPE, adType.firebaseName)
                putString(PARAM_AD_ID_REF, adIdReferenceName)
            }
        )
    }

    fun adShow(
        adType: AdType,
        adIdReferenceName: String = ""
    ) {
        firebaseAnalytics?.logEvent(
            EVENT_AD_SHOW,
            Bundle().apply {
                putString(PARAM_AD_TYPE, adType.firebaseName)
                putString(PARAM_AD_ID_REF, adIdReferenceName)
            }
        )
    }

    override fun onActivityResumed(activity: Activity) {
        logDurationIfNeeded()
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