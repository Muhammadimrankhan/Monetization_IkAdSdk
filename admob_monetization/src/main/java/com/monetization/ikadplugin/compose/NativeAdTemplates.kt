package com.monetization.ikadplugin.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import com.google.android.gms.ads.nativead.NativeAd
import com.monetization.ikadplugin.ads.FirebaseValue
import com.monetization.ikadplugin.firebase_value_fetch.GradientColors

/*
 * Native ad templates, selected by `adViewType`:
 *   0 - small, large icon        1 - small (default for slots)    2 - single row, CTA on the right
 *   3 - large, CTA on top        4 - large, CTA at the bottom     5 - large, fills available height
 * Any other value falls back to 4.
 */

/** Renders [nativeAd] with the template for [adViewType], styled from [FirebaseValue]. */
@Composable
fun NativeAdTemplate(
    nativeAd: NativeAd,
    adViewType: Int,
    modifier: Modifier = Modifier,
    nativeCtaColorAdPosition: Int = -1,
) {
    val ctaBrush = remember(nativeAd, nativeCtaColorAdPosition) {
        ctaBrush(nativeCtaColorAdPosition)
    }
    val icon = remember(nativeAd) {
        nativeAd.icon?.drawable
            ?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
            ?.toBitmap()
            ?.asImageBitmap()
    }
    val parts = LoadedNativeAdParts(nativeAd, icon, ctaBrush)

    NativeAdContainer(adViewType, modifier) { containerModifier ->
        IkNativeAdView(nativeAd, containerModifier) {
            NativeAdLayout(adViewType, parts)
        }
    }
}

/** Animated placeholder with the same shape as the [adViewType] template. */
@Composable
fun NativeAdShimmer(
    adViewType: Int,
    modifier: Modifier = Modifier,
) {
    val shimmerOffset by rememberInfiniteTransition(label = "nativeShimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerOffset",
    )
    NativeAdContainer(adViewType, modifier) { containerModifier ->
        Box(
            containerModifier.drawWithContent {
                drawContent()
                val x = size.width * shimmerOffset
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent),
                        start = Offset(x - size.width / 3, 0f),
                        end = Offset(x, size.height),
                    )
                )
            }
        ) {
            NativeAdLayout(adViewType, ShimmerNativeAdParts)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Container and layouts
// ---------------------------------------------------------------------------------------------

@Composable
private fun NativeAdContainer(
    adViewType: Int,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val (shape, background, border) = containerStyle()
    val fill = if (adViewType == 5) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .then(fill)
                .clip(shape)
                .background(background, shape)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
        ) {
            content(if (adViewType == 5) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun containerStyle(): Triple<Shape, Color, BorderStroke?> {
    val custom = when {
        FirebaseValue.darkTheme -> FirebaseValue.colorNativeBgDarkTheme
        FirebaseValue.nativeAdsBgColorChange -> FirebaseValue.colorNativeBg
        else -> null
    }
    if (custom == null) return Triple(RoundedCornerShape(6.sdp), Color(0xFFE8E8E8), null)
    val shape = RoundedCornerShape(CornerSize(20f))
    val strokeWidth = with(LocalDensity.current) { 2f.toDp() }
    val stroke = BorderStroke(strokeWidth, parseColor(FirebaseValue.colorNativeBgBorderStokes, Color.Gray))
    return Triple(shape, parseColor(custom, Color(0xFFE8E8E8)), stroke)
}

@Composable
private fun NativeAdLayout(adViewType: Int, parts: NativeAdParts) {
    when (adViewType) {
        0 -> SmallLayout(parts, iconSize = 100.sdp, largeIcon = true)
        1 -> SmallLayout(parts, iconSize = 45.sdp, largeIcon = false)
        2 -> SingleRowLayout(parts)
        3 -> LargeLayout(parts, ctaOnTop = true, fillHeight = false)
        5 -> LargeLayout(parts, ctaOnTop = false, fillHeight = true)
        else -> LargeLayout(parts, ctaOnTop = false, fillHeight = false)
    }
}

/** Types 0 and 1: icon + text row, full-width CTA underneath. */
@Composable
private fun SmallLayout(parts: NativeAdParts, iconSize: Dp, largeIcon: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(5.sdp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth()) {
            parts.Icon(
                if (largeIcon) {
                    Modifier
                        .padding(start = 10.sdp, end = 10.sdp, bottom = 5.sdp)
                        .size(iconSize)
                } else {
                    Modifier.size(iconSize)
                }
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 5.sdp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    parts.Badge(
                        if (largeIcon) Modifier.padding(end = 5.dp)
                        else Modifier.padding(horizontal = 5.dp)
                    )
                    parts.Headline(Modifier.padding(start = 5.dp, end = 10.sdp), 11.ssp)
                }
                parts.Body(Modifier, 10.ssp)
            }
        }
        parts.CallToAction(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.sdp, vertical = 2.sdp),
            minHeight = 40.sdp,
            fontSize = 13.ssp,
        )
    }
}

/** Type 2: icon, badge, text and a compact CTA on one row. */
@Composable
private fun SingleRowLayout(parts: NativeAdParts) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(5.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.Icon(Modifier.size(40.sdp))
        parts.Badge(Modifier.padding(start = 5.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 5.sdp)
        ) {
            parts.Headline(Modifier.padding(start = 5.dp, end = 10.sdp), 11.ssp)
            parts.Body(Modifier, 10.ssp)
        }
        parts.CallToAction(
            Modifier
                .padding(5.dp)
                .size(width = 80.sdp, height = 35.sdp),
            minHeight = 0.dp,
            fontSize = 13.sp,
        )
    }
}

/** Types 3, 4 and 5: media fills the space left by the icon/text row and the CTA. */
@Composable
private fun LargeLayout(parts: NativeAdParts, ctaOnTop: Boolean, fillHeight: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(240.sdp))
            .padding(5.dp)
    ) {
        if (ctaOnTop) {
            parts.CallToAction(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 5.sdp, end = 5.sdp, top = 6.sdp, bottom = 3.sdp),
                minHeight = 40.sdp,
                fontSize = 14.ssp,
            )
            LargeInfoRow(parts, iconModifier = Modifier
                .padding(start = 10.sdp, top = 2.sdp)
                .size(40.dp)
                .padding(end = 5.dp, bottom = 5.dp))
            parts.Media(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(5.sdp)
            )
        } else {
            parts.Media(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 3.sdp, end = 3.sdp)
                    .padding(5.sdp)
            )
            LargeInfoRow(parts, iconModifier = Modifier
                .padding(start = 10.sdp)
                .size(45.sdp)
                .padding(end = 5.sdp, bottom = 5.sdp))
            parts.CallToAction(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.sdp, vertical = 2.sdp),
                minHeight = 40.sdp,
                fontSize = 14.ssp,
            )
        }
    }
}

@Composable
private fun LargeInfoRow(parts: NativeAdParts, iconModifier: Modifier) {
    Row(Modifier.fillMaxWidth()) {
        parts.Icon(iconModifier)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                parts.Badge(Modifier.padding(horizontal = 5.dp))
                parts.Headline(Modifier.padding(start = 5.dp), 11.ssp)
            }
            parts.Body(Modifier, 9.ssp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Parts: the same layouts render either a loaded ad or its shimmer placeholder
// ---------------------------------------------------------------------------------------------

private interface NativeAdParts {
    @Composable fun Icon(modifier: Modifier)
    @Composable fun Badge(modifier: Modifier)
    @Composable fun Headline(modifier: Modifier, fontSize: TextUnit)
    @Composable fun Body(modifier: Modifier, fontSize: TextUnit)
    @Composable fun CallToAction(modifier: Modifier, minHeight: Dp, fontSize: TextUnit)
    @Composable fun Media(modifier: Modifier)
}

private class LoadedNativeAdParts(
    private val nativeAd: NativeAd,
    private val icon: ImageBitmap?,
    private val ctaBrush: Brush,
) : NativeAdParts {

    private val textColor: Color
        get() = if (FirebaseValue.darkTheme) Color.White else Color.Black

    @Composable
    override fun Icon(modifier: Modifier) {
        val bitmap = icon ?: return
        NativeAssetView(NativeAsset.ICON, modifier) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }

    @Composable
    override fun Badge(modifier: Modifier) {
        val color = when {
            FirebaseValue.darkTheme -> Color.White
            FirebaseValue.nativeAdsAttributionColorChange ->
                parseColor(FirebaseValue.colorAdsAttribNative, Color.Black)
            else -> Color.Black
        }
        AdBadge(modifier, color)
    }

    @Composable
    override fun Headline(modifier: Modifier, fontSize: TextUnit) {
        val headline = nativeAd.headline ?: return
        NativeAssetView(NativeAsset.HEADLINE, modifier) {
            BasicText(
                text = headline,
                style = TextStyle(color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    override fun Body(modifier: Modifier, fontSize: TextUnit) {
        val body = nativeAd.body ?: return
        NativeAssetView(NativeAsset.BODY, modifier) {
            BasicText(
                text = body,
                style = TextStyle(color = textColor, fontSize = fontSize),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    override fun CallToAction(modifier: Modifier, minHeight: Dp, fontSize: TextUnit) {
        val callToAction = nativeAd.callToAction ?: return
        val shape = ctaShape()
        NativeAssetView(NativeAsset.CALL_TO_ACTION, modifier) {
            // Not clickable on purpose: the touch must reach the registered view so AdMob counts it.
            Box(
                Modifier
                    .fillMaxSize()
                    .heightIn(min = minHeight)
                    .background(ctaBrush, shape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = callToAction,
                    style = TextStyle(color = Color.White, fontSize = fontSize, textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    @Composable
    override fun Media(modifier: Modifier) {
        NativeMediaView(modifier)
    }
}

private object ShimmerNativeAdParts : NativeAdParts {
    private val shimmerColor = Color(0xFFC2C2C2)

    @Composable
    override fun Icon(modifier: Modifier) {
        Box(modifier.background(shimmerColor))
    }

    @Composable
    override fun Badge(modifier: Modifier) {
        AdBadge(modifier, Color.Black)
    }

    @Composable
    override fun Headline(modifier: Modifier, fontSize: TextUnit) {
        Box(
            modifier
                .fillMaxWidth()
                .height(14.sdp)
                .background(shimmerColor)
        )
    }

    @Composable
    override fun Body(modifier: Modifier, fontSize: TextUnit) {
        Box(
            modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(12.sdp)
                .background(shimmerColor)
        )
    }

    @Composable
    override fun CallToAction(modifier: Modifier, minHeight: Dp, fontSize: TextUnit) {
        val shape = if (FirebaseValue.nativeShimmerBtnColorChange) {
            RoundedCornerShape(5.sdp)
        } else {
            RoundedCornerShape(CornerSize(80f))
        }
        Box(
            modifier
                .heightIn(min = minHeight)
                .background(shimmerColor, shape)
        )
    }

    @Composable
    override fun Media(modifier: Modifier) {
        Box(modifier.background(shimmerColor))
    }
}

@Composable
private fun AdBadge(modifier: Modifier, color: Color) {
    BasicText(
        text = "AD",
        style = TextStyle(color = color, fontSize = 11.sp, textAlign = TextAlign.Center),
        modifier = modifier
            .width(25.sdp)
            .border(1.sdp, color, RoundedCornerShape(2.sdp))
            .padding(1.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// CTA styling
// ---------------------------------------------------------------------------------------------

private val DEFAULT_CTA_COLOR = Color(0xFF008AFD)

@Composable
private fun ctaShape(): Shape = when {
    FirebaseValue.everyNativeCtaColorChangeEnable || FirebaseValue.nativeButtonThemeColorChange ->
        RoundedCornerShape(CornerSize(if (FirebaseValue.nativeButtonRectangle) 10f else 80f))
    else -> RoundedCornerShape(30.sdp)
}

/**
 * Per-ad CTA gradient: a pick from [FirebaseValue.everyNativeCtaColorList] (by position, or random
 * when the position is negative), else the theme gradient, else the default blue.
 */
private fun ctaBrush(nativeCtaColorAdPosition: Int): Brush {
    if (FirebaseValue.everyNativeCtaColorChangeEnable) {
        val list = FirebaseValue.everyNativeCtaColorList
        val gradient: GradientColors? = when {
            list.isEmpty() -> null
            nativeCtaColorAdPosition >= 0 -> list[nativeCtaColorAdPosition % list.size]
            else -> list.randomOrNull()
        }
        if (gradient != null) {
            return Brush.horizontalGradient(listOf(Color(gradient.start), Color(gradient.end)))
        }
        return SolidColor(DEFAULT_CTA_COLOR)
    }
    if (FirebaseValue.nativeButtonThemeColorChange) {
        return Brush.horizontalGradient(
            listOf(
                parseColor(FirebaseValue.colorNativeCTR1, DEFAULT_CTA_COLOR),
                parseColor(FirebaseValue.colorNativeCTR2, DEFAULT_CTA_COLOR),
            )
        )
    }
    return SolidColor(DEFAULT_CTA_COLOR)
}

private val HEX_COLOR = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

/** Parses `#RRGGBB` / `#AARRGGBB`; anything else (empty, malformed remote value) gives [fallback]. */
private fun parseColor(hex: String, fallback: Color): Color {
    val value = hex.trim()
    return if (HEX_COLOR.matches(value)) Color(value.toColorInt()) else fallback
}
