package com.monetization.ikadplugin.ads_duration_tracker

import android.content.Context
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.ResponseInfo

/**
 * One paid impression reported by AdMob through [OnPaidEventListener].
 *
 * [valueMicros] is in micro units of [currencyCode] (5,000 = 0.005). [precisionType] is one of the
 * [AdValue.PrecisionType] constants.
 */
data class AdRevenueEvent(
    val adType: AdType,
    val placement: String,
    val adUnitId: String,
    val valueMicros: Long,
    val currencyCode: String,
    val precisionType: Int,
    val adSourceName: String
) {
    val value: Double
        get() = valueMicros / 1_000_000.0
}

fun interface AdRevenueListener {
    fun onAdRevenue(event: AdRevenueEvent)
}

/**
 * Impression-level ad revenue for every ad format the SDK shows.
 *
 * Each paid event is logged to Firebase Analytics as `ad_paid_revenue`, and passed to [listener] so
 * the host app can forward it (Adjust, AppsFlyer, its own backend).
 *
 * The standard `ad_impression` event is off by default: when the AdMob app is linked to Firebase,
 * Analytics already logs it automatically, and logging it again would double count revenue. Turn
 * [logStandardAdImpression] on only when the AdMob app is not linked.
 */
object AdRevenueTracker {

    var listener: AdRevenueListener? = null

    var logStandardAdImpression = false

    internal fun paidEventListener(
        context: Context,
        adType: AdType,
        placement: String,
        adUnitId: String,
        responseInfo: () -> ResponseInfo?
    ): OnPaidEventListener {
        val appContext = context.applicationContext
        return OnPaidEventListener { adValue ->
            try {
                val event = AdRevenueEvent(
                    adType = adType,
                    placement = placement,
                    adUnitId = adUnitId,
                    valueMicros = adValue.valueMicros,
                    currencyCode = adValue.currencyCode,
                    precisionType = adValue.precisionType,
                    adSourceName = responseInfo()?.loadedAdapterResponseInfo?.adSourceName ?: ""
                )
                AdClickDurationTracker.adRevenue(appContext, event, logStandardAdImpression)
                listener?.onAdRevenue(event)
            } catch (e: Exception) {
                AdClickDurationTracker.error(
                    appContext,
                    adType = adType.name,
                    stage = "PAID_EVENT",
                    placement = placement,
                    throwable = e,
                    message = "Failed handling paid event."
                )
            }
        }
    }
}
