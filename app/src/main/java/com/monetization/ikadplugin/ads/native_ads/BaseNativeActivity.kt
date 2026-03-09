package com.monetization.ikadplugin.ads.native_ads

import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.ads.nativead.NativeAd
import com.monetization.ikadplugin.ads.FirebaseValue.ALL_ADS_OFF_ENABLE
import com.monetization.ikadplugin.ads.NativeShimmerEffect.addShimmerLayout
import com.monetization.ikadplugin.ads.collapse.BaseCollapsableBannerActivity
import com.monetization.ikadplugin.pref.AdSharedPreference
import org.koin.android.ext.android.inject

abstract class BaseNativeActivity : BaseCollapsableBannerActivity() {
    private var largeNativeAd: Any? = null
    private var exitNativeAd: Any? = null
    private var isAdEnabled: Boolean = false
    private var adIdNativeReference = ""
    private var adIdBannerReference = ""
    private var loadNewAd: Boolean = false
    private var isAdLoadCalled: Boolean = false
    private var isRequesting: Boolean = false
    private var adFrame: LinearLayout? = null
    private var adViewType: Int = 1
    private var adCallingType: Int = 0
    val nativeAdController: NativeAdController by inject()
//    val prefHelper: AdSharedPreference by inject()
    override fun onPause() {
        nativeAdController.setNativeControllerListener(null)
        super.onPause()
    }

    fun initNativeAdData(
        adCallingType: Int,
        adViewType: Int,
        adIdBannerReference: String,
        adIdNativeReference: String,
        isAdEnabled: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = false
    ) {
        this.adCallingType = adCallingType
        this.adViewType = adViewType
        this.adIdBannerReference = adIdBannerReference
        this.adIdNativeReference = adIdNativeReference
        this.isAdEnabled = isAdEnabled
        this.loadNewAd = loadNewAd
        this.adFrame = adFrame
        isAdLoadCalled = true
        loadSingleNativeAd()
    }


    fun destroyNativeAd() {
        try {
            isAdLoadCalled = false
            if (largeNativeAd != null) {
                destroyAd(largeNativeAd!!)
                largeNativeAd = null
            }
            destroyExitNativeAd()
        } catch (_: Exception) {
        }
    }

    fun destroyExitNativeAd() {
        try {
            isAdLoadCalled = false
            if (exitNativeAd != null) {
                destroyAd(exitNativeAd!!)
                exitNativeAd = null
            }
        } catch (_: Exception) {
        }
    }

    private fun destroyAd(largeNativeAd: Any) {
        try {
            if (largeNativeAd is NativeAd) {
                largeNativeAd.destroy()
            }

        } catch (_: Exception) {
        }
    }

    private fun hideAdFrame() {
        try {
            adFrame?.let {
                it.visibility = View.GONE
                it.removeAllViews()
            }
        } catch (_: Exception) {
        }
    }

    fun loadSingleNativeAd() {
        try {
            if (isAdLoadCalled) {
                if (ALL_ADS_OFF_ENABLE || adFrame == null || !isAdEnabled || prefHelper.isAppPurchased) {
                    hideAdFrame()
                } else {
                    adFrame?.let {
                        if (largeNativeAd == null) {
                            if (!isRequesting) {
                                isRequesting = true
                                addShimmerLayout(
                                    it, adViewType, this
                                )
                                nativeAdController.setNativeControllerListener(object :
                                    AdControllerListener {
                                    override fun onPopulateAd(any: Any) {

                                    }

                                    override fun onAdImpression() {
                                        isRequesting = false
                                    }

                                    override fun onAdLoaded() {
                                        isRequesting = false
                                        if (isFinishing || isDestroyed || isChangingConfigurations) {
                                            return
                                        }
                                        if (largeNativeAd == null) {
                                            loadSingleNativeAd()
                                        }
                                    }

                                    override fun onAdFailed() {
                                        isRequesting = false
                                        if (isFinishing || isDestroyed || isChangingConfigurations) {
                                            return
                                        }
                                        hideAdFrame()
                                    }

                                    override fun resetRequesting() {
                                        isRequesting = false
                                    }
                                })
                                nativeAdController.populateNativeAd(

                                    adViewType,
                                    adIdNativeReference,
                                    this,
                                    isAdEnabled,
                                    it,
                                    loadNewAd
                                ) { ad ->
                                    isRequesting = false
                                    if (!isFinishing && !isDestroyed && !isChangingConfigurations) {
                                        nativeAdController.setNativeControllerListener(null)
                                        largeNativeAd = ad
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun loadExitNativeAd(
        appPurchased: Boolean,
        adViewTypeExit: Int,
        adIdBannerReference: String,
        adIdNativeReference: String,
        enable: Boolean,
        adFrame: LinearLayout
    ) {
        if (ALL_ADS_OFF_ENABLE || !enable || appPurchased || !internetController.isInternetConnected) {
            adFrame.visibility = View.GONE
            adFrame.removeAllViews()
        } else {
            if (exitNativeAd == null) {
                addShimmerLayout(
                    adFrame, adViewTypeExit, this
                )
                nativeAdController.setNativeControllerListener(object : AdControllerListener {
                    override fun onPopulateAd(any: Any) {

                    }

                    override fun onAdImpression() {
                        isRequesting = false
                    }

                    override fun onAdLoaded() {
                        if (isFinishing || isDestroyed || isChangingConfigurations) {
                            return
                        }
                        if (exitNativeAd == null) {
                            loadExitNativeAd(
                                appPurchased,
                                adViewTypeExit,
                                adIdBannerReference,
                                adIdNativeReference,
                                enable,
                                adFrame
                            )
                        }
                    }

                    override fun onAdFailed() {
                        if (isFinishing || isDestroyed || isChangingConfigurations) {
                            return
                        }
                        adFrame.visibility = View.GONE
                        adFrame.removeAllViews()
                    }

                    override fun resetRequesting() {
                    }
                })
                nativeAdController.populateNativeAd(
                    adViewTypeExit, adIdNativeReference, this, enable, adFrame, false

                ) { ad ->
                    if (!isFinishing && !isDestroyed && !isChangingConfigurations) {
                        nativeAdController.setNativeControllerListener(null)
                        exitNativeAd = ad
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSingleNativeAd()
    }

    override fun onDestroy() {
        try {
            destroyNativeAd()
            super.onDestroy()
        } catch (_: Exception) {
        }
    }
}