package com.fliptofocus.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tiny helper for one-off haptic feedback. Every call is fully guarded so a device without a
 * vibrator (or a transient error) can never crash the caller. Requires the VIBRATE permission
 * (a normal, install-time permission).
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** A short single blip - used when the phone moves out of the correct position. */
    fun blip(context: Context) {
        runCatching {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** A satisfying double pulse - used when a challenge is completed. */
    fun success(context: Context) {
        runCatching {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 60L, 90L, 120L), -1))
        }
    }
}
