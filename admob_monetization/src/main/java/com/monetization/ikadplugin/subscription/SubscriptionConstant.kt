package com.monetization.ikadplugin.subscription
import com.monetization.ikadplugin.BuildConfig

object SubscriptionConstant {
    const val WEEKLY_SUBSCRIPTION_ID = "weekly_subscription"
    const val MONTHLY_SUBSCRIPTION_ID = "monthly_subscription"
    const val YEARLY_SUBSCRIPTION_ID = "yearly_subscription"

    var SUBSCRIBED_PRODUCT_ID = ""

    val PRODUCT_ID = if (!BuildConfig.DEBUG) {
        "RemoveAllAds"
    } else "android.test.purchased"
}