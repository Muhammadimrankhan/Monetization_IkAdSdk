package com.monetization.ikadplugin.ads.native_ads

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads.NativeViewPopulate
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.ads.NativeShimmerEffect.addShimmerLayout
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.ads_duration_tracker.AdType

class AdmobNativeAd {

    companion object {
        @Volatile
        private var INSTANCE: AdmobNativeAd? = null

        fun getInstance(
        ): AdmobNativeAd {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobNativeAd().also { INSTANCE = it }
            }
        }
    }

    private var canRequestAd = true
    private var largeAndSmallNativeAd: NativeAd? = null

    private fun clearNativeAd() {
        largeAndSmallNativeAd = null
    }

     fun onDestroyNativeAd(context: Context) {
        try {
            largeAndSmallNativeAd?.destroy()
        } catch (e: Exception) {
            AdClickDurationTracker.warn(context,
                adType = "NATIVE",
                stage = "DESTROY",
                placement = "unknown",
                message = "Failed destroying native ad: ${e.message}"
            )
        } finally {
            largeAndSmallNativeAd = null
        }
    }


    fun hasLargeAdOrLoading(): Boolean {
        return (largeAndSmallNativeAd != null || !canRequestAd)
    }

    fun hasLargeAd(): Boolean {
        return largeAndSmallNativeAd != null
    }

    fun preLoadNativeAd(
        adIdNativeReference: String, context: Context, enable: Boolean
    ) {
        try {
            if (!FirebaseValue.ALL_ADS_OFF_ENABLE && GoogleMobileAdsConsentManager.getInstance(
                    context
                ).canRequestAds && enable && !AdSharedPreference.getInstance(context).isAppPurchased && InternetController.getInstance(
                    context
                ).isInternetConnected
            ) {
                if (largeAndSmallNativeAd == null) {
                    if (!canRequestAd) {
                        return
                    }
                    canRequestAd = false
                    AdClickDurationTracker.adRequestCalling(context,
                        adType = AdType.NATIVE,
                        adIdReferenceName = adIdNativeReference
                    )
                    val adId = FetchConfig.getNativeId(adIdNativeReference)
                    val builder = AdLoader.Builder(
                        context, adId
                    )
                    builder.forNativeAd { newNativeAd: NativeAd ->
                        canRequestAd = true
                        largeAndSmallNativeAd = newNativeAd
                        AdClickDurationTracker.adRequestMatch(context,
                            adType = AdType.NATIVE,
                            adIdReferenceName = adIdNativeReference
                        )
                    }
                    builder.withNativeAdOptions(
                        NativeAdOptions.Builder().setVideoOptions(
                            VideoOptions.Builder().setStartMuted(true).build()
                        ).build()
                    )

                    val adLoader = builder.withAdListener(object : AdListener() {
                        override fun onAdClicked() {
                            super.onAdClicked()

                            AdClickDurationTracker.startTracking(context,
                                adType = AdType.NATIVE,
                                adIdReferenceName = adIdNativeReference
                            )
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            AdClickDurationTracker.adRequestFail(context,
                                adType = AdType.NATIVE,
                                adIdReferenceName = adIdNativeReference
                            )
                            canRequestAd = true
                            clearNativeAd()
                        }
                    }).build()
                    adLoader.loadAd(AdRequest.Builder().build())
                }
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(context,
                adType = "NATIVE",
                stage = "PRELOAD",
                placement = adIdNativeReference,
                throwable = e,
                message = "Native preload flow failed."
            )
        }
    }

//    populateCallback: (Any) -> Unit
//   populateCallback.invoke(it)

    fun populateNativeAd(
        adViewType: Int,
        adIdNativeReference: String,
        context: Context,
        enable: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = false,
        nativeCtaColorAdPosition: Int = -1
    ) {
        if (!FirebaseValue.ALL_ADS_OFF_ENABLE &&
            GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
            enable &&
            !AdSharedPreference.getInstance(context).isAppPurchased &&
            InternetController.getInstance(context).isInternetConnected
        ) {
            if (largeAndSmallNativeAd != null) {
                largeAndSmallNativeAd?.let {
                    try {
                        AdClickDurationTracker.adShow(context,
                            adType = AdType.NATIVE,
                            adIdReferenceName = adIdNativeReference
                        )
                        NativeViewPopulate.addLargeNativeView(
                            context = context,
                            adFrame = adFrame,
                            ad = it,
                            adViewType = adViewType,
                            nativeCtaColorAdPosition = nativeCtaColorAdPosition
                        )
                        clearNativeAd()
                        if (loadNewAd) {
                            preLoadNativeAd(
                                adIdNativeReference,
                                context,
                                enable
                            )
                        }
                    } catch (e: Exception) {
                        AdClickDurationTracker.warn(context,
                            adType = "NATIVE",
                            stage = "POPULATE",
                            placement = adIdNativeReference,
                            message = "Native populate failed: ${e.message}"
                        )
                        adFrame.removeAllViews()
                        adFrame.visibility = View.GONE
                    }
                }
            } else {
                loadAndShowNativeAd(
                    adIdNativeReference = adIdNativeReference,
                    adLayout = adFrame,
                    context = context,
                    enable = enable,
                    adViewType = adViewType,
                    loadNewAd = loadNewAd,
                    nativeCtaColorAdPosition = nativeCtaColorAdPosition
                )
            }

        }
    }


    fun loadAndShowNativeAd(
        adIdNativeReference: String,
        adLayout: LinearLayout,
        context: Context,
        enable: Boolean,
        adViewType: Int,
        loadNewAd: Boolean = false,
        nativeCtaColorAdPosition: Int = -1
    ) {
        try {
            if (!FirebaseValue.ALL_ADS_OFF_ENABLE && GoogleMobileAdsConsentManager.getInstance(
                    context
                ).canRequestAds && enable && !AdSharedPreference.getInstance(context).isAppPurchased && InternetController.getInstance(
                    context
                ).isInternetConnected
            ) {
                if (largeAndSmallNativeAd == null) {
                    if (!canRequestAd) {
                        return
                    }
                    addShimmerLayout(
                        adLayout, adViewType, context
                    )
                    canRequestAd = false
                    AdClickDurationTracker.adRequestCalling(context,
                        adType = AdType.NATIVE,
                        adIdReferenceName = adIdNativeReference
                    )
                    val adId = FetchConfig.getNativeId(adIdNativeReference)
                    val builder = AdLoader.Builder(
                        context, adId
                    )
                    builder.forNativeAd { newNativeAd: NativeAd ->
                        canRequestAd = true
                        largeAndSmallNativeAd = newNativeAd
                        AdClickDurationTracker.adRequestMatch(context,
                            adType = AdType.NATIVE,
                            adIdReferenceName = adIdNativeReference
                        )
                        largeAndSmallNativeAd?.let {
                            AdClickDurationTracker.adShow(context,
                                adType = AdType.NATIVE,
                                adIdReferenceName = adIdNativeReference
                            )
                            NativeViewPopulate.addLargeNativeView(
                                context = context,
                                adFrame = adLayout,
                                ad = it,
                                adViewType = adViewType,
                                nativeCtaColorAdPosition = nativeCtaColorAdPosition
                            )
                            clearNativeAd()
                            if (loadNewAd) {
                                preLoadNativeAd(
                                    adIdNativeReference,
                                    context,
                                    enable
                                )
                            }
                        }
                    }
                    builder.withNativeAdOptions(
                        NativeAdOptions.Builder().setVideoOptions(
                            VideoOptions.Builder().setStartMuted(true).build()
                        ).build()
                    )

                    val adLoader = builder.withAdListener(object : AdListener() {
                        override fun onAdClicked() {
                            super.onAdClicked()

                            AdClickDurationTracker.startTracking(context,
                                adType = AdType.NATIVE,
                                adIdReferenceName = adIdNativeReference
                            )
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            super.onAdFailedToLoad(loadAdError)
                            AdClickDurationTracker.adRequestFail(context,
                                adType = AdType.NATIVE,
                                adIdReferenceName = adIdNativeReference
                            )

                            canRequestAd = true
                            clearNativeAd()
                            adLayout.removeAllViews()
                            adLayout.visibility = View.GONE
                        }
                    }).build()
                    adLoader.loadAd(AdRequest.Builder().build())
                } else {
                    largeAndSmallNativeAd?.let {
                        addShimmerLayout(
                            adLayout, adViewType, context
                        )
                        AdClickDurationTracker.adShow(context,
                            adType = AdType.NATIVE,
                            adIdReferenceName = adIdNativeReference
                        )
                        NativeViewPopulate.addLargeNativeView(
                            context = context,
                            adFrame = adLayout,
                            ad = it,
                            adViewType = adViewType,
                            nativeCtaColorAdPosition = nativeCtaColorAdPosition
                        )
                        clearNativeAd()
                        if (loadNewAd) {
                            preLoadNativeAd(
                                adIdNativeReference,
                                context,
                                enable
                            )
                        }
                    }
                }
            } else {
                adLayout.removeAllViews()
                adLayout.visibility = View.GONE
            }
        } catch (e: Exception) {
            AdClickDurationTracker.error(context,
                adType = "NATIVE",
                stage = "LOAD_AND_SHOW",
                placement = adIdNativeReference,
                throwable = e,
                message = "Native load-and-show flow failed."
            )
            adLayout.removeAllViews()
            adLayout.visibility = View.GONE
        }
    }


    val shownFragmentAds = HashSet<String>()

    fun clearFragmentAdState(fragmentKey: String) {
        shownFragmentAds.remove(fragmentKey)
    }

    fun clearAllFragmentAdStates() {
        shownFragmentAds.clear()
    }

    fun populateNativeAdForFragment(
        adViewType: Int,
        adIdNativeReference: String,
        context: Context,
        enable: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = false,
        nativeCtaColorAdPosition: Int = -1,
        isFragmentCall: Boolean = false,
        fragmentKey: String? = null
    ) {
        try {
            if (!FirebaseValue.ALL_ADS_OFF_ENABLE &&
                GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
                enable &&
                !AdSharedPreference.getInstance(context).isAppPurchased &&
                InternetController.getInstance(context).isInternetConnected
            ) {

                // Sirf fragment case me check lagana hai
                if (isFragmentCall && !fragmentKey.isNullOrEmpty() && shownFragmentAds.contains(
                        fragmentKey
                    )
                ) {
                    return
                }
                if (largeAndSmallNativeAd != null) {
                    largeAndSmallNativeAd?.let { ad ->
                        try {
                            NativeViewPopulate.addLargeNativeView(
                                context = context,
                                adFrame = adFrame,
                                ad = ad,
                                adViewType = adViewType,
                                nativeCtaColorAdPosition = nativeCtaColorAdPosition
                            )
                            if (isFragmentCall && !fragmentKey.isNullOrEmpty()) {
                                shownFragmentAds.add(fragmentKey)
                            }
                            clearNativeAd()
                            if (loadNewAd) {
                                preLoadNativeAd(
                                    adIdNativeReference,
                                    context,
                                    enable
                                )
                            }

                        } catch (_: Exception) {
                        }
                    }

                } else {
                    loadAndShowNativeAdForFragment(
                        adIdNativeReference = adIdNativeReference,
                        adLayout = adFrame,
                        context = context,
                        enable = enable,
                        adViewType = adViewType,
                        loadNewAd = loadNewAd,
                        nativeCtaColorAdPosition = nativeCtaColorAdPosition,
                        isFragmentCall = isFragmentCall,
                        fragmentKey = if (isFragmentCall) fragmentKey else null
                    )
                }
            } else {
                adFrame.removeAllViews()
                adFrame.visibility = View.GONE
            }
        } catch (e: Exception) {
            adFrame.removeAllViews()
            adFrame.visibility = View.GONE
        }
    }

    fun loadAndShowNativeAdForFragment(
        adIdNativeReference: String,
        adLayout: LinearLayout,
        context: Context,
        enable: Boolean,
        adViewType: Int,
        loadNewAd: Boolean = false,
        nativeCtaColorAdPosition: Int = -1,
        isFragmentCall: Boolean = false,
        fragmentKey: String? = null
    ) {
        try {
            if (!FirebaseValue.ALL_ADS_OFF_ENABLE &&
                GoogleMobileAdsConsentManager.getInstance(context).canRequestAds &&
                enable &&
                !AdSharedPreference.getInstance(context).isAppPurchased &&
                InternetController.getInstance(context).isInternetConnected
            ) {
                // Sirf fragment case me block
                if (isFragmentCall && !fragmentKey.isNullOrEmpty() && shownFragmentAds.contains(
                        fragmentKey
                    )
                ) {
                    return
                }

                if (!canRequestAd) {
                    return
                }
                addShimmerLayout(adLayout, adViewType, context)
                canRequestAd = false
                AdClickDurationTracker.adRequestCalling(context,
                    adType = AdType.NATIVE,
                    adIdReferenceName = adIdNativeReference
                )
                val adId = FetchConfig.getNativeId(adIdNativeReference)
                val builder = AdLoader.Builder(context, adId)

                builder.forNativeAd { newNativeAd: NativeAd ->
                    canRequestAd = true
                    largeAndSmallNativeAd = newNativeAd
                    AdClickDurationTracker.adRequestMatch(context,
                        adType = AdType.NATIVE,
                        adIdReferenceName = adIdNativeReference
                    )
                    largeAndSmallNativeAd?.let {
                        AdClickDurationTracker.adShow(context,
                            adType = AdType.NATIVE,
                            adIdReferenceName = adIdNativeReference
                        )
                        NativeViewPopulate.addLargeNativeView(
                            context = context,
                            adFrame = adLayout,
                            ad = it,
                            adViewType = adViewType,
                            nativeCtaColorAdPosition = nativeCtaColorAdPosition
                        )
                        if (isFragmentCall && !fragmentKey.isNullOrEmpty()) {
                            shownFragmentAds.add(fragmentKey)
                        }
                        clearNativeAd()

                        if (loadNewAd) {
                            preLoadNativeAd(
                                adIdNativeReference,
                                context,
                                enable
                            )
                        }
                    }
                }

                builder.withNativeAdOptions(
                    NativeAdOptions.Builder().setVideoOptions(
                        VideoOptions.Builder().setStartMuted(true).build()
                    ).build()
                )

                val adLoader = builder.withAdListener(object : AdListener() {
                    override fun onAdClicked() {
                        super.onAdClicked()

                        AdClickDurationTracker.startTracking(context,
                            adType = AdType.NATIVE,
                            adIdReferenceName = adIdNativeReference
                        )
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        AdClickDurationTracker.adRequestFail(context,
                            adType = AdType.NATIVE,
                            adIdReferenceName = adIdNativeReference
                        )
                        canRequestAd = true
                        clearNativeAd()
                        adLayout.removeAllViews()
                        adLayout.visibility = View.GONE
                    }
                }).build()

                adLoader.loadAd(AdRequest.Builder().build())

            } else {
                adLayout.removeAllViews()
                adLayout.visibility = View.GONE
            }
        } catch (e: Exception) {
            adLayout.removeAllViews()
            adLayout.visibility = View.GONE
        }
    }

}
