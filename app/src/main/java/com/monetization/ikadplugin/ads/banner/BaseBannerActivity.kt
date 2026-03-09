package com.monetization.ikadplugin.ads.banner


import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdView
import com.monetization.ikadplugin.ads.FirebaseValue.ALL_ADS_OFF_ENABLE
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.ads.native_ads.AdControllerListener
import com.monetization.ikadplugin.pref.AdSharedPreference
import org.koin.android.ext.android.inject

abstract class BaseBannerActivity : AppCompatActivity() {
    private var bannerAd: AdView? = null
    private var isAdEnabled: Boolean = false
    private var loadNewAd: Boolean = false
    private var adFrame: LinearLayout? = null
    private var adRef: String = ""

    private var isAdLoadCalled: Boolean = false
    private var isRequesting: Boolean = false
    private var isRectangleBanner: Boolean = false
    val bannerAdController: BannerAdController by inject()
    val internetController: InternetController by inject()
    val prefHelper: AdSharedPreference by inject()


    fun initBannerData(
        adRef: String,
        appPurchased: Boolean,
        isAdEnabled: Boolean,
        adFrame: LinearLayout,
        loadNewAd: Boolean = false,
        isRectangleBanner: Boolean = false
    ) {
        try {
            adFrame.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } catch (_: Exception) {
        }
        this.adRef = adRef
        this.isAdEnabled = isAdEnabled
        this.isRectangleBanner = isRectangleBanner
        this.loadNewAd = loadNewAd
        this.adFrame = adFrame
        isAdLoadCalled = true
        loadSingleBannerAd()
    }

    fun destroyBannerAd() {
        try {
            bannerAd?.destroy()
            bannerAd = null
        } catch (_: Exception) {
        }
    }

    private fun loadSingleBannerAd() {
        if (isAdLoadCalled) {
            if (ALL_ADS_OFF_ENABLE || adFrame == null || !isAdEnabled || prefHelper.isAppPurchased|| !internetController.isInternetConnected) {
                adFrame?.let {
                    it.visibility = View.GONE
                    it.removeAllViews()
                }
            } else {
                adFrame?.let { it ->
                    if (bannerAd == null) {
                        if (!isRequesting) {
                            isRequesting = true
                            bannerAdController.addShimmerLayout(
                                isRectangleBanner, it, this
                            )
                            bannerAdController.setAdControllerListener(object :
                                AdControllerListener {
                                override fun onAdLoaded() {
                                    isRequesting = false
                                    if (isFinishing || isDestroyed || isChangingConfigurations) {
                                        return
                                    }
                                    if (bannerAd == null) {
                                        loadSingleBannerAd()
                                    }
                                }

                                override fun onAdImpression() {

                                }

                                override fun onAdFailed() {
                                    isRequesting = false
                                    if (isFinishing || isDestroyed || isChangingConfigurations) {
                                        return
                                    }
                                    adFrame?.let {
                                        it.visibility = View.GONE
                                        it.removeAllViews()
                                    }
                                }

                                override fun onPopulateAd(any: Any) {
                                    isRequesting = false
                                    if (isFinishing || isDestroyed || isChangingConfigurations) {
                                        return
                                    }
                                    bannerAd = any as AdView
                                    bannerAdController.setAdControllerListener(null)
                                }

                                override fun resetRequesting() {
                                    isRequesting = false
                                }
                            })
                            bannerAdController.populateBannerAd(
                                isRectangleBanner,
                                adRef,
                                this,
                                isAdEnabled,
                                it,
                                loadNewAd
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSingleBannerAd()
        bannerAd?.resume()
    }

    override fun onPause() {
        bannerAd?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        destroyBannerAd()
        super.onDestroy()
    }
}