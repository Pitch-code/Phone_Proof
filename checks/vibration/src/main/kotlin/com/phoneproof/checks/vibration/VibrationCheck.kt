package com.phoneproof.checks.vibration

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import java.util.Locale

/** Why the app could not get as far as measuring anything. */
enum class VibrationAttempt {
    /** The phone reports no vibration motor at all. */
    NO_MOTOR,

    /** The platform refused the request, so nothing was ever asked of the motor. */
    REFUSED,

    /**
     * The app itself was not allowed to run the motor, because it is missing the permission to do so.
     *
     * Separate from [REFUSED] because it is a bug in this app and the buyer must not be told to go
     * hunting through their settings for it. This exists because that is precisely what happened: the
     * manifest was missing `android.permission.VIBRATE` and a working phone was told to check Do Not
     * Disturb.
     */
    NOT_PERMITTED,

    /** No working accelerometer, so there is nothing to feel the phone with. */
    NO_ACCELEROMETER,

    /** The motor was asked to run and the accelerometer was watching. */
    MEASURED,
}

/**
 * What the accelerometer felt while the motor was supposed to be running.
 *
 * Both figures are the mean absolute change between consecutive accelerometer samples, in m/s². That is
 * chosen over the plain variation in magnitude because a vibrating phone barely changes how much
 * acceleration it feels overall — gravity dominates — while changing it very fast. Rate of change is the
 * signal; the size of the reading is not.
 */
data class VibrationTrace(
    val attempt: VibrationAttempt,
    /** Movement while the phone was meant to be sitting still. */
    val restingJerk: Double = 0.0,
    /** Movement while the motor was running. */
    val activeJerk: Double = 0.0,
    /** How long the motor was asked to run. */
    val requestedMillis: Long = 0L,
    /** Whether the phone can vary vibration strength, which is worth reporting and never judged. */
    val hasAmplitudeControl: Boolean = false,
)

/**
 * Did the vibration motor actually move?
 *
 * ## Measured, not asked
 *
 * `Vibrator.vibrate()` tells you nothing. It returns without complaint on a phone whose motor has been
 * disconnected for a year, because all it reports is that Android accepted the request — there is no API
 * anywhere that says the weight actually spun. Every phone-testing app therefore ends up asking the buyer
 * "did you feel that?", which on a handset being passed back and forth in a shop is a question people answer
 * wrongly and confidently.
 *
 * But the phone has an accelerometer, and a vibrating phone is a shaking phone. So the app can feel it: take
 * a quiet baseline, run the motor, and compare. That turns the one hardware test that is traditionally a
 * matter of opinion into a measurement, and it needs nothing from the buyer except that they hold still.
 *
 * ## Why rate of change rather than level
 *
 * A vibrating phone does not feel *more* acceleration on average — gravity is still gravity. It feels
 * acceleration that changes direction dozens of times a second. So the measurement is the mean absolute
 * difference between consecutive samples, which sits near zero on a phone at rest and jumps by an order of
 * magnitude the moment a motor spins.
 *
 * ## What it refuses to conclude
 *
 * A phone resting on a folded coat, or held loosely in a palm, absorbs most of the movement. So a negative
 * result here is never a bare failure: it is a caution that names the surface as the first suspect, exactly
 * as the camera check names a finger over the lens.
 */
object VibrationCheck {

    const val CHECK_ID: String = "hardware.vibration"

    private const val TITLE = "Vibration"

    /**
     * The motor has to produce this many times the resting movement to earn a pass.
     *
     * ## Calibrated against a real phone, having twice been wrong
     *
     * This was three, on the reasoning that "a running motor typically produces ten to fifty times the jerk
     * of a still phone". That guess was never measured. A working handset lying on a hard desk reported
     * **0.01 m/s² at rest and 0.04 while buzzing — a ratio of 2.07** — and was told its motor might need
     * replacing.
     *
     * Two, therefore, which the measured phone clears. The measurement itself also changed in the same
     * commit — a faster sampling rate, no silent tail in the window, and a percentile instead of a mean —
     * so the figures a phone reports now should be larger than that 2.07 and this bar more comfortable
     * still. Both changes are needed: a better measurement does not excuse a threshold nobody checked.
     */
    const val SHAKE_RATIO: Double = 2.0

    /**
     * And it has to clear this absolutely, in m/s² at the ninetieth percentile of change between samples.
     *
     * The ratio alone is not enough: a phone on a stone slab can rest near zero, and twice almost-nothing is
     * still almost-nothing. But this was 0.35, roughly **nine times** what the real phone above actually
     * produced, so it failed working hardware on its own.
     *
     * 0.02 is twice the resting noise that phone reported, which is the useful comparison — the floor exists
     * to clear the sensor's own noise, not to assert how hard a motor ought to shake a desk.
     */
    const val MINIMUM_ACTIVE_JERK: Double = 0.02

    /**
     * Below this ratio the phone did not measurably move at all, and only then may the motor be doubted.
     *
     * The gap between this and [SHAKE_RATIO] is deliberate and is the most important part of this check.
     * A reading in between means *the app could not tell* — not that the hardware is faulty. Before this
     * existed, everything short of a confident pass was reported as a probable fault, which is how a
     * working motor came to be described as "a repair — worth 800 off".
     */
    const val FLAT_RATIO: Double = 1.25

    /**
     * Above this much resting movement, the phone was not still enough to measure against.
     *
     * The buyer was holding it wrong, walking, or in a moving vehicle. Nothing can be concluded, and saying
     * so is far better than comparing a motor against a baseline that already contains a bus.
     */
    const val TOO_RESTLESS: Double = 0.8

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A phone resting on a coat, a cushion or a palm absorbs most of the movement.",
        "Do Not Disturb and some battery savers suppress vibration entirely.",
        "A phone gripped tightly damps the motor far more than one lying on a hard surface.",
        "A few phones use a soft haptic engine whose gentlest pattern is genuinely hard to feel.",
    )

    fun evaluate(trace: VibrationTrace): CheckResult {
        val measurements = buildList {
            if (trace.attempt == VibrationAttempt.MEASURED) {
                add(Measurement("Movement while still", format(trace.restingJerk), "m/s²"))
                add(Measurement("Movement while buzzing", format(trace.activeJerk), "m/s²"))
                add(Measurement("Times stronger", "${format(ratio(trace))}×"))
                add(Measurement("Motor run for", "${trace.requestedMillis}", "ms"))
            }
            add(
                Measurement(
                    "Strength control",
                    if (trace.hasAmplitudeControl) "yes" else "on or off only",
                ),
            )
        }

        when (trace.attempt) {
            VibrationAttempt.NO_MOTOR -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                // HIGH: the phone was asked what it has and it answered. That is a fact, not a failure.
                confidence = Confidence.HIGH,
                headline = "This phone reports no vibration motor, so there is nothing to test.",
                action = "Unusual for a phone. Worth checking that silent mode still gets your " +
                    "attention before you rely on it.",
                measurements = measurements,
            )

            VibrationAttempt.REFUSED -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "The phone would not let the app run the motor, so nothing was tested.",
                action = "Check Do Not Disturb is off and try again. Otherwise set a one-minute " +
                    "alarm and feel it for yourself.",
                measurements = measurements,
            )

            VibrationAttempt.NOT_PERMITTED -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                // Owns it. There is no version of this the buyer can fix, so sending them to a settings
                // screen would waste their time and quietly imply their phone is at fault.
                headline = "This test could not run because of a fault in this app, not in the phone.",
                action = "Nothing you can do from here, and nothing here counts against the phone. " +
                    "Update the app, and test vibration by setting a one-minute alarm.",
                measurements = measurements,
            )

            VibrationAttempt.NO_ACCELEROMETER -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                // Named plainly, because this test is only a measurement thanks to the accelerometer. With
                // no working one the app is reduced to asking, which is what it exists not to do.
                headline = "There is no working accelerometer to feel the phone with, so the motor " +
                    "could not be measured.",
                action = "Run the sensor test first. If the accelerometer is dead, that is the more " +
                    "serious finding of the two.",
                measurements = measurements,
            )

            VibrationAttempt.MEASURED -> Unit
        }

        if (trace.restingJerk > TOO_RESTLESS) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "The phone was moving too much to measure a buzz against.",
                action = "Rest it on a table, or hold it still in your palm, and run it again.",
                measurements = measurements,
            )
        }

        val measuredRatio = ratio(trace)
        val strongEnough = measuredRatio >= SHAKE_RATIO && trace.activeJerk >= MINIMUM_ACTIVE_JERK

        if (strongEnough) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                // Says how it knows. This is the one hardware test in the app that usually comes down to
                // opinion, and the whole point is that here it did not.
                headline = "The accelerometer felt the phone shake — ${format(measuredRatio)} times " +
                    "more movement than at rest. Nobody had to be asked.",
                measurements = measurements,
            )
        }

        // The middle band, and the reason this check no longer accuses working hardware.
        //
        // Something moved, but not by enough to be sure it was the motor rather than the desk, the sensor's
        // own noise, or a phone whose vibration is coupled into whatever it is lying on. "I could not tell"
        // is the truthful answer, and it is the one a buyer can act on by feeling the phone for themselves.
        if (measuredRatio >= FLAT_RATIO) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "The phone moved a little while the motor ran, but not enough to call it " +
                    "either way.",
                action = "Hold the phone loosely in your hand and run it again — a heavy table takes " +
                    "the movement into itself, so a phone lying flat on one can read almost still. " +
                    "Trust your fingers over this number.",
                measurements = measurements,
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            // CAUTION, never a bare failure. The app cannot see what the phone was resting on, and a
            // folded coat absorbs almost everything — the same reasoning that keeps the camera check
            // from failing a phone with a finger over the lens.
            outcome = CheckOutcome.CAUTION,
            confidence = Confidence.MEDIUM,
            headline = "Android accepted the vibration and the phone did not move at all.",
            consequence = "A dead motor means silent mode stops getting your attention: you will " +
                "miss calls with the phone in your pocket, and every alarm becomes a sound " +
                "everyone around you hears too.",
            // No price on it any more. The old wording named a figure off the back of a threshold nobody
            // had measured, on a screen where being wrong costs the seller money.
            action = "Hold it loosely in your hand and run it again, and put a finger on the back " +
                "while it does. If you feel nothing at all, treat the motor as a repair and get the " +
                "price down — but trust your fingers, not this number.",
            measurements = measurements,
            falsePositiveCauses = FALSE_POSITIVE_CAUSES,
        )
    }

    /**
     * How much more the phone moved while buzzing.
     *
     * The floor on the divisor is what stops a phone on a stone slab producing an enormous meaningless
     * number — and, since the absolute test runs alongside this, a huge ratio on tiny movement still fails.
     */
    fun ratio(trace: VibrationTrace): Double =
        trace.activeJerk / trace.restingJerk.coerceAtLeast(RATIO_FLOOR)

    /**
     * The smallest resting movement the ratio will divide by.
     *
     * This was 0.02, and it was doing real damage. The real phone rested at **0.01** — half the floor — so
     * the clamp replaced its true baseline with a number twice as large and reported 2.07× when the honest
     * figure was about 4×. A guard against dividing by almost-nothing had quietly become the thing deciding
     * the verdict.
     *
     * 0.005 sits below the resting noise a real accelerometer produces, so it guards the division without
     * touching any reading a phone actually reports.
     */
    private const val RATIO_FLOOR = 0.005

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
