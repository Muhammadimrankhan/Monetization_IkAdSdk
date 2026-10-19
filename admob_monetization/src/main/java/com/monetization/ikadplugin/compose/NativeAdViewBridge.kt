package com.monetization.ikadplugin.compose

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/*
 * AdMob only counts clicks and impressions on views registered with a NativeAdView, so a Compose
 * native ad is a NativeAdView whose content is Compose, and each asset (headline, icon, CTA...) is
 * its own small ComposeView registered on it.
 */

private val LocalNativeAdView = staticCompositionLocalOf<NativeAdView?> { null }

internal enum class NativeAsset { HEADLINE, BODY, ICON, CALL_TO_ACTION }

/** Hosts [content] inside a [NativeAdView] bound to [nativeAd]. */
@Composable
internal fun IkNativeAdView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    val currentAd by rememberUpdatedState(nativeAd)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            NativeAdView(context).apply {
                id = View.generateViewId()
                val adView = this
                addView(
                    ComposeView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        // Set once: the inner composition reads the latest content / ad through the
                        // updated states above, so recomposing the slot does not rebuild the ad views.
                        setContent {
                            CompositionLocalProvider(LocalNativeAdView provides adView) {
                                currentContent()
                                // Effects run after the asset views above were created and registered,
                                // which setNativeAd() needs in order to wire up click and impression
                                // tracking.
                                val ad = currentAd
                                DisposableEffect(ad) {
                                    adView.setNativeAd(ad)
                                    onDispose { }
                                }
                            }
                        }
                    }
                )
            }
        },
    )
}

/** Renders [content] in a view registered on the enclosing [IkNativeAdView] as [asset]. */
@Composable
internal fun NativeAssetView(
    asset: NativeAsset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val adView = checkNotNull(LocalNativeAdView.current) { "NativeAssetView outside IkNativeAdView" }
    val currentContent by rememberUpdatedState(content)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ComposeView(context).apply {
                id = View.generateViewId()
                when (asset) {
                    NativeAsset.HEADLINE -> adView.headlineView = this
                    NativeAsset.BODY -> adView.bodyView = this
                    NativeAsset.ICON -> adView.iconView = this
                    NativeAsset.CALL_TO_ACTION -> adView.callToActionView = this
                }
            }
        },
        update = { view -> view.setContent { currentContent() } },
    )
}

/** The ad's main image / video, registered on the enclosing [IkNativeAdView]. */
@Composable
internal fun NativeMediaView(modifier: Modifier = Modifier) {
    val adView = checkNotNull(LocalNativeAdView.current) { "NativeMediaView outside IkNativeAdView" }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MediaView(context).apply {
                id = View.generateViewId()
                setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        if (child is ImageView) {
                            child.adjustViewBounds = true
                            child.scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    }

                    override fun onChildViewRemoved(parent: View?, child: View?) {}
                })
                adView.mediaView = this
            }
        },
    )
}
