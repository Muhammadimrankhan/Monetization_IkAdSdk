package com.monetization.ikadplugin.consent_sdk


import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.monetization.ikadplugin.BuildConfig

class GoogleMobileAdsConsentManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: GoogleMobileAdsConsentManager? = null

        fun getInstance(context: Context): GoogleMobileAdsConsentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoogleMobileAdsConsentManager(context).also { INSTANCE = it }
            }
        }
    }

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /** Interface definition for a callback to be invoked when consent gathering is complete. */
    fun interface OnConsentGatheringCompleteListener {
        fun consentGatheringComplete(error: FormError?)
    }

    /** Helper variable to determine if the app can request ads. */
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    /** Helper variable to determine if the privacy options form is required. */
    val isPrivacyOptionsRequired: Boolean
        get() =
            consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Helper method to call the UMP SDK methods to request consent information and load/show a
     * consent form if necessary.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener
    ) {
        // For testing purposes, you can force a DebugGeography of EEA or NOT_EEA.
        val params: ConsentRequestParameters = if (BuildConfig.DEBUG) {
            val debugSettings =
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    // Check your logcat output for the hashed device ID e.g.
                    // "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("ABCDEF012345")" to use
                    // the debug functionality.
//                    .addTestDeviceHashedId("217F8F20478E478B6F0BC0637D36B078")
                    .addTestDeviceHashedId("ABCDEF012345")
                    .build()

            ConsentRequestParameters.Builder().setConsentDebugSettings(debugSettings).build()
        } else {
            ConsentRequestParameters.Builder().build()
        }
//        consentInformation.reset()
        // Requesting an update to consent information should be called on every app launch.
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    // Consent has been gathered.
                    onConsentGatheringCompleteListener.consentGatheringComplete(formError)
                }
            },
            { requestConsentError ->
                onConsentGatheringCompleteListener.consentGatheringComplete(requestConsentError)
            }
        )
    }

    /** Helper method to call the UMP SDK method to show the privacy options form. */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissedListener: OnConsentFormDismissedListener
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener)
    }


}