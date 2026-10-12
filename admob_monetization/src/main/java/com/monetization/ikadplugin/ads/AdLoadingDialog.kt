package com.monetization.ikadplugin.ads

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Full-screen "Ad Loading..." overlay shown while a full-screen ad is fetched. The content is
 * Compose, so the host must be a [ComponentActivity] (it supplies the lifecycle / saved-state
 * owners Compose needs); for any other Activity no overlay is shown.
 */
class AdLoadingDialog(private val activity: Activity?) {
    private var dialog: Dialog? = null

    fun showAlertDialog() {
        try {
            val currentActivity = activity ?: return
            if (currentActivity.isFinishing || currentActivity.isDestroyed) return
            val dialog = dialog ?: return
            if (!dialog.isShowing) {
                dialog.show()
            }
        } catch (ignored: Exception) {
        }
    }

    fun dismissAlertDialog() {
        try {
            val currentActivity = activity ?: return
            if (currentActivity.isFinishing || currentActivity.isDestroyed) return
            val dialog = dialog ?: return
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        } catch (ignored: Exception) {
        }
    }

    fun setBlackColor() {
        try {
            dialog?.window?.setBackgroundDrawable(Color.BLACK.toDrawable())
        } catch (_: Exception) {
        }
    }

    init {
        try {
            val owner = activity as? ComponentActivity
            if (owner != null) {
                dialog = Dialog(owner).apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setCanceledOnTouchOutside(false)
                    setCancelable(false)
                    setContentView(ComposeView(owner).apply {
                        setContent { AdLoadingContent() }
                    })
                    window?.apply {
                        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                        setLayout(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT
                        )
                        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                        decorView.setViewTreeLifecycleOwner(owner)
                        decorView.setViewTreeViewModelStoreOwner(owner)
                        decorView.setViewTreeSavedStateRegistryOwner(owner)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun AdLoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingSpinner(Modifier.size(42.dp))
        Spacer(Modifier.height(24.dp))
        BasicText(
            text = "Ad Loading...",
            style = TextStyle(
                color = ComposeColor.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun LoadingSpinner(modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        drawArc(
            color = ComposeColor.White,
            startAngle = rotation,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
