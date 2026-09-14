# IkAdPlugin — Android Ads & Monetization SDK

A drop-in Android monetization library that wraps Google AdMob, the Google UMP consent SDK and
Google Play Billing behind a single entry point (`IkAdSdk`).

Instead of writing ad-loading boilerplate per screen, you register your ad unit IDs once as
**placement references** (`"home_native"`, `"splash"`, `"exit"`, …), assign a runtime config object,
then call one method per placement. The SDK handles consent, network checks, purchase state,
shimmer placeholders, preloading, counters, loading dialogs, lifecycle cleanup and Firebase
Analytics tracking for you.

| | |
| --- | --- |
| **Module** | `admob_monetization` |
| **Namespace / package** | `com.monetization.ikadplugin` |
| **Current version** | `0.1.25` |
| **Maven coordinates** | `com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.25` |
| **Repository** | https://github.com/Muhammadimrankhan/Monetization_IkAdSdk |
| **min SDK / compile SDK** | 24 / 37 |
| **Java / Kotlin target** | Java 11 (JVM target 11) |
| **UI support** | XML/Views **and** Jetpack Compose |

---

## Contents

- [Feature overview](#feature-overview)
- [Project structure](#project-structure)
- [What the SDK bundles](#what-the-sdk-bundles)
- [Requirements](#requirements)
- [Installation](#installation)
- [AdMob app ID setup](#admob-app-id-setup)
- [Application class setup](#application-class-setup)
- [SDK configuration](#sdk-configuration)
- [Consent and splash startup](#consent-and-splash-startup)
- [The gate: when is an ad actually requested?](#the-gate-when-is-an-ad-actually-requested)
- [View / XML usage](#view--xml-usage)
- [Jetpack Compose usage](#jetpack-compose-usage)
- [Remove-ads purchases](#remove-ads-purchases)
- [Native ad layout types and styling](#native-ad-layout-types-and-styling)
- [Interstitial counter logic](#interstitial-counter-logic)
- [Firebase Analytics events](#firebase-analytics-events)
- [Full API reference](#full-api-reference)
- [ProGuard / R8](#proguard--r8)
- [Testing checklist](#testing-checklist)
- [Troubleshooting](#troubleshooting)
- [Release / publishing](#release--publishing)

---

## Feature overview

| Ad format | Controller | Highlights |
| --- | --- | --- |
| **Banner** | `IkAdSdk.bannerController` | Adaptive anchored banner or `MEDIUM_RECTANGLE`, preload + populate, shimmer placeholder |
| **Collapsible banner** | `IkAdSdk.collapseBannerController` | AdMob `collapsible = "bottom"` extras, preload + populate, explicit `destroy()` |
| **Native** | `IkAdSdk.nativeController` | 6 built-in layouts, shimmer, gradient CTA rotation, dark theme, per-fragment "show once" mode |
| **Interstitial** | `IkAdSdk.interstitialController` | Splash flow, counter flow, every-click flow, back-press flow, preload, loading dialog, Android 15 immersive |
| **Rewarded interstitial** | `IkAdSdk.rewardedInterstitialController` | Counter + every-click flows, reward callback, preload, loading dialog |
| **App open** | `IkAdSdk.openAppAdController` | `ProcessLifecycleOwner` observer, 4-hour cache expiry, per-screen blocking, permission-dialog guard |

| Non-ad feature | Entry point |
| --- | --- |
| GDPR / UMP consent | `IkAdSdk.startSplash()`, `GoogleMobileAdsConsentManager` |
| Runtime config (Firebase Remote Config friendly) | `FetchConfig` + `AdsConfig` |
| Placement reference → ad unit ID mapping | `FetchConfig` + `NetworkIdConfig` |
| One-time "remove ads" purchase | `IkAdSdk.oneTimePurchaseController` |
| Subscriptions (weekly / monthly / yearly) | `IkAdSdk.subscriptionController` |
| Purchase state persistence | `AdSharedPreference` |
| Internet / VPN detection | `InternetController` |
| Ad funnel analytics | `AdClickDurationTracker` |
| Compose integration | `com.monetization.ikadplugin.compose.*` |

---

## Project structure

```
IkAdPlugin/
├── admob_monetization/                  ← the SDK library module (this is what you ship)
│   └── src/main/java/com/monetization/ikadplugin/
│       ├── ads/
│       │   ├── AdKeys.kt                 global runtime flags
│       │   ├── AdLoadingDialog.kt        full-screen "ad is loading" dialog
│       │   ├── FirebaseValue.kt          live config values read by the ad classes
│       │   ├── NativeAdLayouts.kt        adViewType → layout resolver
│       │   ├── NativeShimmerEffect.kt    shimmer placeholder builder
│       │   ├── NativeViewPopulate.kt     native binding + adaptive banner sizing
│       │   ├── banner/AdmobBannerAd.kt
│       │   ├── collapse/AdmobCollapsibleBannerAd.kt
│       │   ├── interstitial_ads/         AdmobInterstitialAd + InterstitialControllerListener
│       │   ├── native_ads/AdmobNativeAd.kt
│       │   ├── open_ap_ads/              AdmobOpenAppAd + OpenAdControllerListener
│       │   ├── rewarded_interstitial/    AdmobRewardedInterstitialAd + listener
│       │   └── shimmer_effect/           vendored Shimmer implementation (Java)
│       ├── ads_duration_tracker/AdClickDurationTracker.kt
│       ├── ads_id/NetworkIdConfig.kt
│       ├── compose/                      Compose wrappers, ad slots, ad ViewModels
│       ├── consent_sdk/GoogleMobileAdsConsentManager.kt
│       ├── firebase_value_fetch/         AdsConfig, FetchConfig, GradientColors, CallBack
│       ├── internetController/InternetController.kt
│       ├── network_instance/IkAdSdk.kt   ← main entry point
│       ├── one_time_purchase/ProductsPurchaseHelper.kt
│       ├── pref/                         AdSharedPreference, OneTimePurchaseConfig
│       └── subscription/                 SubscriptionHelper, PLAN, configs, constants
│   └── src/main/res/layout/              6 native layouts + shimmer + loading dialog
│
└── app/                                  ← sample host app (configuration reference)
    └── src/main/java/com/monetization/admob/
        ├── IkAdPluginAppClass.kt         Application + ActivityLifecycleCallbacks example
        └── MainActivity.kt               AdsConfig + NetworkIdConfig wiring example
```

---

## What the SDK bundles

These arrive transitively — you do **not** need to add them again:

| Dependency | Version |
| --- | --- |
| `com.google.android.gms:play-services-ads` | 25.4.0 |
| `com.google.android.ump:user-messaging-platform` | 4.0.0 |
| `com.android.billingclient:billing-ktx` | 9.1.0 |
| `com.google.firebase:firebase-analytics` (via BoM) | 34.17.0 |
| `androidx.lifecycle:lifecycle-process` | 2.11.0 |
| `androidx.compose.runtime` / `androidx.compose.ui` | 1.11.4 |
| `androidx.appcompat` / `core-ktx` / `material` | 1.7.1 / 1.19.0 / 1.14.0 |
| `com.intuit.sdp` / `com.intuit.ssp` | 1.1.1 |

The library manifest declares these permissions for you:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="com.android.vending.BILLING" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

It also injects the AdMob `APPLICATION_ID` meta-data and re-themes
`com.google.android.gms.ads.AdActivity` with a black `AdTheme` (no enter/exit animation) so
full-screen ads do not flash white.

---

## Requirements

- Android **minSdk 24** or higher.
- A valid **AdMob app ID** plus ad unit IDs for the placements you use.
- `google-services.json` and the Google Services Gradle plugin **if you want the Firebase Analytics
  events** (without Firebase the tracker simply no-ops).
- Google Play Billing products configured in Play Console **only** if you use remove-ads purchases
  or subscriptions.
- An `AppCompatActivity` for the interstitial / rewarded **preload** APIs — they require it.
  Everything else accepts a plain `Activity`.

---

## Installation

### Option A — JitPack (recommended)

`settings.gradle.kts`:

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

App module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.25")
}
```

Always use the tag you actually published on GitHub/JitPack.

### Option B — local module

```kotlin
// settings.gradle.kts
include(":app", ":admob_monetization")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":admob_monetization"))
}
```

---

## AdMob app ID setup

The library manifest reads `${ADMOB_APP_ID}` from a manifest placeholder, so define it per build type:

```kotlin
android {
    buildTypes {
        debug {
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-3940256099942544~3347511713" // Google test app ID
        }
        release {
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" // your real AdMob app ID
        }
    }
}
```

> Without this placeholder the manifest merge fails, or the Ads SDK crashes at init with
> *"The Google Mobile Ads SDK was initialized without an application ID"*.

---

## Application class setup

The `Application` class does three jobs: keep the current activity available for app open ads,
maintain the foreground/background flags, and initialise the analytics tracker.

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

    /** Screens that must never be interrupted by an app open ad. */
    private val openAdBlockedScreens = setOf("SplashActivity", "PremiumActivity")

    override fun onCreate() {
        super.onCreate()
        AdClickDurationTracker.init(this)          // Firebase ad-funnel analytics
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
            IkAdSdk.setCurrentActivity(null)       // clears the WeakReference
        }
        canShowOpenAd = true
        IS_APP_PAUSE = false
    }

    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
```

```xml
<application android:name=".MyApplication" ... />
```

---

## SDK configuration

Configure **before** requesting or showing any ad — the splash activity is the natural place.

### 1. Debug mode

```kotlin
FetchConfig.assignBuildConfig(BuildConfig.DEBUG)
```

When `true` the SDK:

- forces UMP `DEBUG_GEOGRAPHY_EEA` so you can exercise the consent form,
- applies your `consentVerifyID` as an AdMob test device ID,
- shows debug toasts for every tracked ad event.

### 2. Ad behaviour config (`AdsConfig`)

`AdsConfig` is a plain data class — build it from local defaults, Firebase Remote Config, your own
backend, or anything else, then hand it to `FetchConfig.assignRemoteConfigValues()`.

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

#### Every `AdsConfig` field

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `allAppInterstitialAdCountChange` | `Int` | `3` | Counter threshold — how many eligible actions before a counter interstitial/rewarded shows. |
| `splashTime` | `Long` | `13` | Splash timeout (seconds), exposed to your splash flow as `FirebaseValue.SPLASH_TIME`. |
| `colorNativeCTR1` / `colorNativeCTR2` | `String` | `#E74625` | Start/end colors of the native CTA gradient when `nativeButtonThemeColorChange = true`. |
| `colorNativeBg` | `String` | `#EDEDED` | Native ad background in light theme (needs `nativeAdsBgColorChange = true`). |
| `colorNativeBgDarkTheme` | `String` | `#505050` | Native ad background when `darkTheme = true`. |
| `colorNativeBgBorderStokes` | `String` | `#505050` | Native ad border stroke color. |
| `colorAdsAttribNative` | `String` | `#000000` | "Ad" attribution badge color (needs `nativeAdsAttributionColorChange = true`). |
| `interstitialCounterStartSplash` | `Boolean` | `true` | Selects the counter algorithm — see [Interstitial counter logic](#interstitial-counter-logic). |
| `nativeShimmerBtnColorChange` | `Boolean` | `true` | Tints the shimmer placeholder's CTA button. |
| `nativeButtonThemeColorChange` | `Boolean` | `false` | Applies the `colorNativeCTR1`→`colorNativeCTR2` gradient to the native CTA. |
| `nativeAdsAttributionColorChange` | `Boolean` | `false` | Applies `colorAdsAttribNative` to the "Ad" badge. |
| `nativeAdsBgColorChange` | `Boolean` | `false` | Applies the configured background color to the native container. |
| `nativeButtonRectangle` | `Boolean` | `false` | Square CTA corners instead of rounded. |
| `allAdsOffEnable` | `Boolean` | `false` | **Kill switch** — when `true` every ad request in the SDK is skipped. |
| `splashInterstitialCallEnable` | `Boolean` | `false` | Splash interstitial timing flag (`FirebaseValue.SPLASH_INTERSTITIAL_CALL_ENABLE`). |
| `progressLoadingOpenAppEnable` | `Boolean` | `true` | Show the loading dialog before an app open ad. |
| `interstitialPreloadEnable` | `Boolean` | `false` | Preload interstitials in the background instead of loading on demand. |
| `interstitialPreloadProgressEnable` | `Boolean` | `false` | Show the ~1s loading dialog even when a preloaded interstitial is ready. |
| `rewardedInterstitialPreloadEnable` | `Boolean` | `false` | Same as above, for rewarded interstitials. |
| `rewardedInterstitialPreloadProgressEnable` | `Boolean` | `false` | Same as above, for rewarded interstitials. |
| `everyNativeCtaColorChangeEnable` | `Boolean` | `false` | Rotate CTA gradients from `everyNativeCtaColor` per placement position. |
| `everyNativeCtaColor` | `List<GradientColors>` | `emptyList()` | Gradient pool. `GradientColors(start: Int, end: Int)` takes ARGB ints. |
| `darkTheme` | `Boolean` | `false` | Use `colorNativeBgDarkTheme` for native backgrounds. |

> All of these land on the mutable singleton `FirebaseValue`. You can also flip a single value at
> runtime (e.g. `FirebaseValue.ALL_ADS_OFF_ENABLE = true`) without rebuilding the whole config.

### 3. Ad unit IDs (`NetworkIdConfig`)

Placement references are arbitrary strings you choose. **Every map must contain a `"default"` key** —
it is the fallback whenever a reference is missing.

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
        "bottom_banner" to "ca-app-pub-xxx/888"   // collapsible banners read this map too
    ),
    openAppAdId = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "open_app" to "ca-app-pub-xxx/999"
    )
)

FetchConfig.initializeNetWorkId(networkConfig)
```

Lookup helpers (used internally, public if you need them):
`FetchConfig.getInterstitialId()`, `getRewardedInterstitialId()`, `getNativeId()`, `getBannerId()`,
`getOpenAppAdId()` — each returns `""` when neither the reference nor `"default"` exists.

### 4. Purchase product IDs

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

---

## Consent and splash startup

`IkAdSdk.startSplash()` gathers UMP consent and initialises the Mobile Ads SDK, then calls you back.
It short-circuits (calling back immediately) when consent was already gathered in this process, when
the user has purchased, or when there is no internet.

```kotlin
IkAdSdk.startSplash(
    context = this,                             // must be an Activity
    consentVerifyID = "YOUR_TEST_DEVICE_HASH"   // used only in debug builds
) { canRequestAds ->
    // Reached after the UMP form (if any) and MobileAds.initialize().
    // canRequestAds is a Boolean: true when ads may be requested.
    continueSplashFlow()
}
```

Splash interstitial:

```kotlin
IkAdSdk.interstitialController.initAdMobSplash(
    adId = "splash",
    context = this,
    enable = true,
    interstitialControllerListener = object : InterstitialControllerListener {
        override fun onAdClosed() = openHomeScreen()
        override fun onAdLoaded() = Unit
        override fun onSplashAdViewGone() = Unit   // good place to hide your splash progress
        override fun onIapShow() = Unit            // fired when the user is already premium
    }
)
```

Forward the splash lifecycle so a backgrounded splash never shows an ad on top of another app:

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

If the splash ad failed and you want a second chance on the next screen:

```kotlin
IkAdSdk.interstitialController.showInterstitialIfSplashFail(
    adIdString = "home_interstitial",
    activity = this,
    enableAds = true,
    interstitialControllerListener = listener
)
```

Then start app open ads (after consent, never before):

```kotlin
(application as MyApplication).initOpenAd(adReference = "open_app", enable = true)
```

### Privacy options ("Manage consent") entry point

Required in the EEA so users can change their choice later:

```kotlin
val consent = GoogleMobileAdsConsentManager.getInstance(this)

if (consent.isPrivacyOptionsRequired) {
    settingsPrivacyButton.isVisible = true
    settingsPrivacyButton.setOnClickListener {
        consent.showPrivacyOptionsForm(this) { formError ->
            formError?.let { Log.w("Consent", it.message) }
        }
    }
}
```

---

## The gate: when is an ad actually requested?

Every inline and full-screen request passes the same guard. Understanding it answers almost every
"why is nothing showing?" question:

```
!FirebaseValue.ALL_ADS_OFF_ENABLE                 // kill switch off
&& GoogleMobileAdsConsentManager.canRequestAds    // UMP consent obtained
&& enable                                         // the per-call flag you passed
&& !AdSharedPreference.isAppPurchased             // not premium (purchase OR subscription)
&& InternetController.isInternetConnected         // a validated network is up
```

Full-screen formats add two more: `!AdKeys.IS_APP_PAUSE` (app is in the foreground) and
`!AdKeys.isShowingOpenAd` (no app open ad on screen).

When the gate fails, inline containers are hidden (`View.GONE`, so no empty gap) and full-screen
listeners get `onAdClosed()` immediately — your flow continues, it is never blocked.

### `AdKeys` runtime flags

| Flag | Meaning |
| --- | --- |
| `AdKeys.canShowOpenAd` | Set `false` on screens where app open ads must not appear. |
| `AdKeys.isPermissionCheck` | Set by `openAppAdDisableWhenPermissionCheck()`; suppresses the next open ad. |
| `AdKeys.IS_APP_PAUSE` | `true` while the app is backgrounded; maintained by your `Application` callbacks. |
| `AdKeys.isShowingOpenAd` | `true` while an app open ad is on screen. |
| `AdKeys.iapScreenShow` | Marks that a purchase screen is showing. |

---

## View / XML usage

Every inline (banner / collapsible / native) ad needs a `LinearLayout` container:

```xml
<LinearLayout
    android:id="@+id/adContainer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical" />
```

The SDK inserts a shimmer placeholder while loading, swaps in the real ad view on success, and hides
the container on failure — so keep the height `wrap_content` and never reserve fixed empty space.

### Banner ad

```kotlin
val adContainer = findViewById<LinearLayout>(R.id.adContainer)

// Optional: preload earlier (e.g. on the previous screen) so populate is instant
IkAdSdk.bannerController.loadBannerAd(
    adReference = "home_banner",
    context = this,
    enable = true,
    isRectangleBanner = false
)

IkAdSdk.bannerController.populateBannerAd(
    adReference = "home_banner",
    context = this,
    enable = true,
    isRectangleBanner = false,   // true → AdSize.MEDIUM_RECTANGLE, false → adaptive anchored
    adLayout = adContainer,
    loadNewAd = true             // request a fresh ad when nothing is cached
)

// State helpers
IkAdSdk.bannerController.hasBanner()          // a banner is loaded
IkAdSdk.bannerController.hasBannerOrLoading() // loaded, or a request is in flight
```

### Collapsible banner ad

Uses the same `bannerAdId` map and sends AdMob's `collapsible = "bottom"` extras.

```kotlin
IkAdSdk.collapseBannerController.loadAd(
    adReference = "bottom_banner",
    activity = this,
    enable = true
)

IkAdSdk.collapseBannerController.populateAd(
    adReference = "bottom_banner",
    activity = this,
    enable = true,
    adFrame = adContainer,
    loadNewAd = true
)

override fun onDestroy() {
    IkAdSdk.collapseBannerController.destroy()   // required — releases the AdView
    super.onDestroy()
}
```

`hasAd()` / `hasAdOrLoading()` are available for state checks.

### Native ad

```kotlin
// Optional preload
IkAdSdk.nativeController.preLoadNativeAd(
    adIdNativeReference = "home_native",
    context = this,
    enable = true
)

IkAdSdk.nativeController.populateNativeAd(
    adViewType = 4,                       // see "Native ad layout types"
    adIdNativeReference = "home_native",
    context = this,
    enable = true,
    adFrame = adContainer,
    loadNewAd = true,
    nativeCtaColorAdPosition = 0          // -1 = random gradient
)

override fun onDestroy() {
    IkAdSdk.nativeController.onDestroyNativeAd(this)
    super.onDestroy()
}

IkAdSdk.nativeController.hasLargeAd()          // a native ad is cached
IkAdSdk.nativeController.hasLargeAdOrLoading()
```

**Fragment mode** — show the ad only the first time a given fragment key is visited:

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

// Allow it to show again
IkAdSdk.nativeController.clearFragmentAdState("details_screen")
IkAdSdk.nativeController.clearAllFragmentAdStates()
// Inspect: IkAdSdk.nativeController.shownFragmentAds  (HashSet<String>)
```

### Interstitial ad

All variants take an `InterstitialControllerListener`:

```kotlin
interface InterstitialControllerListener {
    fun onAdClosed()          // ALWAYS called — on dismiss, on failure, and when ads are gated off
    fun onAdLoaded()
    fun onSplashAdViewGone()
    fun onIapShow()           // user is premium
}
```

Put your navigation in `onAdClosed()` — it is the single continuation point.

```kotlin
// Counter-based: shows every Nth eligible call
IkAdSdk.interstitialController.showInterstitial(
    adIdString = "home_interstitial",
    activity = this,
    enable = true,
    interstitialControllerListener = listener
)

// Every click: shows whenever an ad is available
IkAdSdk.interstitialController.showInterstitialEveryClick(
    adIdString = "home_interstitial",
    activity = this,
    enableAds = true,
    interstitialControllerListener = listener
)

// Back press / conditional click: pass false to skip the ad entirely
IkAdSdk.interstitialController.showInterstitialClickAndBack(
    isBackPressAdShow = shouldShowBackAd,
    adIdString = "exit_interstitial",
    activity = this,
    enable = true,
    interstitialControllerListener = listener
)

// Preload (requires AppCompatActivity)
IkAdSdk.interstitialController.preLoadAd(
    adIdReferenceName = "home_interstitial",
    mContext = this
)

// Housekeeping
IkAdSdk.interstitialController.resetInterstitialCounter()
IkAdSdk.interstitialController.hasAd()
IkAdSdk.interstitialController.isExitAppCall()      // mark the next show as an exit-flow ad
IkAdSdk.interstitialController.removeCallBacks()
IkAdSdk.interstitialController.removeCallBacksInstant()
IkAdSdk.interstitialController.resetSplash()
IkAdSdk.interstitialController.android15Support()   // immersive mode on API 35+
```

### Rewarded interstitial ad

```kotlin
val rewardedListener = object : RewardedInterstitialControllerListener {
    override fun onUserEarnedReward(rewardType: String, rewardAmount: Int) {
        unlockReward(rewardType, rewardAmount)
    }
    override fun onAdRewardGranted(boolean: Boolean) = Unit  // fired when the ad is dismissed
    override fun onAdClosed() = continueAfterAd()
    override fun onAdFailed() = continueAfterAd()
    override fun onAdLoaded() = Unit
    override fun onIapShow() = Unit
}

IkAdSdk.rewardedInterstitialController.showRewardedInterstitialEveryClick(
    adIdString = "rewarded_interstitial",
    activity = this,
    enableAds = true,
    interstitialControllerListener = rewardedListener
)

IkAdSdk.rewardedInterstitialController.showRewardedInterstitial(   // counter-based
    adIdString = "rewarded_interstitial",
    activity = this,
    enable = true,
    interstitialControllerListener = rewardedListener
)

IkAdSdk.rewardedInterstitialController.preLoadAd(                  // AppCompatActivity required
    adIdReferenceName = "rewarded_interstitial",
    mContext = this,
    interstitialControllerListener = rewardedListener
)

IkAdSdk.rewardedInterstitialController.hasAd()
IkAdSdk.rewardedInterstitialController.isRewardComplete   // true after a reward was granted
```

### App open ad

App open ads are driven by a `ProcessLifecycleOwner` observer: after `initOpenAd()`, every time the
app returns to the foreground the SDK shows a cached ad (ads older than **4 hours** are refetched).

```kotlin
IkAdSdk.openAppAdController.initOpenAd(
    context = applicationContext,
    adRef = "open_app",
    openAdEnable = true
)

// Before launching a runtime permission dialog (otherwise returning triggers an open ad)
IkAdSdk.openAppAdController.openAppAdDisableWhenPermissionCheck()

// Block on specific screens
AdKeys.canShowOpenAd = false

// Tear down completely (removes the lifecycle observer, drops the cached ad)
IkAdSdk.openAppAdController.releaseOpenAd()

IkAdSdk.openAppAdController.isAdAvailable   // cached and not expired
```

`OpenAdControllerListener` exists for diagnostics and exposes `onContextNotFound`, `onAdCalling`,
`onAdLoaded`, `onAdFailed`, `onAdInitFailed`, `onStartActivityException`, `onStartActivityFailed`
and `onAdPurchased`.

---

## Jetpack Compose usage

Two layers are available:

1. **Stateless composables** (`IkComposeBannerAd`, `IkComposeCollapsibleBannerAd`, `IkComposeNativeAd`)
   — thin `AndroidView` wrappers, ideal for single-screen apps.
2. **Ad slots backed by a ViewModel** (`NativeAdSlot`, `BannerAdSlot`) — the ad view is owned by the
   ViewModel, so navigating away and back re-shows the *same* loaded ad with no shimmer and no new
   request. Prefer this for navigation-heavy apps.

```kotlin
import com.monetization.ikadplugin.compose.IkComposeBannerAd
import com.monetization.ikadplugin.compose.IkComposeCollapsibleBannerAd
import com.monetization.ikadplugin.compose.IkComposeNativeAd
import com.monetization.ikadplugin.compose.rememberIkComposeInterstitialAdAssignContext
import com.monetization.ikadplugin.compose.rememberIkComposeRewardedInterstitialAdPreLoad
```

### Inline ads + full-screen controllers

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
        isRectangleBanner = false,
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

    Button(onClick = {
        interstitial.showInterstitialAdEveryClick(
            enable = true,
            onAdClosed = { continueAction() }
        )
    }) { Text("Continue") }

    Button(onClick = {
        rewarded.showRewardedInterstitialAdEveryClick(
            enable = true,
            onAdClosed = { continueAfterAd() },
            onAdFailed = { continueAfterAd() }
        )
    }) { Text("Reward") }
}
```

`IkComposeInterstitialController` exposes `preload()`, `showInterstitialCounter()`,
`showInterstitialClickOrBackCounter()`, `showInterstitialAdEveryClick()` and `hasAd()`.
Each show method takes optional `onAdClosed` / `onAdLoaded` / `onSplashAdViewGone` / `onIapShow`
lambdas instead of a listener object.

`IkComposeRewardedInterstitialController` exposes `preload()`, `showRewardedInterstitialAdCounter()`,
`showRewardedInterstitialAdEveryClick()` and `hasAd()`, with
`onAdClosed` / `onAdRewardGranted` / `onAdLoaded` / `onAdFailed` / `onIapShow` lambdas.
Three `remember…` factories exist for it:

| Factory | Behaviour on entering the composition |
| --- | --- |
| `rememberIkComposeRewardedInterstitialAdPreLoad` | Preloads when `preloadOnStart = true`; you show it manually. |
| `rememberIkComposeRewardedInterstitialAdCounter` | Immediately runs the counter show flow. |
| `rememberIkComposeRewardedInterstitialAdShowEveryClick` | Immediately runs the every-click show flow. |

### Ad slots that survive navigation

```kotlin
@Composable
fun HomeRoute(activity: Activity) {
    // One ViewModel per placement — sharing one between placements makes them fight over the frame.
    val nativeVm: NativeAdViewModel = viewModel(key = "home_native") {
        NativeAdViewModel(placementKey = "home_native")
    }
    val bannerVm: BannerAdViewModel = viewModel(key = "bottom_banner") {
        BannerAdViewModel(placementKey = "bottom_banner", collapsible = true)
    }

    NativeAdSlot(
        activity = activity,
        viewModel = nativeVm,
        enable = true,
        adViewType = 4,
        nativeCtaColorAdPosition = 0,
        loadNewAd = false
    )

    BannerAdSlot(
        activity = activity,
        viewModel = bannerVm,
        enable = true,
        isRectangleBanner = false,
        loadNewAd = false
    )
}
```

Behaviour worth knowing:

- The slot emits **nothing at all** while hidden, so it takes zero height (no empty gap).
- A loaded ad is reused as-is when you return to the screen; a slot that failed to fill retries.
- Recomposition alone never triggers a reload — only a change to the request inputs does.
- The ViewModel observes the purchase flow and hides itself the moment the user buys premium.
- `BannerAdViewModel.onCleared()` destroys the `AdView` (and the collapsible cache, when `collapsible = true`).

`AdSlotState` is a sealed interface — `Idle`, `Loading`, `Loaded`, `Hidden` — and the extension
`AdSlotState.isVisible` is `true` for `Loading` and `Loaded`. Collect `viewModel.state` if you want
to react to it yourself.

---

## Remove-ads purchases

Ads are hidden as soon as either of these is `true`:

```kotlin
AdSharedPreference.getInstance(context).appAdPurchased   // one-time purchase
AdSharedPreference.getInstance(context).isSubscription   // active subscription
// convenience: AdSharedPreference.getInstance(context).isAppPurchased  (either of the two)
```

They live in the `MONETIZATION_PREF` shared preferences file and are set by the billing helpers,
but they are writable if you need to restore state from your own backend.

### One-time remove-ads purchase

```kotlin
// 1. Configure the product key (once, at startup)
FetchConfig.assignOneTimeRemoveAdsConfig(OneTimePurchaseConfig(productKey = "remove_ads"))

// 2. Connect to Google Play Billing
IkAdSdk.oneTimePurchaseController.initBilling(applicationContext)

// 3. Observe the price
lifecycleScope.launch {
    IkAdSdk.oneTimePurchaseController.productPriceFlow.collect { model ->
        priceTextView.text = model.price      // PurchasePriceModel(price: String)
    }
}

// 4. Observe purchase completion
lifecycleScope.launch {
    IkAdSdk.oneTimePurchaseController.appPurchased.collect { purchased ->
        if (purchased) hideAllAdContainers()
    }
}

// 5. Launch the purchase flow
IkAdSdk.oneTimePurchaseController.purchaseProduct(this)

// Restore / re-check
IkAdSdk.oneTimePurchaseController.checkHistoryIfSkuNull(this)
IkAdSdk.oneTimePurchaseController.isBillingClientReady
```

Purchases are acknowledged automatically.

### Subscription remove-ads

```kotlin
// 1. Configure product IDs
FetchConfig.assignSubscriptionRemoveAdsConfig(
    SubscriptionConfig(
        sub_week_Key = "weekly_remove_ads",
        sub_monthly_Key = "monthly_remove_ads",
        sub_yearly_Key = "yearly_remove_ads"
    )
)

// 2. Connect + query (safe to call repeatedly)
IkAdSdk.subscriptionController.fetchProductsListIfNull(this)

// 3. Read products (SubscriptionModel.productsList is Map<productId, ProductDetails>)
lifecycleScope.launch {
    IkAdSdk.subscriptionController.productListFlow.collect { model ->
        val monthlyId = IkAdSdk.subscriptionController.getSelectedSubscriptionId(PLAN.MONTHLY)
        val monthly = model.productsList[monthlyId]
        monthlyPriceView.text = monthly?.subscriptionOfferDetails
            ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    }
}

// 4. Observe subscription state
lifecycleScope.launch {
    IkAdSdk.subscriptionController.appSubscribed.collect { subscribed ->
        if (subscribed) hideAllAdContainers()
    }
}

// 5. Buy, or upgrade/downgrade an existing plan
monthly?.let { IkAdSdk.subscriptionController.purchaseProduct(this, it) }
yearly?.let  { IkAdSdk.subscriptionController.changeSubscriptionPlan(this, it) }

// Extras
IkAdSdk.subscriptionController.isSubscriptionUpdateSupported
IkAdSdk.subscriptionController.isBillingClientReady
IkAdSdk.subscriptionController.viewUrlContent("https://your.app/privacy")  // opens a browser
```

`PLAN` is `WEEKLY | MONTHLY | YEARLY`; `getSelectedSubscriptionId(PLAN)` maps a plan back to the
product ID you registered.

---

## Native ad layout types and styling

Pass one of these as `adViewType`:

| Value | Layout resource | Typical use |
| --- | --- | --- |
| `0` | `split_native_layout` | Split/compact layout |
| `1` | `small_native_layout` | List rows, dense screens |
| `2` | `small_button_native_layout` | Small ad with a prominent CTA |
| `3` | `large_native_layout_top` | Large ad, media on top |
| `4` | `large_native_layout` | Standard large ad with media |
| `5` | `large_native_layout_exit` | Exit / back-press dialog |

Any other value falls back to `large_native_layout`.

Each layout binds these IDs: `bg_native`, `ad_view`, `ad_media`, `ad_app_icon`, `text_layout`,
`ads_text_ads` (the "Ad" badge), `ad_headline`, `ad_body`, `ad_call_to_action`. Overriding the layout
files in your app (same file names) is the simplest way to restyle, as long as the IDs stay.

### CTA gradient selection

With `everyNativeCtaColorChangeEnable = true`:

- `nativeCtaColorAdPosition = -1` → a random gradient from `everyNativeCtaColor`.
- `nativeCtaColorAdPosition >= 0` → `everyNativeCtaColor[position % everyNativeCtaColor.size]`,
  so a list of ads can cycle deterministically through the palette.

With `everyNativeCtaColorChangeEnable = false` and `nativeButtonThemeColorChange = true`, the CTA
uses the fixed `colorNativeCTR1` → `colorNativeCTR2` gradient. `nativeButtonRectangle` switches the
CTA between rounded and square corners.

Overridable color resources: `shimmer` (`#C2C2C2`), `ad_btn_color` (`#008AFD`), `black`, `white`.
Overridable styles: `AdLoadingDialog` (the loading dialog theme) and `AdTheme` (the full-screen ad
activity theme).

---

## Interstitial counter logic

`showInterstitial()` and `showRewardedInterstitial()` are counter-gated. The counter is global
(`FirebaseValue.allAppInterstitialAdCount`) and the threshold is
`allAppInterstitialAdCountChange`. `interstitialCounterStartSplash` picks between two algorithms:

**`interstitialCounterStartSplash = true` (splash mode)**

```
if (count >= threshold) { count = 0; show ad }
else                    { count++;  continue immediately }
```
The first `threshold` eligible actions are free, then an ad shows. Best when the splash already
showed an ad and you do not want a second one right away.

**`interstitialCounterStartSplash = false` (main mode)**

```
if (count == 0 || count >= threshold) { count = 1; show ad }
else                                  { count++;  continue immediately }
```
The **first** eligible action shows an ad, then one every `threshold` actions.

In both modes, when the counter fires but no ad is cached: with preload enabled the flow continues
(`onAdClosed()`) and a load starts in the background; with preload disabled the ad is loaded on
demand behind the loading dialog. Reset the counter any time with `resetInterstitialCounter()`.

---

## Firebase Analytics events

`AdClickDurationTracker.init(application)` registers lifecycle callbacks and logs the full funnel.
Events are only sent when Firebase Analytics is present in the host app; in debug builds each one
also shows a toast.

| Event | When |
| --- | --- |
| `ad_request_calling` | A request is about to be sent |
| `ad_request_Loaded` | The request filled |
| `ad_request_fail` | The request failed |
| `ad_request_duration_end` | Request round-trip finished |
| `ad_show` | An ad was displayed |
| `ad_click` | The user clicked an ad |
| `ad_click_return_duration` | The user came back to the app after a click (with duration) |
| `user_earn_rewarded_value` | A reward was granted |
| `ad_request_warning` | A recoverable problem (e.g. no activity to show on) |
| `ad_request_error` | An exception was caught |

Parameters: `ad_type`, `ad_id_ref`, `duration_millis`, `duration_seconds`, and for warnings/errors
`adType`, `stage`, `placement`, `message`, `throwable`. Click-return durations under 500 ms are
discarded as noise.

`AdType` values (`firebaseName` in parentheses): `INTERSTITIAL` (`interstitial`), `APP_OPEN`
(`app_open`), `BANNER` (`banner`), `NATIVE` (`native`), `REWARDED` (`rewarded`),
`REWARDED_INTERSTITIAL` (`rewarded_interstitial`).

Manual use, if you fire ads outside the SDK:

```kotlin
AdClickDurationTracker.startTracking(context, AdType.NATIVE, adIdReferenceName = "home_native")
AdClickDurationTracker.reset()
```

---

## Full API reference

### `IkAdSdk` (object) — entry point

| Member | Description |
| --- | --- |
| `setCurrentActivity(activity: Activity?)` | Stores a `WeakReference` used by app open ads. |
| `getCurrentActivity(): Activity?` | The current activity, or `null`. |
| `startSplash(context, consentVerifyID, callback)` | Gathers UMP consent + initialises MobileAds. |
| `initMobileSdk(context)` | Initialises MobileAds directly (off the main thread) when consent allows. |
| `isConsentCheck: Boolean` | `true` after consent was successfully gathered this process. |
| `nativeController` | `AdmobNativeAd` singleton. |
| `interstitialController` | `AdmobInterstitialAd` singleton. |
| `rewardedInterstitialController` | `AdmobRewardedInterstitialAd` singleton. |
| `bannerController` | `AdmobBannerAd` singleton. |
| `collapseBannerController` | `AdmobCollapsibleBannerAd` singleton. |
| `openAppAdController` | `AdmobOpenAppAd` singleton. |
| `oneTimePurchaseController` | `ProductsPurchaseHelper` singleton. |
| `subscriptionController` | `SubscriptionHelper` singleton. |

### Configuration

| Class | Members |
| --- | --- |
| `FetchConfig` | `assignRemoteConfigValues(AdsConfig)`, `assignBuildConfig(Boolean)`, `initializeNetWorkId(NetworkIdConfig)`, `assignOneTimeRemoveAdsConfig(OneTimePurchaseConfig)`, `assignSubscriptionRemoveAdsConfig(SubscriptionConfig)`, `getInterstitialId/getRewardedInterstitialId/getNativeId/getBannerId/getOpenAppAdId(String)` |
| `AdsConfig` | 24 behaviour/styling fields — see the table above |
| `NetworkIdConfig` | `interstitialAds`, `rewardedInterstitialAds`, `nativeAdId`, `bannerAdId`, `openAppAdId` (all `Map<String, String>`) |
| `GradientColors` | `start: Int`, `end: Int` (ARGB) |
| `FirebaseValue` | Mutable live config singleton, plus `allAppInterstitialAdCount` and `IS_INTER_SHOWING` |
| `OneTimePurchaseConfig` | `productKey: String` |
| `SubscriptionConfig` | `sub_week_Key`, `sub_monthly_Key`, `sub_yearly_Key` |
| `SubscriptionConstant` | The resolved product ID globals + `isDebug` |

### Ad controllers

| Controller | Public methods |
| --- | --- |
| `AdmobBannerAd` | `loadBannerAd`, `populateBannerAd`, `loadAndShowBannerAd`, `hasBanner`, `hasBannerOrLoading` |
| `AdmobCollapsibleBannerAd` | `loadAd`, `populateAd`, `destroy`, `hasAd`, `hasAdOrLoading` |
| `AdmobNativeAd` | `preLoadNativeAd`, `populateNativeAd`, `loadAndShowNativeAd`, `populateNativeAdForFragment`, `loadAndShowNativeAdForFragment`, `clearFragmentAdState`, `clearAllFragmentAdStates`, `shownFragmentAds`, `onDestroyNativeAd`, `hasLargeAd`, `hasLargeAdOrLoading` |
| `AdmobInterstitialAd` | `initAdMobSplash`, `showSplashInterstitial`, `showInterstitial`, `showInterstitialEveryClick`, `showInterstitialClickAndBack`, `showInterstitialIfSplashFail`, `preLoadAd`, `resetInterstitialCounter`, `resetSplash`, `hasAd`, `pauseAd`, `resumeAd`, `onDestroy`, `removeCallBacks`, `removeCallBacksInstant`, `isExitAppCall`, `android15Support` |
| `AdmobRewardedInterstitialAd` | `showRewardedInterstitial`, `showRewardedInterstitialEveryClick`, `preLoadAd`, `hasAd`, `isExitAppCall`, `removeCallBacksInstant`, `android15Support`, `isRewardComplete` |
| `AdmobOpenAppAd` | `initOpenAd`, `releaseOpenAd`, `openAppAdDisableWhenPermissionCheck`, `isAdAvailable` |

### Support classes

| Class | Purpose |
| --- | --- |
| `GoogleMobileAdsConsentManager` | `getInstance(context)`, `gatherConsent(activity, listener)`, `showPrivacyOptionsForm(activity, listener)`, `canRequestAds`, `isPrivacyOptionsRequired` |
| `AdSharedPreference` | `getInstance(context)`, `appAdPurchased`, `isSubscription`, `isAppPurchased` |
| `InternetController` | `getInstance(context)`, `isInternetConnected`, `isVPNConnected`, `observeVPN(): Flow<Boolean>` |
| `AdClickDurationTracker` | `init(application)`, `startTracking(context, adType, adIdReferenceName)`, `reset()`, plus the `adRequest*` / `adShow` / `warn` / `error` loggers |
| `AdLoadingDialog` | `showAlertDialog()`, `dismissAlertDialog()`, `setBlackColor()` |
| `NativeAdLayouts` | `getNativeAdLayout(displayAdsLayoutPosition, context)` |
| `NativeViewPopulate` | `addLargeNativeView(...)`, `getAdSize(activity)` (adaptive banner size) |
| `NativeShimmerEffect` | `addShimmerLayout(...)` |

### Compose API

| Symbol | Kind |
| --- | --- |
| `IkComposeBannerAd(activity, adReference, enable, modifier, isRectangleBanner, loadNewAd)` | `@Composable` |
| `IkComposeCollapsibleBannerAd(activity, adReference, enable, modifier, loadNewAd)` | `@Composable` |
| `IkComposeNativeAd(context, adIdNativeReference, enable, modifier, adViewType, loadNewAd, nativeCtaColorAdPosition)` | `@Composable` |
| `rememberIkComposeInterstitialAdAssignContext(activity, adReference, enable)` | `@Composable` factory |
| `rememberIkComposeRewardedInterstitialAdPreLoad(...)` | `@Composable` factory |
| `rememberIkComposeRewardedInterstitialAdCounter(...)` | `@Composable` factory |
| `rememberIkComposeRewardedInterstitialAdShowEveryClick(...)` | `@Composable` factory |
| `IkComposeInterstitialController` | `@Stable` controller |
| `IkComposeRewardedInterstitialController` | `@Stable` controller |
| `NativeAdSlot(activity, viewModel, enable, modifier, adViewType, nativeCtaColorAdPosition, loadNewAd)` | `@Composable` |
| `BannerAdSlot(activity, viewModel, enable, modifier, isRectangleBanner, loadNewAd)` | `@Composable` |
| `NativeAdViewModel(placementKey)` | `ViewModel` |
| `BannerAdViewModel(placementKey, collapsible)` | `ViewModel` |
| `InlineAdViewModel` | shared base — `state: StateFlow<AdSlotState>`, `reset()` |
| `AdSlotState` | `Idle` / `Loading` / `Loaded` / `Hidden`, plus `isVisible` |

---

## ProGuard / R8

The library ships a `consumer-rules.pro`, so no extra rules are required for the SDK itself. The
Ads, UMP, Billing and Firebase SDKs ship their own consumer rules too.

If you obfuscate and load `AdsConfig` from JSON (Remote Config, your backend), keep your own model
classes, e.g.:

```proguard
-keep class com.monetization.ikadplugin.firebase_value_fetch.** { *; }
-keep class com.monetization.ikadplugin.ads_id.** { *; }
```

---

## Testing checklist

Use Google's test ad units during development — never your production IDs:

| Format | Test ad unit ID |
| --- | --- |
| App ID | `ca-app-pub-3940256099942544~3347511713` |
| Banner | `ca-app-pub-3940256099942544/6300978111` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |
| Rewarded interstitial | `ca-app-pub-3940256099942544/5354046379` |
| Native advanced | `ca-app-pub-3940256099942544/2247696110` |
| App open | `ca-app-pub-3940256099942544/9257395921` |

Then verify:

- [ ] `FetchConfig.assignBuildConfig(BuildConfig.DEBUG)` runs before `startSplash()`.
- [ ] Every ID map has a `"default"` entry.
- [ ] The UMP form appears in debug (forced EEA geography) and consent persists after restart.
- [ ] Airplane mode: inline containers collapse, full-screen flows continue via `onAdClosed()`.
- [ ] Buying remove-ads hides every placement immediately, and survives an app restart.
- [ ] App open ad does not appear after a runtime-permission dialog, or on splash/premium screens.
- [ ] Rotating and navigating back does not leak `AdView`s (`destroy()` / `onDestroy…` are wired).

---

## Troubleshooting

### Ads never show

- Confirm `IkAdSdk.startSplash()` completed before any ad call.
- Confirm `FetchConfig.initializeNetWorkId()` ran before loading placements.
- Make sure each ID map includes `"default"`.
- Check `AdsConfig.allAdsOffEnable` — when `true` every request is skipped.
- Check the per-call `enable` flag.
- Check purchase state — ads are hidden when purchased or subscribed.
- Confirm the device has a *validated* internet connection (`InternetController.isInternetConnected`).
- Use AdMob test IDs during development; new ad units can take hours to start filling.

### Consent blocks ad requests

- `GoogleMobileAdsConsentManager.canRequestAds` must be `true` before requests run.
- Call `FetchConfig.assignBuildConfig(BuildConfig.DEBUG)` before consent for debug UMP behaviour.
- Pass your hashed test device ID as `consentVerifyID` while testing.
- If you need to re-test the form, clear app data (UMP caches the choice).

### App open ads show where they should not

- Set `AdKeys.canShowOpenAd = false` for blocked screens (do it in your `Application` callbacks).
- Call `openAppAdDisableWhenPermissionCheck()` before requesting runtime permissions.
- Keep splash, purchase and any full-screen-ad screens on the blocked list.

### Inline ads leave empty space

- The SDK sets the `LinearLayout` to `GONE` on failure; keep its height `wrap_content`.
- Do not reserve fixed-height empty ad containers.
- In Compose, prefer `NativeAdSlot` / `BannerAdSlot` — they emit nothing while hidden.

### Preloaded ads do not show immediately

- Enable the matching preload flag in `AdsConfig`.
- Call `preLoadAd()` early enough, and from an `AppCompatActivity`.
- Fill rate still depends on inventory, consent, network and ad unit health.

### Interstitial shows too often / too rarely

- Tune `allAppInterstitialAdCountChange` and re-read [Interstitial counter logic](#interstitial-counter-logic).
- `showInterstitialEveryClick()` ignores the counter entirely — switch to `showInterstitial()` if
  that is not what you want.

### Callback never fires

- `onAdClosed()` is the guaranteed continuation for every full-screen flow, including the gated-off
  and failure paths. Never put navigation only in `onAdLoaded()`.

---

## Release / publishing

The library module is configured for `maven-publish` with a single `release` variant:

```kotlin
group = "com.github.Muhammadimrankhan"
version = "0.1.25"
// artifactId = "Monetization_IkAdSdk"
```

To cut a release:

1. Bump `version` **and** the `publications` version in `admob_monetization/build.gradle.kts`.
2. Commit and tag with the same version (`git tag 0.1.26 && git push --tags`).
3. JitPack builds the tag on first request:
   `com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.26`.
4. Update the version references in this README.

Local artifact check:

```bash
./gradlew :admob_monetization:assembleRelease
./gradlew :admob_monetization:publishToMavenLocal
```
