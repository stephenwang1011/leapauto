package com.leapauto.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

object HapticFeedbackHelper {
    internal fun shouldUseVibratorFallback(vibratedByView: Boolean, systemHapticEnabled: Boolean): Boolean =
        !vibratedByView && systemHapticEnabled

    @Suppress("DEPRECATION")
    private fun isSystemHapticEnabled(context: Context): Boolean = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) != 0
    }.getOrDefault(true)

    /** 触发干净短促的座舱微动开关级机械点击触感 (约 10~15ms 清脆阻尼) */
    fun performMechanicalClick(view: View? = null, context: Context? = null) {
        var vibrated = false
        if (view != null) {
            val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            val feedbackConstant = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> HapticFeedbackConstants.CONTEXT_CLICK
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            vibrated = view.performHapticFeedback(feedbackConstant, flags)
        }
        if (context != null && shouldUseVibratorFallback(vibrated, isSystemHapticEnabled(context))) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
