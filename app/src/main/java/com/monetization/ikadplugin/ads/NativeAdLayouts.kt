package com.monetization.ikadplugin.ads

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.monetization.ikadplugin.R

object NativeAdLayouts {
     fun getNativeAdLayout(
        displayAdsLayoutPosition: Int, context: Context
    ): View {
        return when (displayAdsLayoutPosition) {
            0 -> LayoutInflater.from(context).inflate(R.layout.split_native_layout, null)
            1 -> LayoutInflater.from(context).inflate(R.layout.small_native_layout, null)
            2 -> LayoutInflater.from(context).inflate(R.layout.small_button_native_layout, null)
            3 -> LayoutInflater.from(context).inflate(R.layout.large_native_layout_top, null)
            4 -> LayoutInflater.from(context).inflate(R.layout.large_native_layout, null)
            5 -> LayoutInflater.from(context).inflate(R.layout.large_native_layout_exit, null)
            else -> {
                LayoutInflater.from(context).inflate(R.layout.large_native_layout, null)
            }
        }
    }


}