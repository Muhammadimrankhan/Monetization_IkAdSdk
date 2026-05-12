package com.monetization.ikadplugin.ads

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.WindowManager
import com.monetization.ikadplugin.R
import androidx.core.graphics.drawable.toDrawable

class AdLoadingDialog(private val activity: Activity?) {
    private var alertDialog1: AlertDialog? = null
    fun showAlertDialog() {
        try {
            val currentActivity = activity ?: return
            if (currentActivity.isFinishing || currentActivity.isDestroyed) return
            val dialog = alertDialog1 ?: return
            if (!dialog.isShowing) {
                dialog.show()
            }
        } catch (ignored: Exception) {
        }
    }

//    fun dismissAlertDialog(activity: Activity?) {
//        try {
//            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
//                if (alertDialog1!!.isShowing) {
//                    alertDialog1?.dismiss()
//                }
//            }
//        } catch (ignored: Exception) {
//        }
//    }
    fun dismissAlertDialog() {
        try {
            val currentActivity = activity ?: return
            if (currentActivity.isFinishing || currentActivity.isDestroyed) return
            val dialog = alertDialog1 ?: return
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        } catch (ignored: Exception) {
        }
    }

    fun setBlackColor() {
        try {
            alertDialog1?.window?.setBackgroundDrawable(Color.BLACK.toDrawable())
        } catch (_: Exception) {

        }
    }

    init {
        try {
            val builder = AlertDialog.Builder(activity, R.style.AdLoadingDialog)
            val v1 = LayoutInflater.from(activity).inflate(R.layout.ad_loading_dialog, null)
            builder.setView(v1)
            alertDialog1 = builder.create().apply {
                setCanceledOnTouchOutside(false)
                setCancelable(false)
                window?.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }

        } catch (_: Exception) {
        }
    }
}