package com.phoneproof.core.device

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.phoneproof.core.diagnostics.Diagnostics

/**
 * Runs the vibration motor, and admits that doing so proves nothing.
 *
 * [buzz] returns whether the platform *accepted* the request. That is all any Android API will tell you:
 * there is nothing anywhere that reports whether the weight actually spun. A phone whose motor has been
 * disconnected for a year accepts every request without complaint.
 *
 * Which is why this class is only half of the test. The other half is the accelerometer, feeling whether the
 * phone moved.
 */
class VibrationDriver(private val context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.onFailure { Diagnostics.error(TAG, "no vibrator service", it) }.getOrNull()

    fun hasMotor(): Boolean = runCatching { vibrator?.hasVibrator() == true }.getOrDefault(false)

    /** Whether strength can be varied. Worth reporting, never judged — plenty of good phones cannot. */
    fun hasAmplitudeControl(): Boolean =
        runCatching { vibrator?.hasAmplitudeControl() == true }.getOrDefault(false)

    /**
     * Asks the motor to run for [millis], and reports whether the platform took the request.
     *
     * ## Why this asks as an alarm
     *
     * The usage attached to a vibration decides whether the system will honour it. Touch and haptic-feedback
     * usages respect the "vibrate on touch" setting, which a great many people switch off — so a test using
     * them would report a dead motor on a working phone whose owner disliked keyboard buzz. Alarm usage is
     * the one that is delivered regardless of Do Not Disturb and those preferences.
     *
     * Defensible because this is a diagnostic the buyer explicitly asked for, one short buzz long, and
     * because the alternative is a test that quietly fails on a settings screen the app never looked at.
     */
    fun buzz(millis: Long): Boolean {
        val vibrator = vibrator ?: return false
        return runCatching {
            val effect = VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            true
        }.onFailure { Diagnostics.warn(TAG, "vibrate($millis) refused", it) }.getOrDefault(false)
    }

    /** Stops early, so backing out of the screen does not leave a phone buzzing in someone's hand. */
    fun cancel() {
        runCatching { vibrator?.cancel() }
            .onFailure { Diagnostics.warn(TAG, "could not cancel vibration", it) }
    }

    private companion object {
        const val TAG = "VibrationDriver"
    }
}
