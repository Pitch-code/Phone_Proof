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
 * What came of asking the motor to run.
 *
 * Three values rather than a boolean because the difference between the last two decides who gets blamed.
 */
enum class BuzzResult {
    /** The platform took the request. Says nothing about whether the weight actually spun. */
    ACCEPTED,

    /** The app lacks `android.permission.VIBRATE`. A bug in this app, never a fault in the phone. */
    NOT_PERMITTED,

    /** The platform said no for some other reason, which may genuinely be about this handset. */
    DECLINED,
}

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
     * Asks the motor to run for [millis].
     *
     * ## Why the answer is three-valued and not a boolean
     *
     * It used to return `true`/`false`, and that cost a real bug. `android.permission.VIBRATE` was missing
     * from the manifest, so every call threw `SecurityException`; the failure was flattened into `false`, and
     * the check reported "the phone would not let the app run the motor — check Do Not Disturb is off". The
     * app blamed a perfectly good handset for its own missing manifest line, which is the exact failure this
     * whole product exists to avoid.
     *
     * So a refused permission is now its own answer. [BuzzResult.NOT_PERMITTED] means the app is broken and
     * must say so; [BuzzResult.DECLINED] means the platform said no for a reason that might be about the
     * phone. Collapsing the two is what made a bug in this file look like a fault in someone's phone.
     *
     * ## Why this asks as an alarm, and then stops asking politely
     *
     * The usage attached to a vibration decides whether the system honours it. Touch and haptic-feedback
     * usages respect the "vibrate on touch" setting, which a great many people switch off — so a test using
     * them would report a dead motor on a working phone whose owner disliked keyboard buzz. Alarm usage is
     * delivered regardless of Do Not Disturb and those preferences, and is defensible because this is a
     * diagnostic the buyer explicitly asked for, one short buzz long.
     *
     * If that is declined the plainer calls are tried in turn, because the goal is only to get the weight
     * spinning so the accelerometer has something to feel. Which call started the motor does not change what
     * the accelerometer measures, and some manufacturers are known to be selective about vibration
     * attributes. A `SecurityException` short-circuits the whole ladder: every rung needs the same
     * permission, so retrying would only produce three identical failures.
     */
    fun buzz(millis: Long): BuzzResult {
        val vibrator = vibrator ?: return BuzzResult.DECLINED
        val effect = runCatching {
            VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
        }.onFailure { Diagnostics.warn(TAG, "could not build a $millis ms effect", it) }.getOrNull()
            ?: return BuzzResult.DECLINED

        for ((name, request) in ladder(vibrator, effect, millis)) {
            val attempt = runCatching(request)
            if (attempt.isSuccess) {
                Diagnostics.info(TAG, "motor started via $name")
                return BuzzResult.ACCEPTED
            }
            val error = attempt.exceptionOrNull()
            if (error is SecurityException) {
                // Not the phone's fault, and it must never be reported as though it were.
                Diagnostics.error(TAG, "VIBRATE permission is missing — this is an app bug", error)
                return BuzzResult.NOT_PERMITTED
            }
            Diagnostics.warn(TAG, "$name was declined, trying the next", error)
        }
        return BuzzResult.DECLINED
    }

    /** Ways to start the motor, strongest guarantee first. */
    private fun ladder(
        vibrator: Vibrator,
        effect: VibrationEffect,
        millis: Long,
    ): List<Pair<String, () -> Unit>> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add("alarm usage" to {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            })
        } else {
            add("alarm audio attributes" to {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            })
        }
        // No attributes at all. Subject to the buyer's touch-vibration setting, but a buzz the
        // accelerometer can feel is worth more than a tidy refusal.
        add("plain effect" to { vibrator.vibrate(effect) })
        // The pre-API-26 call, kept because a handful of OEM builds still honour only this one.
        add("legacy duration" to {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        })
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
