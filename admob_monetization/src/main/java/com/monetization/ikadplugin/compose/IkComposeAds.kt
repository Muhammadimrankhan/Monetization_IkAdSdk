package com.monetization.ikadplugin.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.monetization.ikadplugin.ads.banner.AdmobBannerAd
import com.monetization.ikadplugin.ads.collapse.AdmobCollapsibleBannerAd
import com.monetization.ikadplugin.ads.interstitial_ads.AdmobInterstitialAd
import com.monetization.ikadplugin.ads.interstitial_ads.InterstitialControllerListener
import com.monetization.ikadplugin.ads.native_ads.AdmobNativeAd
import com.monetization.ikadplugin.ads.rewarded_interstitial.AdmobRewardedInterstitialAd
import com.monetization.ikadplugin.ads.rewarded_interstitial.RewardedInterstitialControllerListener

@Composable
fun IkComposeBannerAd(activity: Activity,
    adReference: String,
    enable: Boolean,
    modifier: Modifier = Modifier,
    isRectangleBanner: Boolean = false,
    loadNewAd: Boolean = false
) {

    AndroidView(modifier = modifier, factory = { context ->
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }, update = { adFrame ->
        activity.let {
            AdmobBannerAd.getInstance().populateBannerAd(
                adReference = adReference,
                context = it,
                enable = enable,
                isRectangleBanner = isRectangleBanner,
                adLayout = adFrame,
                loadNewAd = loadNewAd
            )
        }
    })
}

@Composable
fun IkComposeCollapsibleBannerAd(
    activity: Activity,
    adReference: String,
    enable: Boolean,
    modifier: Modifier = Modifier,
    loadNewAd: Boolean = false
) {

    AndroidView(modifier = modifier, factory = { context ->
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }, update = { adFrame ->
        activity.let {
            AdmobCollapsibleBannerAd.getInstance().populateAd(
                adReference = adReference,
                activity = it,
                enable = enable,
                adFrame = adFrame,
                loadNewAd = loadNewAd
            )
        }
    })
}

@Composable
fun IkComposeNativeAd(
    context: Activity,
    adIdNativeReference: String,
    enable: Boolean,
    modifier: Modifier = Modifier,
    adViewType: Int = 1,
    loadNewAd: Boolean = false,
    nativeCtaColorAdPosition: Int = -1
) {
    AndroidView(modifier = modifier, factory = { viewContext ->
        LinearLayout(viewContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }, update = { adFrame ->
        AdmobNativeAd.getInstance().populateNativeAd(
            adViewType = adViewType,
            adIdNativeReference = adIdNativeReference,
            context = context,
            enable = enable,
            adFrame = adFrame,
            loadNewAd = loadNewAd,
            nativeCtaColorAdPosition = nativeCtaColorAdPosition
        )
    })
}
//
//@Composable
//fun rememberIkComposeInterstitialAdCounter(
//    activity: Activity,
//    adReference: String,
//    enable: Boolean = false,
//    preloadOnStart: Boolean = false
//): IkComposeInterstitialController {
//    val controller = remember(adReference) {
//        IkComposeInterstitialController(activity, adReference)
//    }
//
//    LaunchedEffect(activity, enable, preloadOnStart, adReference) {
//        controller.updateActivity(activity)
////        controller.showInterstitialCounter(enable)
//    }
//
//    DisposableEffect(controller) {
//        onDispose {
//            controller.clear()
//        }
//    }
//
//    return controller
//
//
//}

@Composable
fun rememberIkComposeInterstitialAdAssignContext(
    activity: Activity,
    adReference: String,
    enable: Boolean = false
): IkComposeInterstitialController {
    val controller = remember(adReference) {
        IkComposeInterstitialController(activity, adReference)
    }

    LaunchedEffect(activity, enable, adReference) {
        controller.updateActivity(activity)
//        controller.showInterstitialAdEveryClick(enable)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.clear()
        }
    }

    return controller
}

//@Composable
//fun rememberIkComposeInterstitialAdPreLoad(
//    context: Activity, adReference: String, enable: Boolean = false, preloadOnStart: Boolean = false
//): IkComposeInterstitialController {
//
//    val controller = remember(adReference) {
//        IkComposeInterstitialController(context, adReference)
//    }
//
//    LaunchedEffect(context, enable, preloadOnStart, adReference) {
//        controller.updateActivity(context)
//        if (preloadOnStart) {
//            controller.preload(enable)
//        }
//    }
//
//    DisposableEffect(controller) {
//        onDispose {
//            controller.clear()
//        }
//    }
//
//    return controller
//}

@Stable
class IkComposeInterstitialController internal constructor(
    private var activity: Activity?, private val adReference: String,
) {
    internal fun updateActivity(activity: Activity?) {
        this.activity = activity
    }

    fun preload(enable: Boolean = true) {
        if (!enable) {
            return
        }
        val appCompatActivity = activity as? AppCompatActivity ?: return
        AdmobInterstitialAd.getInstance().preLoadAd(adReference, appCompatActivity)
    }

    fun showInterstitialCounter(
        enable: Boolean = false,
        onAdClosed: () -> Unit = {},
        onAdLoaded: () -> Unit = {},
        onSplashAdViewGone: () -> Unit = {},
        onIapShow: () -> Unit = {}
    ) {
        val currentActivity = activity ?: run {
            onAdClosed()
            return
        }

        AdmobInterstitialAd.getInstance().showInterstitial(
            adIdString = adReference,
            activity = currentActivity,
            enable = enable,
            interstitialControllerListener = object : InterstitialControllerListener {
                override fun onAdClosed() = onAdClosed()
                override fun onAdLoaded() = onAdLoaded()
                override fun onSplashAdViewGone() = onSplashAdViewGone()
                override fun onIapShow() = onIapShow()
            })
    }

    fun showInterstitialClickOrBackCounter(
        isClickOrBackPress: Boolean,
        enable: Boolean = false,
        onAdClosed: () -> Unit = {},
        onAdLoaded: () -> Unit = {},
        onSplashAdViewGone: () -> Unit = {},
        onIapShow: () -> Unit = {}
    ) {
        val currentActivity = activity ?: run {
            onAdClosed()
            return
        }

        AdmobInterstitialAd.getInstance().showInterstitialClickAndBack(
            isBackPressAdShow = isClickOrBackPress,
            adIdString = adReference,
            activity = currentActivity,
            enable = enable,
            interstitialControllerListener = object : InterstitialControllerListener {
                override fun onAdClosed() = onAdClosed()
                override fun onAdLoaded() = onAdLoaded()
                override fun onSplashAdViewGone() = onSplashAdViewGone()
                override fun onIapShow() = onIapShow()
            })
    }

    fun showInterstitialAdEveryClick(
        enable: Boolean = false,
        onAdClosed: () -> Unit = {},
        onAdLoaded: () -> Unit = {},
        onSplashAdViewGone: () -> Unit = {},
        onIapShow: () -> Unit = {}
    ) {

        val currentActivity = activity ?: run {
            onAdClosed()
            return
        }

        AdmobInterstitialAd.getInstance().showInterstitialEveryClick(
            adIdString = adReference,
            activity = currentActivity,
            enableAds = enable,
            interstitialControllerListener = object : InterstitialControllerListener {
                override fun onAdClosed() = onAdClosed()
                override fun onAdLoaded() = onAdLoaded()
                override fun onSplashAdViewGone() = onSplashAdViewGone()
                override fun onIapShow() = onIapShow()
            })
    }

    fun hasAd(): Boolean = AdmobInterstitialAd.getInstance().hasAd()

    internal fun clear() {
        activity = null
    }
}

@Composable
fun rememberIkComposeRewardedInterstitialAdPreLoad(activity: Activity,
    adReference: String,
    enable: Boolean = false,
    preloadOnStart: Boolean = false,
    onUserEarnedReward: (rewardType: String, rewardAmount: Int) -> Unit = { _, _ -> }
): IkComposeRewardedInterstitialController {
    val controller = remember(adReference) {
        IkComposeRewardedInterstitialController(adReference)
    }

    SideEffect {
        controller.updateRewardCallback(onUserEarnedReward)
    }

    LaunchedEffect(activity, enable, preloadOnStart, adReference) {
        controller.updateActivity(activity)
        if (preloadOnStart) {
            controller.preload(enable)
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.clear()
        }
    }

    return controller
}


@Composable
fun rememberIkComposeRewardedInterstitialAdCounter(activity: Activity,
    adReference: String,
    enable: Boolean = false,
    preloadOnStart: Boolean = false,
    onUserEarnedReward: (rewardType: String, rewardAmount: Int) -> Unit = { _, _ -> }
): IkComposeRewardedInterstitialController {
    val controller = remember(adReference) {
        IkComposeRewardedInterstitialController(adReference)
    }

    SideEffect {
        controller.updateRewardCallback(onUserEarnedReward)
    }

    LaunchedEffect(activity, enable, preloadOnStart, adReference) {
        controller.updateActivity(activity)
        controller.showRewardedInterstitialAdCounter(enable)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.clear()
        }
    }

    return controller
}

@Composable
fun rememberIkComposeRewardedInterstitialAdShowEveryClick(activity: Activity,
    adReference: String,
    enable: Boolean = false,
    preloadOnStart: Boolean = false,
    onUserEarnedReward: (rewardType: String, rewardAmount: Int) -> Unit = { _, _ -> }
): IkComposeRewardedInterstitialController {
    val controller = remember(adReference) {
        IkComposeRewardedInterstitialController(adReference)
    }

    SideEffect {
        controller.updateRewardCallback(onUserEarnedReward)
    }

    LaunchedEffect(activity, enable, preloadOnStart, adReference) {
        controller.updateActivity(activity)
        controller.showRewardedInterstitialAdEveryClick(enable)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.clear()
        }
    }

    return controller
}

@Stable
class IkComposeRewardedInterstitialController internal constructor(
    private val adReference: String
) {
    private var activity: Activity? = null
    private var onUserEarnedReward: (rewardType: String, rewardAmount: Int) -> Unit = { _, _ -> }

    internal fun updateActivity(activity: Activity?) {
        this.activity = activity
    }

    internal fun updateRewardCallback(
        onUserEarnedReward: (rewardType: String, rewardAmount: Int) -> Unit
    ) {
        this.onUserEarnedReward = onUserEarnedReward
    }

    fun preload(
        enable: Boolean = false,
        onAdFailed: () -> Unit = {},
        onAdClosed: () -> Unit = {},
        onAdLoaded: () -> Unit = {},
        onIapShow: () -> Unit = {}
    ) {
        if (!enable) return
        val appCompatActivity = activity as? AppCompatActivity ?: return
        AdmobRewardedInterstitialAd.getInstance().preLoadAd(
            adIdReferenceName = adReference,
            mContext = appCompatActivity,
            interstitialControllerListener = rewardedListener(
                onAdClosed = onAdClosed,
                onAdLoaded = onAdLoaded,
                onAdFailed = onAdFailed,
                onIapShow = onIapShow
            )
        )
    }

    fun showRewardedInterstitialAdCounter(
        enable: Boolean = false,
        onAdClosed: () -> Unit = {},
        onAdLoaded: () -> Unit = {},
        onAdFailed: () -> Unit = {},
        onIapShow: () -> Unit = {}
    ) {
        val currentActivity = activity ?: run {
            onAdClosed()
            return
        }

        AdmobRewardedInterstitialAd.getInstance().showRewardedInterstitial(
            adIdString = adReference,
            activity = currentActivity,
            enable = enable,
            interstitialControllerListener = rewardedListener(
                onAdClosed = onAdClosed,
                onAdLoaded = onAdLoaded,
                onAdFailed = onAdFailed,
                onIapShow = onIapShow
            )
        )
    }

    fun showRewardedInterstitialAdEveryClick(
        enable: Boolean = false,
        onAdClosed: () -> Unit = {},
        onAdLoaded: () -> Unit = {},
        onAdFailed: () -> Unit = {},
        onIapShow: () -> Unit = {}
    ) {
        val currentActivity = activity ?: run {
            onAdClosed()
            return
        }

        AdmobRewardedInterstitialAd.getInstance().showRewardedInterstitialEveryClick(
            adIdString = adReference,
            activity = currentActivity,
            enableAds = enable,
            interstitialControllerListener = rewardedListener(
                onAdClosed = onAdClosed,
                onAdLoaded = onAdLoaded,
                onAdFailed = onAdFailed,
                onIapShow = onIapShow
            )
        )
    }

    fun hasAd(): Boolean = AdmobRewardedInterstitialAd.getInstance().hasAd()

    private fun rewardedListener(
        onAdClosed: () -> Unit,
        onAdLoaded: () -> Unit,
        onAdFailed: () -> Unit,
        onIapShow: () -> Unit
    ): RewardedInterstitialControllerListener {
        return object : RewardedInterstitialControllerListener {
            override fun onAdClosed() = onAdClosed()
            override fun onAdLoaded() = onAdLoaded()
            override fun onAdFailed() = onAdFailed()
            override fun onIapShow() = onIapShow()
            override fun onUserEarnedReward(rewardType: String, rewardAmount: Int) {
                onUserEarnedReward(rewardType, rewardAmount)
            }
        }
    }

    internal fun clear() {
        activity = null
    }
}

