package com.monetization.ikadplugin.ads

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.material.imageview.ShapeableImageView
import com.monetization.ikadplugin.R
import com.monetization.ikadplugin.ads.NativeAdLayouts.getNativeAdLayout
import com.monetization.ikadplugin.ads.shimmer_effect.ShimmerFrameLayout

object NativeShimmerEffect {

    fun addShimmerLayout(
        adFrame: LinearLayout, displayAdsLayoutPosition: Int, context: Context
    ) {
        val shimmerNewView = getNativeAdLayout(
            displayAdsLayoutPosition, context,
        )
        setShimmerColorOnView(displayAdsLayoutPosition, context, shimmerNewView)
        val shimmerContainer = LayoutInflater.from(context)
            .inflate(R.layout.shimmer_layout, null) as ShimmerFrameLayout
        try {
            shimmerContainer.parent?.let { parent ->
                (parent as ViewGroup).removeAllViews()
            }
        } catch (_: Exception) {
        }
        adFrame.visibility = View.VISIBLE
        try {
            adFrame.removeAllViews()
        } catch (_: Exception) {
        }
        shimmerContainer.addView(
            shimmerNewView
        )
        adFrame.addView(shimmerContainer)
    }

    private fun setShimmerColorOnView(
        adViewType: Int, context: Context, shimmerNewView: View
    ) {
        try {
            val shimmerColor = ContextCompat.getColor(context, R.color.shimmer)
            val adButton = shimmerNewView.findViewById<AppCompatButton>(R.id.ad_call_to_action)
            val icon = shimmerNewView.findViewById<ShapeableImageView>(R.id.ad_app_icon)
            val adText = shimmerNewView.findViewById<TextView>(R.id.ads_text_ads)
            val textLayout = shimmerNewView.findViewById<LinearLayout>(R.id.text_layout)
            if (adViewType >= 3) {
                val mediaView = shimmerNewView.findViewById<MediaView>(R.id.ad_media)
                mediaView.setBackgroundColor(shimmerColor)
            }
            if (!FirebaseValue.nativeShimmerBtnColorChange) {
                val color = ContextCompat.getColor(context, R.color.shimmer)
                val colors = intArrayOf(color, color)
                val gd = GradientDrawable()
                gd.orientation = GradientDrawable.Orientation.LEFT_RIGHT
                gd.colors = colors
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = 80f
                adButton.background = gd
            } else {
                adButton.background =
                    ContextCompat.getDrawable(context, R.drawable.shimmer_new_ads_btn)
            }
            icon.setBackgroundColor(shimmerColor)
            textLayout.setBackgroundColor(shimmerColor)
            adText.setBackgroundColor(shimmerColor)
        } catch (ignore: Exception) {
        }
    }
}