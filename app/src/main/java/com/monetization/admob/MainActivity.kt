package com.monetization.admob

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.ads_id.NetworkIdConfig
import com.monetization.ikadplugin.firebase_value_fetch.AdsConfig
import com.monetization.ikadplugin.firebase_value_fetch.FetchConfig
import com.monetization.ikadplugin.firebase_value_fetch.GradientColors
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
       val adsConfig = AdsConfig(
           FirebaseValue.allAppInterstitialAdCountChange,
           FirebaseValue.SPLASH_TIME,
           FirebaseValue.colorNativeCTR1,
           FirebaseValue.colorNativeCTR2,
           FirebaseValue.colorNativeBg,
           FirebaseValue.colorNativeBgDarkTheme,
           FirebaseValue.colorNativeBgBorderStokes,
           FirebaseValue.colorAdsAttribNative,
           FirebaseValue.interstitialCounterStartSplash,
           FirebaseValue.nativeShimmerBtnColorChange,
           FirebaseValue.nativeButtonThemeColorChange,
           FirebaseValue.nativeAdsAttributionColorChange,
           FirebaseValue.nativeAdsBgColorChange,
           FirebaseValue.nativeButtonRectangle,
           FirebaseValue.ALL_ADS_OFF_ENABLE,
           FirebaseValue.SPLASH_INTERSTITIAL_CALL_ENABLE,
           FirebaseValue.PROGRESS_LOADING_OPEN_AP_ENABLE,
           FirebaseValue.INTERSTITIAL_PRE_LOAD_ENABLE,
           FirebaseValue.INTERSTITIAL_PRE_LOAD_PROGRESS_ENABLE,
           true,
           listOf(
               GradientColors("#FF00C6FF".toColorInt(), "#FF0072FF".toColorInt()),
               GradientColors("#FF7F00FF".toColorInt(), "#FFE100FF".toColorInt()),
               GradientColors("#FFFF512F".toColorInt(), "#FFDD2476".toColorInt()),
               GradientColors("#FF11998E".toColorInt(), "#FF38EF7D".toColorInt()),
               GradientColors("#FFFC4A1A".toColorInt(), "#FFF7B733".toColorInt()),
               GradientColors("#FF1D4350".toColorInt(), "#FFA43931".toColorInt()),
               GradientColors("#FF232526".toColorInt(), "#FF414345".toColorInt()),
               GradientColors("#FF56AB2F".toColorInt(), "#FFA8E063".toColorInt()),
               GradientColors("#FF614385".toColorInt(), "#FF516395".toColorInt()),
               GradientColors("#FF2193B0".toColorInt(), "#FF6DD5ED".toColorInt())
           ),
           false
       )

        FetchConfig.assignRemoteConfigValues(adsConfig)

        val networkConfig = NetworkIdConfig(
            interstitialAds = mapOf(
                "default" to "ca-app-pub-xxx/000",   // 🔥 fallback interstitial
                "splash" to "ca-app-pub-xxx/111",
                "home" to "ca-app-pub-xxx/222",
                "exit" to "ca-app-pub-xxx/333"
            ),

            nativeAdId = mapOf(
                "default" to "ca-app-pub-xxx/000",   // 🔥 fallback native
                "home_native" to "ca-app-pub-xxx/444", "details_native" to "ca-app-pub-xxx/555"
            ), bannerAdId = mapOf(
                "default" to "ca-app-pub-xxx/000",   // 🔥 fallback banner
                "home_banner" to "ca-app-pub-xxx/666", "bottom_banner" to "ca-app-pub-xxx/777"
            ), openAppAdId = mapOf(
                "default" to "ca-app-pub-xxx/000",   // 🔥 fallback open app ad
                "open_app_id" to "ca-app-pub-xxx/666",
            )
        )

        FetchConfig.initializeNetWorkId(networkConfig)
    }
}
