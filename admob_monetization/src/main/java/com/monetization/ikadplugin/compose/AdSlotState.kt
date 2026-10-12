package com.monetization.ikadplugin.compose

import android.content.Context
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference

/**
 * What an inline (native / banner) ad slot is currently doing.
 *
 * [Hidden] means the slot must render nothing at all: the ad was suppressed (ads off, no consent,
 * placement disabled, user purchased, offline) or it failed to fill. The UI collapses to zero
 * height instead of leaving an empty gap.
 */
sealed interface AdSlotState {
    data object Idle : AdSlotState
    data object Loading : AdSlotState
    data object Loaded : AdSlotState
    data object Hidden : AdSlotState
}

val AdSlotState.isVisible: Boolean
    get() = this is AdSlotState.Loading || this is AdSlotState.Loaded

/**
 * The same conditions the ad classes check internally before requesting an inline ad. Kept here so
 * a ViewModel can decide to hide a slot without calling into the ad classes at all.
 */
internal fun canRequestInlineAd(context: Context, enable: Boolean): Boolean =
    !FirebaseValue.ALL_ADS_OFF_ENABLE &&
        GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
        enable &&
        !AdSharedPreference.getInstance(context).isAppPurchased &&
        InternetController.getInstance(context).isInternetConnected
