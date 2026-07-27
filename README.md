# IkAdPlugin Android Ads SDK

A lightweight Android monetization SDK for AdMob placements. The SDK centralizes ad unit IDs, consent setup, Firebase-style runtime config, native ad layouts, app open ads, interstitial counters, rewarded interstitial ads, banner ads, collapsible banners, and optional remove-ads billing helpers.

This guide is written for app developers who want to add the SDK step by step.

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [AdMob app ID setup](#admob-app-id-setup)
- [Application class setup](#application-class-setup)
- [SDK configuration](#sdk-configuration)
- [Consent and splash startup](#consent-and-splash-startup)
- [View/XML usage](#viewxml-usage)
- [Jetpack Compose usage](#jetpack-compose-usage)
- [Remove ads purchases](#remove-ads-purchases)
- [Native ad layout types](#native-ad-layout-types)
- [Useful SDK classes](#useful-sdk-classes)
- [Troubleshooting](#troubleshooting)

## Requirements

- Android min SDK 24 or higher.
- A valid AdMob app ID.
- AdMob ad unit IDs for the placements you want to use.
- Internet access on the device.
- Google UMP consent flow if your app needs GDPR/EEA consent.
- Billing products in Google Play Console only if you use remove-ads purchases or subscriptions.

The SDK module currently declares version `0.1.24`.

## Installation

### Option A: Use JitPack

Add JitPack in your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add the dependency in your app module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.24")
}
```

Use the same version as your released GitHub/JitPack tag. If you publish a newer tag, replace `0.1.24` with that tag.

### Option B: Use as a local module

If this SDK source is inside your project, include the module in `settings.gradle.kts`:

```kotlin
include(":app", ":admob_monetization")
```

Then add it in your app module:

```kotlin
dependencies {
    implementation(project(":admob_monetization"))
}
```

## AdMob app ID setup

The SDK library manifest reads the AdMob app ID from the `ADMOB_APP_ID` manifest placeholder.

Add this in your app module `build.gradle.kts`:

```kotlin
android {
    buildTypes {
        debug {
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-3940256099942544~3347511713" // Google test app ID
        }

        release {
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" // Your real AdMob app ID
        }
    }
}
```

## Application class setup

Create an `Application` class. This keeps the current activity available for app open ads and starts Firebase ad analytics tracking.

```kotlin
package com.example.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.monetization.ikadplugin.ads.AdKeys.IS_APP_PAUSE
import com.monetization.ikadplugin.ads.AdKeys.canShowOpenAd
import com.monetization.ikadplugin.ads_duration_tracker.AdClickDurationTracker
import com.monetization.ikadplugin.network_instance.IkAdSdk

class MyApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var isOpenAdInitialized = false
    private val openAdBlockedScreens = setOf("SplashActivity")

    override fun onCreate() {
        super.onCreate()
        AdClickDurationTracker.init(this)
        registerActivityLifecycleCallbacks(this)
    }

    fun initOpenAd(adReference: String, enable: Boolean) {
        if (!isOpenAdInitialized) {
            isOpenAdInitialized = true
            IkAdSdk.openAppAdController.initOpenAd(
                context = this,
                adRef = adReference,
                openAdEnable = enable
            )
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        IkAdSdk.setCurrentActivity(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        IkAdSdk.setCurrentActivity(activity)
        canShowOpenAd = activity::class.java.simpleName !in openAdBlockedScreens
    }

    override fun onActivityResumed(activity: Activity) {
        IS_APP_PAUSE = false
        IkAdSdk.setCurrentActivity(activity)
        canShowOpenAd = activity::class.java.simpleName !in openAdBlockedScreens
    }

    override fun onActivityPaused(activity: Activity) {
        IS_APP_PAUSE = true
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (IkAdSdk.getCurrentActivity() === activity) {
            IkAdSdk.setCurrentActivity(null)
        }
        canShowOpenAd = true
        IS_APP_PAUSE = false
    }

    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
```

Register it in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ...>
</application>
```

## SDK configuration

Configure the SDK before you request or show ads. A good place is your splash activity or app startup flow.

### 1. Assign debug mode

```kotlin
FetchConfig.assignBuildConfig(BuildConfig.DEBUG)
```

Debug mode enables SDK debug behavior such as test consent geography and debug toasts.

### 2. Configure ad behavior

```kotlin
import androidx.core.graphics.toColorInt
import com.monetization.ikadplugin.firebase_value_fetch.AdsConfig
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.firebase_value_fetch.GradientColors

val adsConfig = AdsConfig(
    allAppInterstitialAdCountChange = 3,
    splashTime = 13,
    colorNativeCTR1 = "#E74625",
    colorNativeCTR2 = "#E74625",
    colorNativeBg = "#EDEDED",
    colorNativeBgDarkTheme = "#505050",
    colorNativeBgBorderStokes = "#505050",
    colorAdsAttribNative = "#000000",
    interstitialCounterStartSplash = true,
    nativeShimmerBtnColorChange = true,
    nativeButtonThemeColorChange = false,
    nativeAdsAttributionColorChange = false,
    nativeAdsBgColorChange = false,
    nativeButtonRectangle = false,
    allAdsOffEnable = false,
    splashInterstitialCallEnable = false,
    progressLoadingOpenAppEnable = true,
    interstitialPreloadEnable = true,
    interstitialPreloadProgressEnable = true,
    rewardedInterstitialPreloadEnable = true,
    rewardedInterstitialPreloadProgressEnable = true,
    everyNativeCtaColorChangeEnable = true,
    everyNativeCtaColor = listOf(
        GradientColors("#FF00C6FF".toColorInt(), "#FF0072FF".toColorInt()),
        GradientColors("#FF7F00FF".toColorInt(), "#FFE100FF".toColorInt()),
        GradientColors("#FFFF512F".toColorInt(), "#FFDD2476".toColorInt())
    ),
    darkTheme = false
)

FetchConfig.assignRemoteConfigValues(adsConfig)
```

You can build `AdsConfig` from local defaults, Firebase Remote Config, your backend, or any other config source.

Important fields:

| Field | Purpose |
| --- | --- |
| `allAppInterstitialAdCountChange` | Show counter ads after this many eligible actions. |
| `interstitialCounterStartSplash` | Controls whether the counter starts from splash behavior or main-flow behavior. |
| `allAdsOffEnable` | Turns off all SDK ad requests when `true`. |
| `splashInterstitialCallEnable` | Controls splash interstitial timing behavior. |
| `interstitialPreloadEnable` | Preloads interstitial ads instead of loading only on click. |
| `interstitialPreloadProgressEnable` | Shows loading dialog when a preloaded interstitial is shown. |
| `rewardedInterstitialPreloadEnable` | Preloads rewarded interstitial ads. |
| `progressLoadingOpenAppEnable` | Shows the loading dialog before app open ads. |
| `nativeButtonThemeColorChange` | Applies `colorNativeCTR1` and `colorNativeCTR2` to native CTA buttons. |
| `everyNativeCtaColorChangeEnable` | Uses a gradient from `everyNativeCtaColor` for each native position. |
| `darkTheme` | Uses dark native background color. |

### 3. Configure ad unit IDs

Every map should include a `"default"` key. The SDK uses it as a fallback when a placement key is missing.

```kotlin
import com.monetization.ikadplugin.ads_id.NetworkIdConfig
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig

val networkConfig = NetworkIdConfig(
    interstitialAds = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "splash" to "ca-app-pub-xxx/111",
        "home_interstitial" to "ca-app-pub-xxx/222",
        "exit_interstitial" to "ca-app-pub-xxx/333"
    ),
    rewardedInterstitialAds = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "rewarded_interstitial" to "ca-app-pub-xxx/444"
    ),
    nativeAdId = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "home_native" to "ca-app-pub-xxx/555",
        "details_native" to "ca-app-pub-xxx/666"
    ),
    bannerAdId = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "home_banner" to "ca-app-pub-xxx/777",
        "bottom_banner" to "ca-app-pub-xxx/888"
    ),
    openAppAdId = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "open_app" to "ca-app-pub-xxx/999"
    )
)

FetchConfig.initializeNetWorkId(networkConfig)
```

### 4. Optional remove-ads product IDs

```kotlin
FetchConfig.assignOneTimeRemoveAdsConfig(
    OneTimePurchaseConfig(productKey = "remove_ads")
)

FetchConfig.assignSubscriptionRemoveAdsConfig(
    SubscriptionConfig(
        sub_week_Key = "weekly_remove_ads",
        sub_monthly_Key = "monthly_remove_ads",
        sub_yearly_Key = "yearly_remove_ads"
    )
)
```

## Consent and splash startup

Call `IkAdSdk.startSplash()` from your splash or first activity after SDK config is assigned.

```kotlin
IkAdSdk.startSplash(
    context = this,
    consentVerifyID = "YOUR_TEST_DEVICE_HASH"
) { canRequestAds ->
    // This callback is reached after UMP consent and MobileAds initialization.
    // Continue your splash flow here.
}
```

To load and show a splash interstitial:

```kotlin
IkAdSdk.interstitialController.initAdMobSplash(
    adId = "splash",
    context = this,
    enable = true,
    interstitialControllerListener = object : InterstitialControllerListener {
        override fun onAdClosed() {
            openHomeScreen()
        }

        override fun onAdLoaded() = Unit
        override fun onSplashAdViewGone() = Unit
        override fun onIapShow() = Unit
    }
)
```

If the splash activity can pause and resume while loading the ad, forward lifecycle events:

```kotlin
override fun onPause() {
    IkAdSdk.interstitialController.pauseAd()
    super.onPause()
}

override fun onResume() {
    super.onResume()
    IkAdSdk.interstitialController.resumeAd(this, enable = true)
}

override fun onDestroy() {
    IkAdSdk.interstitialController.onDestroy()
    super.onDestroy()
}
```

After splash configuration and consent, initialize app open ads:

```kotlin
(application as MyApplication).initOpenAd(
    adReference = "open_app",
    enable = true
)
```

## View/XML usage

All inline View/XML ads need a `LinearLayout` container.

```xml
<LinearLayout
    android:id="@+id/adContainer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical" />
```

### Banner ad

```kotlin
val adContainer = findViewById<LinearLayout>(R.id.adContainer)

IkAdSdk.bannerController.populateBannerAd(
    adReference = "home_banner",
    context = this,
    enable = true,
    isRectangleBanner = false,
    adLayout = adContainer,
    loadNewAd = true
)
```

Use `isRectangleBanner = true` for `AdSize.MEDIUM_RECTANGLE`. Otherwise the SDK uses an adaptive banner size.

To preload first:

```kotlin
IkAdSdk.bannerController.loadBannerAd(
    adReference = "home_banner",
    context = this,
    enable = true,
    isRectangleBanner = false
)
```

### Collapsible banner ad

```kotlin
IkAdSdk.collapseBannerController.populateAd(
    adReference = "bottom_banner",
    activity = this,
    enable = true,
    adFrame = adContainer,
    loadNewAd = true
)
```

Destroy it when it is no longer needed:

```kotlin
override fun onDestroy() {
    IkAdSdk.collapseBannerController.destroy()
    super.onDestroy()
}
```

### Native ad

```kotlin
IkAdSdk.nativeController.populateNativeAd(
    adViewType = 4,
    adIdNativeReference = "home_native",
    context = this,
    enable = true,
    adFrame = adContainer,
    loadNewAd = true,
    nativeCtaColorAdPosition = 0
)
```

To preload a native ad:

```kotlin
IkAdSdk.nativeController.preLoadNativeAd(
    adIdNativeReference = "home_native",
    context = this,
    enable = true
)
```

To clean up:

```kotlin
override fun onDestroy() {
    IkAdSdk.nativeController.onDestroyNativeAd(this)
    super.onDestroy()
}
```

For fragments where an ad should appear only once per fragment key:

```kotlin
IkAdSdk.nativeController.populateNativeAdForFragment(
    adViewType = 1,
    adIdNativeReference = "details_native",
    context = requireContext(),
    enable = true,
    adFrame = adContainer,
    loadNewAd = false,
    nativeCtaColorAdPosition = 1,
    isFragmentCall = true,
    fragmentKey = "details_screen"
)
```

Reset that fragment state when needed:

```kotlin
IkAdSdk.nativeController.clearFragmentAdState("details_screen")
```

### Interstitial ad

Counter-based interstitial:

```kotlin
IkAdSdk.interstitialController.showInterstitial(
    adIdString = "home_interstitial",
    activity = this,
    enable = true,
    interstitialControllerListener = object : InterstitialControllerListener {
        override fun onAdClosed() {
            continueAction()
        }

        override fun onAdLoaded() = Unit
        override fun onSplashAdViewGone() = Unit
        override fun onIapShow() = Unit
    }
)
```

Every-click interstitial:

```kotlin
IkAdSdk.interstitialController.showInterstitialEveryClick(
    adIdString = "home_interstitial",
    activity = this,
    enableAds = true,
    interstitialControllerListener = object : InterstitialControllerListener {
        override fun onAdClosed() {
            continueAction()
        }

        override fun onAdLoaded() = Unit
        override fun onSplashAdViewGone() = Unit
        override fun onIapShow() = Unit
    }
)
```

Back-press or click-controlled interstitial:

```kotlin
IkAdSdk.interstitialController.showInterstitialClickAndBack(
    isBackPressAdShow = shouldShowBackAd,
    adIdString = "exit_interstitial",
    activity = this,
    enable = true,
    interstitialControllerListener = listener
)
```

Preload an interstitial:

```kotlin
IkAdSdk.interstitialController.preLoadAd(
    adIdReferenceName = "home_interstitial",
    mContext = this
)
```

Reset the global interstitial counter:

```kotlin
IkAdSdk.interstitialController.resetInterstitialCounter()
```

### Rewarded interstitial ad

```kotlin
IkAdSdk.rewardedInterstitialController.showRewardedInterstitialEveryClick(
    adIdString = "rewarded_interstitial",
    activity = this,
    enableAds = true,
    interstitialControllerListener = object : RewardedInterstitialControllerListener {
        override fun onUserEarnedReward(rewardType: String, rewardAmount: Int) {
            unlockReward(rewardType, rewardAmount)
        }

        override fun onAdRewardGranted(boolean: Boolean) {
            // Called when the full-screen ad is dismissed.
        }

        override fun onAdClosed() {
            continueAfterAd()
        }

        override fun onAdLoaded() = Unit
        override fun onAdFailed() = continueAfterAd()
        override fun onIapShow() = Unit
    }
)
```

Counter-based rewarded interstitial:

```kotlin
IkAdSdk.rewardedInterstitialController.showRewardedInterstitial(
    adIdString = "rewarded_interstitial",
    activity = this,
    enable = true,
    interstitialControllerListener = rewardedListener
)
```

Preload a rewarded interstitial:

```kotlin
IkAdSdk.rewardedInterstitialController.preLoadAd(
    adIdReferenceName = "rewarded_interstitial",
    mContext = this,
    interstitialControllerListener = rewardedListener
)
```

### App open ad controls

The app open ad is shown by the process lifecycle observer after `initOpenAd()`.

Disable app open ads while showing Android permission dialogs:

```kotlin
IkAdSdk.openAppAdController.openAppAdDisableWhenPermissionCheck()
```

Release app open ads if you need to stop the observer:

```kotlin
IkAdSdk.openAppAdController.releaseOpenAd()
```

You can also block app open ads from selected screens:

```kotlin
AdKeys.canShowOpenAd = false
```

## Jetpack Compose usage

The SDK exposes Compose helpers in:

```kotlin
import com.monetization.ikadplugin.compose.IkComposeBannerAd
import com.monetization.ikadplugin.compose.IkComposeCollapsibleBannerAd
import com.monetization.ikadplugin.compose.IkComposeNativeAd
import com.monetization.ikadplugin.compose.rememberIkComposeInterstitialAdAssignContext
import com.monetization.ikadplugin.compose.rememberIkComposeRewardedInterstitialAdPreLoad
```

Use the current `Activity` when calling the composables:

```kotlin
@Composable
fun HomeScreen(activity: Activity) {
    val interstitial = rememberIkComposeInterstitialAdAssignContext(
        activity = activity,
        adReference = "home_interstitial",
        enable = true
    )

    val rewarded = rememberIkComposeRewardedInterstitialAdPreLoad(
        activity = activity,
        adReference = "rewarded_interstitial",
        enable = true,
        preloadOnStart = true,
        onUserEarnedReward = { rewardType, rewardAmount ->
            unlockReward(rewardType, rewardAmount)
        }
    )

    IkComposeBannerAd(
        activity = activity,
        adReference = "home_banner",
        enable = true,
        loadNewAd = true
    )

    IkComposeCollapsibleBannerAd(
        activity = activity,
        adReference = "bottom_banner",
        enable = true,
        loadNewAd = true
    )

    IkComposeNativeAd(
        context = activity,
        adIdNativeReference = "home_native",
        enable = true,
        adViewType = 4,
        loadNewAd = true,
        nativeCtaColorAdPosition = 0
    )

    Button(
        onClick = {
            interstitial.showInterstitialAdEveryClick(
                enable = true,
                onAdClosed = { continueAction() }
            )
        }
    ) {
        Text("Continue")
    }

    Button(
        onClick = {
            rewarded.showRewardedInterstitialAdEveryClick(
                enable = true,
                onAdClosed = { continueAfterAd() },
                onAdFailed = { continueAfterAd() }
            )
        }
    ) {
        Text("Reward")
    }
}
```

For navigation screens where you want inline ads to survive leaving and returning, use:

- `NativeAdViewModel`
- `BannerAdViewModel`
- `NativeAdSlot`
- `BannerAdSlot`

Create one ad view model per placement key.

## Remove ads purchases

The SDK hides ads when either of these values becomes true:

- `AdSharedPreference.getInstance(context).appAdPurchased`
- `AdSharedPreference.getInstance(context).isSubscription`

### One-time remove ads purchase

Configure the product key first:

```kotlin
FetchConfig.assignOneTimeRemoveAdsConfig(
    OneTimePurchaseConfig(productKey = "remove_ads")
)
```

Initialize billing:

```kotlin
IkAdSdk.oneTimePurchaseController.initBilling(applicationContext)
```

Read the price:

```kotlin
lifecycleScope.launch {
    IkAdSdk.oneTimePurchaseController.productPriceFlow.collect { model ->
        priceTextView.text = model.price
    }
}
```

Start purchase:

```kotlin
IkAdSdk.oneTimePurchaseController.purchaseProduct(this)
```

Check purchase history if needed:

```kotlin
IkAdSdk.oneTimePurchaseController.checkHistoryIfSkuNull(this)
```

### Subscription remove ads

Configure product IDs:

```kotlin
FetchConfig.assignSubscriptionRemoveAdsConfig(
    SubscriptionConfig(
        sub_week_Key = "weekly_remove_ads",
        sub_monthly_Key = "monthly_remove_ads",
        sub_yearly_Key = "yearly_remove_ads"
    )
)
```

Initialize and load products:

```kotlin
IkAdSdk.subscriptionController.fetchProductsListIfNull(this)
```

Collect products:

```kotlin
lifecycleScope.launch {
    IkAdSdk.subscriptionController.productListFlow.collect { model ->
        val monthlyId = IkAdSdk.subscriptionController.getSelectedSubscriptionId(PLAN.MONTHLY)
        val monthlyProduct = model.productsList[monthlyId]
    }
}
```

Start a subscription purchase:

```kotlin
monthlyProduct?.let {
    IkAdSdk.subscriptionController.purchaseProduct(this, it)
}
```

Change an existing subscription:

```kotlin
yearlyProduct?.let {
    IkAdSdk.subscriptionController.changeSubscriptionPlan(this, it)
}
```

## Native ad layout types

Pass one of these values as `adViewType`:

| Value | Layout |
| --- | --- |
| `0` | `split_native_layout` |
| `1` | `small_native_layout` |
| `2` | `small_button_native_layout` |
| `3` | `large_native_layout_top` |
| `4` | `large_native_layout` |
| `5` | `large_native_layout_exit` |

Any other value falls back to `large_native_layout`.

When `everyNativeCtaColorChangeEnable = true`, the SDK chooses the CTA gradient like this:

- `nativeCtaColorAdPosition = -1`: random gradient.
- `nativeCtaColorAdPosition >= 0`: `everyNativeCtaColor[position % everyNativeCtaColor.size]`.

## Useful SDK classes

| Class | Purpose |
| --- | --- |
| `IkAdSdk` | Main SDK entry point and controller holder. |
| `FetchConfig` | Assigns runtime config, ad unit IDs, build/debug mode, and purchase IDs. |
| `AdsConfig` | Holds ad behavior and styling flags. |
| `NetworkIdConfig` | Holds placement-reference to AdMob ad unit ID maps. |
| `AdSharedPreference` | Stores remove-ads purchase and subscription status. |
| `GoogleMobileAdsConsentManager` | UMP consent helper used by `IkAdSdk.startSplash()`. |
| `AdClickDurationTracker` | Logs ad request, show, click, reward, warning, and error events to Firebase Analytics. |

## Troubleshooting

### Ads never show

- Confirm `IkAdSdk.startSplash()` completed before showing ads.
- Confirm `FetchConfig.initializeNetWorkId()` was called before loading placements.
- Make sure each ID map includes `"default"`.
- Check `AdsConfig.allAdsOffEnable`; when true, every ad request is skipped.
- Check `enable`; most ad calls skip loading when `enable = false`.
- Check purchase state; ads are hidden when the app is purchased or subscribed.
- Confirm the device has internet.
- Use AdMob test IDs during development.

### Consent blocks ad requests

- `GoogleMobileAdsConsentManager.canRequestAds` must be true before ad requests run.
- Call `FetchConfig.assignBuildConfig(BuildConfig.DEBUG)` before consent if you want debug UMP behavior.
- Pass your real hashed test device ID in `consentVerifyID` while testing.

### App open ads show on screens where they should not

- Set `AdKeys.canShowOpenAd = false` for blocked screens.
- In your `Application` lifecycle callbacks, keep splash, permission, purchase, and sensitive screens blocked.
- Call `IkAdSdk.openAppAdController.openAppAdDisableWhenPermissionCheck()` before requesting Android runtime permissions.

### Inline ads leave empty space

- The SDK hides the `LinearLayout` on failed or suppressed requests.
- Keep the container height `wrap_content`.
- Do not put fixed-height empty ad containers in production layouts unless your design intentionally reserves space.

### Preloaded ads do not show immediately

- Enable the relevant preload flag in `AdsConfig`.
- Call the correct preload method early enough.
- AdMob fill rate still depends on inventory, consent, network, ad unit health, and app review state.
