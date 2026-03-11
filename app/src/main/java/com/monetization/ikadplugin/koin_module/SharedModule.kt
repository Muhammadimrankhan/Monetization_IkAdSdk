package com.monetization.ikadplugin.koin_module

import android.content.Context
import android.net.ConnectivityManager
import com.monetization.ikadplugin.ads.banner.BannerAdController
import com.monetization.ikadplugin.consent_sdk.GoogleMobileAdsConsentManager
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.ads.interstitial_ads.InterstitialNewController
import com.monetization.ikadplugin.ads.native_ads.NativeAdController
import com.monetization.ikadplugin.ads.open_ap_ads.AppOpenManager
import com.monetization.ikadplugin.consent_sdk.SplashConsentVerify
import com.monetization.ikadplugin.one_time_purchase.ProductsPurchaseHelper
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.subscription.SubscriptionHelper
import org.koin.dsl.module

val sharedModule = module {
    factory {
        get<Context>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    single {
        GoogleMobileAdsConsentManager(get())
    }

    single {
        BannerAdController(get(), get(), get())
    }
    single {
        InternetController(get())
    }

    single {
        InterstitialNewController(get(),get(),get())
    }

    single {
        AppOpenManager(get(),get())
    }
    single {
        NativeAdController(get(), get(), get())
    }

    single {
        SubscriptionHelper(get(), get(), get())
    }

    single {
        ProductsPurchaseHelper(get(), get(), get())
    }

    single {
        AdSharedPreference(get())
    }

    single {
        SplashConsentVerify(get(), get(), get())
    }

}