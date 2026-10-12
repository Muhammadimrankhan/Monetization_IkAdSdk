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
| **Current version** | `0.1.31` |
| **Maven coordinates** | `com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.31` |
| **Repository** | https://github.com/Muhammadimrankhan/Monetization_IkAdSdk |
| **min SDK / compile SDK** | 24 / 37 |
| **Java / Kotlin target** | Java 11 (JVM target 11) |
| **UI support** | Jetpack Compose only (no XML layouts / View containers) |

---

## Contents

- [How to use — quick start](#how-to-use--quick-start)
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
- [Full-screen ad controllers](#full-screen-ad-controllers)
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

## How to use — quick start

The whole integration is seven steps. Each step links to the detailed section further down.

### Step 1 — Add the dependency

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

App `build.gradle.kts` (your app must have Compose enabled):

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures { compose = true }
}

dependencies {
    implementation("com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.31")
}
```

### Step 2 — Set your AdMob app ID

```kotlin
android {
    buildTypes {
        debug {
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713" // test ID
        }
        release {
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
        }
    }
}
```

More: [AdMob app ID setup](#admob-app-id-setup).

### Step 3 — Register an `Application` class

It tracks the current activity (needed for app open ads) and starts analytics. Copy the class from
[Application class setup](#application-class-setup) and register it in your manifest:

```xml
<application android:name=".MyApplication" ... />
```

### Step 4 — Configure the SDK once (in your splash `ComponentActivity`)

```kotlin
FetchConfig.assignBuildConfig(BuildConfig.DEBUG)
FetchConfig.assignRemoteConfigValues(AdsConfig(/* see "Every AdsConfig field" */))
FetchConfig.initializeNetWorkId(
    NetworkIdConfig(
        interstitialAds = mapOf("default" to "ca-app-pub-xxx/000", "home_interstitial" to "ca-app-pub-xxx/111"),
        rewardedInterstitialAds = mapOf("default" to "ca-app-pub-xxx/000"),
        nativeAdId = mapOf("default" to "ca-app-pub-xxx/000", "home_native" to "ca-app-pub-xxx/222"),
        bannerAdId = mapOf("default" to "ca-app-pub-xxx/000", "home_banner" to "ca-app-pub-xxx/333"),
        openAppAdId = mapOf("default" to "ca-app-pub-xxx/000", "open_app" to "ca-app-pub-xxx/444"),
    )
)
```

The strings like `"home_native"` are **placement references** — you pick them, and pass the same
string when you show the ad. Every map needs a `"default"` entry. More:
[SDK configuration](#sdk-configuration).

### Step 5 — Get consent, then continue

```kotlin
IkAdSdk.startSplash(context = this, consentVerifyID = "YOUR_TEST_DEVICE_HASH") { canRequestAds ->
    (application as MyApplication).initOpenAd(adReference = "open_app", enable = true)
    openHomeScreen()
}
```

No ad is requested before consent. More: [Consent and splash startup](#consent-and-splash-startup).

### Step 6 — Show banner, collapsible banner and native ads in Compose

Drop the composable where the ad should appear. That's all — loading, shimmer and hiding are handled
for you.

```kotlin
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(Modifier.fillMaxSize()) {

                // Native ad — adViewType picks the template (0..5, see the layout table)
                IkComposeNativeAd(
                    context = this@HomeActivity,
                    adIdNativeReference = "home_native",
                    enable = true,
                    adViewType = 1,
                )

                Spacer(Modifier.weight(1f))

                // Adaptive banner (set isRectangleBanner = true for a 300x250 banner)
                IkComposeBannerAd(
                    activity = this@HomeActivity,
                    adReference = "home_banner",
                    enable = true,
                )

                // Or a collapsible bottom banner
                IkComposeCollapsibleBannerAd(
                    activity = this@HomeActivity,
                    adReference = "home_banner",
                    enable = true,
                )
            }
        }
    }
}
```

What the user sees:

| Moment | Shown on screen |
| --- | --- |
| Composable is called (first frame) | **Shimmer** shaped like the ad |
| Ad loaded | Shimmer is replaced by the **ad** |
| Ad failed / no fill / no answer within 20 s | Shimmer **disappears**, slot takes **zero height** |
| Ads can't be requested (ads off, premium, no consent, offline, `enable = false`) | **Nothing** — not even a shimmer |
| User returns to the screen | The **same** loaded ad again, no shimmer, no new request |

Pass `enable = false` (for example from a Remote Config flag) to switch one placement off.
More: [Jetpack Compose usage](#jetpack-compose-usage).

### Step 7 — Show full-screen ads on a click

```kotlin
@Composable
fun ContinueButton(activity: Activity, onNext: () -> Unit) {
    val interstitial = rememberIkComposeInterstitialAdAssignContext(
        activity = activity,
        adReference = "home_interstitial",
        enable = true,
    )
    Button(onClick = {
        interstitial.showInterstitialAdEveryClick(
            enable = true,
            onAdClosed = onNext,   // always called: after the ad, on failure, or when ads are off
        )
    }) { Text("Continue") }
}
```

Put your navigation in `onAdClosed` — it is called in every case, so your flow never gets stuck.
Rewarded and app open ads: [Full-screen ad controllers](#full-screen-ad-controllers).

### Optional — Remove-ads purchase

Once the user buys "remove ads" or subscribes, every ad slot hides itself automatically. See
[Remove-ads purchases](#remove-ads-purchases).

> A complete working example is the sample app: `app/src/main/java/com/monetization/admob/MainActivity.kt`.

---

## Feature overview

| Ad format | Controller | Highlights |
| --- | --- | --- |
| **Banner** | `IkComposeBannerAd` / `BannerAdSlot` | Adaptive anchored banner or `MEDIUM_RECTANGLE`, preload, Compose shimmer placeholder |
| **Collapsible banner** | `IkComposeCollapsibleBannerAd` / `BannerAdSlot` | AdMob `collapsible = "bottom"` extras, preload |
| **Native** | `IkComposeNativeAd` / `NativeAdSlot` | 6 built-in Compose templates, Compose shimmer, gradient CTA rotation, dark theme |
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
│       │   ├── AdLoadingDialog.kt        full-screen "ad is loading" dialog (Compose content)
│       │   ├── FirebaseValue.kt          live config values read by the ad classes
│       │   ├── banner/                   AdmobBannerAd (loads AdViews) + adaptive banner sizing
│       │   ├── collapse/AdmobCollapsibleBannerAd.kt
│       │   ├── interstitial_ads/         AdmobInterstitialAd + InterstitialControllerListener
│       │   ├── native_ads/AdmobNativeAd.kt   loads NativeAd objects (no views)
│       │   ├── open_ap_ads/              AdmobOpenAppAd + OpenAdControllerListener
│       │   └── rewarded_interstitial/    AdmobRewardedInterstitialAd + listener
│       ├── ads_duration_tracker/AdClickDurationTracker.kt
│       ├── ads_id/NetworkIdConfig.kt
│       ├── compose/                      composables, native templates + shimmer, ad ViewModels
│       ├── consent_sdk/GoogleMobileAdsConsentManager.kt
│       ├── firebase_value_fetch/         AdsConfig, FetchConfig, GradientColors, CallBack
│       ├── internetController/InternetController.kt
│       ├── network_instance/IkAdSdk.kt   ← main entry point
│       ├── one_time_purchase/ProductsPurchaseHelper.kt
│       ├── pref/                         AdSharedPreference, OneTimePurchaseConfig
│       └── subscription/                 SubscriptionHelper, PLAN, configs, constants
│
└── app/                                  ← sample Compose host app (configuration reference)
    └── src/main/java/com/monetization/admob/
        ├── IkAdPluginAppClass.kt         Application + ActivityLifecycleCallbacks example
        └── MainActivity.kt               AdsConfig + NetworkIdConfig wiring + Compose ad slots
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
| `androidx.compose.runtime` / `ui` / `foundation` | 1.12.1 |
| `androidx.activity:activity-compose` | 1.13.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 |
| `androidx.core:core-ktx` | 1.19.0 |

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
- A Jetpack Compose host: your screens are hosted by a `ComponentActivity` (or a subclass). The
  "Ad Loading..." overlay is drawn in Compose and needs a `ComponentActivity` as its owner.

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
    implementation("com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.31")
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

When the gate fails, inline ad slots render nothing (no shimmer, zero height) and full-screen
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

## Full-screen ad controllers

Interstitial, rewarded interstitial and app open ads draw their own full-screen UI, so they are
driven from plain Kotlin — call them from a click handler or a `LaunchedEffect`. For Compose-friendly
wrappers with lambda callbacks see
[Inline ads + full-screen controllers](#inline-ads--full-screen-controllers).

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

// Preload
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

IkAdSdk.rewardedInterstitialController.preLoadAd(
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

Inline ads are Compose-only: native ads are drawn by built-in Compose templates (wrapped in AdMob's
`NativeAdView` so clicks and impressions are counted), the loading placeholder is a Compose shimmer,
and banners host AdMob's `AdView` through `AndroidView`. No XML layout or `LinearLayout` container
is involved.

Two layers are available:

1. **One-call composables** (`IkComposeBannerAd`, `IkComposeCollapsibleBannerAd`, `IkComposeNativeAd`)
   — each creates a ViewModel for its placement in the current `ViewModelStoreOwner` (the Activity,
   or the navigation back-stack entry) and renders an ad slot.
2. **Ad slots with your own ViewModel** (`NativeAdSlot`, `BannerAdSlot`) — same rendering, but you
   create and scope the ViewModel yourself.

In both cases the loaded ad is owned by the ViewModel, so navigating away and back re-shows the
*same* ad with no shimmer and no new request.

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
    // One ViewModel per placement — sharing one between placements makes them share a single ad.
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

- The shimmer shows from the first frame, is replaced by the ad when it loads, and disappears if
  the ad fails, isn't filled, or AdMob doesn't answer within **20 seconds**.
- When ads can't be requested at all (gate fails), no shimmer is shown even briefly.
- The slot emits **nothing at all** while hidden, so it takes zero height (no empty gap).
- A rectangle banner (`isRectangleBanner = true`) gets a tall shimmer matching its 250dp size.
- A loaded ad is reused as-is when you return to the screen; a slot that failed to fill retries.
- Recomposition alone never triggers a reload — only a change to `enable` / `loadNewAd` (or
  `isRectangleBanner`) does. Changing `adViewType` or `nativeCtaColorAdPosition` just re-renders the
  same native ad with a different template.
- A native ad survives configuration changes; a banner `AdView` is tied to its Activity, so a new
  Activity instance loads a fresh banner.
- The ViewModel observes the purchase flow and hides itself the moment the user buys premium.
- `onCleared()` destroys the `NativeAd` / `AdView` (and the collapsible cache, when `collapsible = true`).

To render a `NativeAd` you loaded yourself, call `NativeAdTemplate(nativeAd, adViewType, modifier,
nativeCtaColorAdPosition)`; `NativeAdShimmer(adViewType, modifier)` draws the matching placeholder.

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

| Value | Compose template | Typical use |
| --- | --- | --- |
| `0` | Small, large (100sdp) icon, full-width CTA below | Compact card |
| `1` | Small, icon + text, full-width CTA below (default) | List rows, dense screens |
| `2` | Single row: icon, text, compact CTA on the right | Small ad with a prominent CTA |
| `3` | Large (240sdp), CTA on top, media below | Large ad, CTA first |
| `4` | Large (240sdp), media on top, CTA at the bottom | Standard large ad with media |
| `5` | Like `4` but fills the available height | Exit / back-press dialog |

Any other value falls back to `4`. The templates are drawn in Compose (`NativeAdTemplate`) and sized
in screen-scaled units, so they look the same as the previous layouts across screen widths.
Banners use the type-`2` shimmer as their loading placeholder.

### CTA gradient selection

With `everyNativeCtaColorChangeEnable = true`:

- `nativeCtaColorAdPosition = -1` → a random gradient from `everyNativeCtaColor`.
- `nativeCtaColorAdPosition >= 0` → `everyNativeCtaColor[position % everyNativeCtaColor.size]`,
  so a list of ads can cycle deterministically through the palette.

With `everyNativeCtaColorChangeEnable = false` and `nativeButtonThemeColorChange = true`, the CTA
uses the fixed `colorNativeCTR1` → `colorNativeCTR2` gradient. `nativeButtonRectangle` switches the
CTA between rounded and square corners.

Without either flag the CTA is the default blue (`#008AFD`). Container background, border and text
colours follow `darkTheme`, `nativeAdsBgColorChange`, `colorNativeBg*` and
`nativeAdsAttributionColorChange` / `colorAdsAttribNative`. The only remaining overridable style is
`AdTheme` (the full-screen ad activity theme).

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
| `AdmobBannerAd` | `loadBannerAd` (preload), `loadBanner(…, onLoaded: (AdView) -> Unit, onFailed)`, `hasBanner`, `hasBannerOrLoading` |
| `AdmobCollapsibleBannerAd` | `loadAd` (preload), `loadCollapsibleBanner(…, onLoaded: (AdView) -> Unit, onFailed)`, `destroy`, `hasAd`, `hasAdOrLoading` |
| `AdmobNativeAd` | `preLoadNativeAd`, `loadNativeAd(…, onLoaded: (NativeAd) -> Unit, onFailed)`, `onDestroyNativeAd`, `hasLargeAd`, `hasLargeAdOrLoading` |
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
| `AdLoadingDialog` | `showAlertDialog()`, `dismissAlertDialog()`, `setBlackColor()` (Compose content; needs a `ComponentActivity`) |

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
| `NativeAdTemplate(nativeAd, adViewType, modifier, nativeCtaColorAdPosition)` | `@Composable` |
| `NativeAdShimmer(adViewType, modifier)` | `@Composable` |
| `NativeAdViewModel(placementKey)` | `ViewModel` — `load(activity, enable, loadNewAd)` |
| `BannerAdViewModel(placementKey, collapsible)` | `ViewModel` — `load(activity, enable, isRectangleBanner, loadNewAd)` |
| `InlineAdViewModel<T>` | shared base — `state: StateFlow<AdSlotState>`, `reset()` |
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

- Ad slots emit nothing while hidden; don't wrap them in a fixed-height container.
- Use `Modifier` paddings on the slot itself rather than reserving space around it.

### Preloaded ads do not show immediately

- Enable the matching preload flag in `AdsConfig`.
- Call `preLoadAd()` early enough (e.g. on the previous screen).
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
version = "0.1.31"
// artifactId = "Monetization_IkAdSdk"
```

To cut a release:

1. Bump `version` **and** the `publications` version in `admob_monetization/build.gradle.kts`.
2. Commit and tag with the same version (`git tag 0.1.32 && git push --tags`).
3. JitPack builds the tag on first request:
   `com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.1.32`.
4. Update the version references in this README.

Local artifact check:

```bash
./gradlew :admob_monetization:assembleRelease
./gradlew :admob_monetization:publishToMavenLocal
```
