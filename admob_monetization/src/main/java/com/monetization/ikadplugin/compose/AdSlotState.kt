package com.monetization.ikadplugin.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
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
 * a ViewModel can decide to hide a slot without touching the ad layout at all.
 */
internal fun canRequestInlineAd(context: Context, enable: Boolean): Boolean =
    !FirebaseValue.ALL_ADS_OFF_ENABLE &&
        GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
        enable &&
        !AdSharedPreference.getInstance(context).isAppPurchased &&
        InternetController.getInstance(context).isInternetConnected

/**
 * The view of type [adViewClass] inside [root], or null while [root] still holds only the shimmer
 * placeholder. Finding one is how the ViewModels learn a load succeeded, without the ad classes
 * needing to expose callbacks.
 */
internal fun findAdView(root: View?, adViewClass: Class<*>): View? {
    if (root == null) return null
    if (adViewClass.isInstance(root)) return root
    if (root !is ViewGroup) return null
    for (i in 0 until root.childCount) {
        findAdView(root.getChildAt(i), adViewClass)?.let { return it }
    }
    return null
}

internal fun containsAdView(root: View?, adViewClass: Class<*>): Boolean =
    findAdView(root, adViewClass) != null
