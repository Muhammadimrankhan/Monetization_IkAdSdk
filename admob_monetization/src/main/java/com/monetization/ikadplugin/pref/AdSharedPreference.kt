package com.monetization.ikadplugin.pref

import android.content.Context
import android.content.SharedPreferences

class AdSharedPreference private constructor(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("MONETIZATION_PREF", Context.MODE_PRIVATE)
    private val preferencesEdit = preferences.edit()
    val isAppPurchased: Boolean
        get() = isSubscription || appAdPurchased

    var appAdPurchased: Boolean
        get() = preferences.getBoolean("appPurchased", false)
        set(value) = preferencesEdit.putBoolean("appPurchased", value).apply()

    var isSubscription: Boolean
        get() = preferences.getBoolean("isSubscription", false)
        set(value) = preferencesEdit.putBoolean("isSubscription", value).apply()
    companion object {
        @Volatile
        private var INSTANCE: AdSharedPreference? = null

        fun getInstance(context: Context): AdSharedPreference {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdSharedPreference(context).also { INSTANCE = it }
            }
        }
    }
}