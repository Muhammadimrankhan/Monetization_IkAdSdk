package com.monetization.ikadplugin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Screen-scaled sizes, matching the `sdp` / `ssp` units the ad templates were designed in: one unit
 * is 1dp on a 300dp-wide (smallest width) screen and grows proportionally on wider ones.
 */

private const val BASE_SMALLEST_WIDTH_DP = 300f

@Composable
@ReadOnlyComposable
private fun scale(): Float = LocalConfiguration.current.smallestScreenWidthDp / BASE_SMALLEST_WIDTH_DP

internal val Int.sdp: Dp
    @Composable @ReadOnlyComposable get() = (this * scale()).dp

internal val Int.ssp: TextUnit
    @Composable @ReadOnlyComposable get() = (this * scale()).sp
