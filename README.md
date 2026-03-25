A lightweight and flexible Android Ads SDK built to simplify AdMob + mediation integration, with support for remote config (Firebase), multiple ad placements, and lifecycle-aware ad handling.
📦 Installation
Step 1: Add JitPack Repository

Add this in your root settings.gradle:

dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

Step 2: Add Dependency

dependencies {
    implementation 'com.github.Muhammadimrankhan:Monetization_IkAdSdk:0.0.1_adapter'
}

Core Classes:

IkAdSdk → Main entry point for all ad operations

AdsConfig → Controls ad behavior via local or Firebase config

NetworkIdConfig → Stores all ad unit IDs (Interstitial, Native, Banner, Open App)

🎛️ Ads Configuration

You can control SDK behavior dynamically using Firebase Remote Config or default values.

Default Config Values

val adsConfig = AdsConfig(

    allAppInterstitialAdCountChange = 3,
    splashTime = 13,
    colorNativeCTR1 = "#E74625",
    colorNativeCTR2 = "#E74625",
    colorNativeBg = "#EDEDED",
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
    interstitialPreloadEnable = false,
    interstitialPreloadProgressEnable = false
)
Apply Firebase Remote Config

Call this after fetching values from Firebase:

FetchConfig.assignRemoteConfigValues(adsConfig)


📡 Ad Unit Configuration

Define all your ad placements in one place:

Admob app id initialize first in app gradle follows:

  buildTypes {
  
        release {
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
        }
        debug {
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-3940256099942544~3347511713"
        }
    }
val networkConfig = NetworkIdConfig(

    interstitialAds = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "splash" to "ca-app-pub-xxx/111",
        "home" to "ca-app-pub-xxx/222",
        "exit" to "ca-app-pub-xxx/333"
    ),

    nativeAdId = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "home_native" to "ca-app-pub-xxx/444",
        "details_native" to "ca-app-pub-xxx/555"
    ),

    bannerAdId = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "home_banner" to "ca-app-pub-xxx/666",
        "bottom_banner" to "ca-app-pub-xxx/777"
    ),

    openAppAdId = mapOf(
        "default" to "ca-app-pub-xxx/000",
        "open_app_id" to "ca-app-pub-xxx/666"
    )
)

Initialize it:

FetchConfig.initializeNetWorkId(networkConfig)

🏁 Initialization (Application Class)

Create a custom Application class:

class IkAdPluginAppClass : Application(), Application.ActivityLifecycleCallbacks {

    private var isAdsInitialized = false
    private var isOpenAdInitialized = false

    fun initOpenAd(adRef: String, enable: Boolean) {
        if (!isOpenAdInitialized) {
            isOpenAdInitialized = true
            IkAdSdk.openAppAdController.initOpenAd(this, adRef, enable)
        }
    }

    fun initFirst() {
        if (!isAdsInitialized) {
            isAdsInitialized = true
            registerActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        IkAdSdk.setCurrentActivity(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        IkAdSdk.setCurrentActivity(activity)
        checkActivity()
    }

    override fun onActivityResumed(activity: Activity) {
        IS_APP_PAUSE = false
        IkAdSdk.setCurrentActivity(activity)
        checkActivity()
    }

    override fun onActivityPaused(activity: Activity) {
        IS_APP_PAUSE = true
    }

    override fun onActivityDestroyed(activity: Activity) {
        IkAdSdk.setCurrentActivity(null)
        canShowOpenAd = true
        IS_APP_PAUSE = false
    }

    private fun checkActivity() {
        canShowOpenAd = IkAdSdk.getCurrentActivity() != MainActivity::class.java
    }

    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}


🧠 Key Features

✅ Centralized ad management

✅ Firebase Remote Config support

✅ Multiple ad placements

✅ Open App Ad lifecycle handling

✅ Memory-safe Activity tracking (via WeakReference)

✅ Easy integration with existing apps

⚠️ Best Practices

Always initialize SDK in Application class

Use default ad IDs as fallback

Avoid showing Open Ads on critical screens (e.g., Splash, MainActivity)

Keep Firebase values synced with SDK config
📌 Notes

Ensure AdMob_App_ID is added in App Gradle
Test using test ads before publishing
Handle GDPR/UMP consent if targeting EU users
