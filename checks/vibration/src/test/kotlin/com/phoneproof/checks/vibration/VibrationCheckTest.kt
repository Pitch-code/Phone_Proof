package com.phoneproof.checks.vibration

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class VibrationCheckTest {

    private fun measured(
        resting: Double,
        active: Double,
        amplitude: Boolean = true,
    ) = VibrationTrace(
        attempt = VibrationAttempt.MEASURED,
        restingJerk = resting,
        activeJerk = active,
        requestedMillis = 600L,
        hasAmplitudeControl = amplitude,
    )

    // ------------------------------------------------------------------ measured, not asked

    @Test
    fun a_phone_that_visibly_shook_passes_without_anyone_being_asked() {
        // A running motor typically produces ten to fifty times the jerk of a still phone.
        val result = VibrationCheck.evaluate(measured(resting = 0.04, active = 1.8))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.headline).contains("Nobody had to be asked")
    }

    @Test
    fun the_pass_says_how_much_more_it_moved() {
        // The number is the point. "It vibrated" is an opinion; "45 times more movement than at rest" is a
        // measurement a buyer can put in front of a seller.
        val result = VibrationCheck.evaluate(measured(resting = 0.04, active = 1.8))

        assertThat(result.headline).contains("times")
        assertThat(result.measurements.map { it.label })
            .containsAtLeast("Movement while still", "Movement while buzzing", "Times stronger")
    }

    @Test
    fun a_motor_that_never_moved_is_a_caution_and_names_the_surface_first() {
        val result = VibrationCheck.evaluate(measured(resting = 0.05, active = 0.06))

        // Never a bare failure: the app cannot see whether the phone was on a table or a folded coat, which
        // is the same reason the camera check will not fail a lens with a finger over it.
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.falsePositiveCauses.first()).contains("coat")
        assertThat(result.action).contains("hard table")
    }

    @Test
    fun the_consequence_is_about_missed_calls_rather_than_a_missing_buzz() {
        val result = VibrationCheck.evaluate(measured(resting = 0.05, active = 0.06))

        assertThat(result.consequence).contains("pocket")
        assertThat(result.consequence).contains("alarm")
    }

    // ------------------------------------------------------------------ the two thresholds

    @Test
    fun a_big_ratio_on_movement_too_small_to_feel_is_not_enough() {
        // A phone on a stone slab rests at almost zero, so the ratio is easy to satisfy — ten times
        // almost-nothing is still almost-nothing. Without the absolute floor this would pass a motor nobody
        // could feel.
        //
        // The first version of this test used 0.05 m/s² and did not demonstrate anything, because the ratio
        // floor had already capped the divisor and the ratio came out at 2.5. The test failed and was right
        // to: the numbers have to clear one threshold and miss the other for the case to mean what it says.
        val trace = measured(resting = 0.005, active = 0.2)

        assertThat(VibrationCheck.ratio(trace)).isGreaterThan(VibrationCheck.SHAKE_RATIO)
        assertThat(trace.activeJerk).isLessThan(VibrationCheck.MINIMUM_ACTIVE_JERK)
        assertThat(VibrationCheck.evaluate(trace).outcome).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun strong_absolute_movement_that_is_barely_above_a_restless_baseline_is_not_enough_either() {
        // Both tests have to pass. Here the phone moved a lot in absolute terms and was already moving
        // nearly that much before the motor started, so the motor has proved nothing.
        val trace = measured(resting = 0.5, active = 1.0)

        assertThat(trace.activeJerk).isGreaterThan(VibrationCheck.MINIMUM_ACTIVE_JERK)
        assertThat(VibrationCheck.ratio(trace)).isLessThan(VibrationCheck.SHAKE_RATIO)
        assertThat(VibrationCheck.evaluate(trace).outcome).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun a_phone_being_waved_about_cannot_be_measured_against() {
        val result = VibrationCheck.evaluate(measured(resting = 1.5, active = 6.0))

        // Comparing a motor against a baseline that already contains a moving bus proves nothing, however
        // favourable the ratio looks.
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("moving too much")
        assertThat(result.action).contains("Rest it on a table")
    }

    @Test
    fun the_restless_guard_runs_before_the_verdict_not_after() {
        // Deliberate ordering: a restless phone with a genuinely strong buzz still gets "cannot tell",
        // because the measurement was not made under conditions that mean anything.
        val trace = measured(resting = VibrationCheck.TOO_RESTLESS + 0.1, active = 20.0)

        assertThat(VibrationCheck.evaluate(trace).outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    // ------------------------------------------------------------------ never got as far as measuring

    @Test
    fun a_phone_with_no_motor_is_a_fact_rather_than_a_fault() {
        val result = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.NO_MOTOR))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun a_refused_request_never_becomes_a_finding_about_the_motor() {
        val result = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.REFUSED))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.action).contains("Do Not Disturb")
    }

    @Test
    fun with_no_accelerometer_the_app_says_it_cannot_measure_rather_than_asking() {
        // The whole premise of this check is that it does not have to ask. When the instrument it relies on
        // is missing, it says so and points at the more serious finding instead of falling back to an
        // opinion.
        val result = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.NO_ACCELEROMETER))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("no working accelerometer")
        assertThat(result.action).contains("sensor test")
    }

    @Test
    fun no_movement_numbers_are_shown_when_nothing_was_measured() {
        // Showing "0.00 m/s²" for a phone that was never asked to vibrate would read as a dead motor.
        val labels = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.REFUSED))
            .measurements.map { it.label }

        assertThat(labels).doesNotContain("Movement while buzzing")
        assertThat(labels).contains("Strength control")
    }

    // ------------------------------------------------------------------ reporting

    @Test
    fun amplitude_control_is_reported_and_never_judged() {
        // Plenty of good phones only manage on and off. It is worth knowing and it is not a fault.
        val plain = VibrationCheck.evaluate(measured(0.04, 1.8, amplitude = false))

        assertThat(plain.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(plain.measurements.first { it.label == "Strength control" }.value)
            .isEqualTo("on or off only")
    }

    @Test
    fun numbers_do_not_change_shape_with_the_machines_locale() {
        val value = VibrationCheck.evaluate(measured(0.04, 1.8))
            .measurements.first { it.label == "Movement while buzzing" }.value

        assertThat(value).isEqualTo("1.80")
    }

    @Test
    fun every_outcome_tells_the_buyer_what_to_do_next() {
        VibrationAttempt.entries.forEach { attempt ->
            // Driven off the enum rather than a hand-written list, because the list went five entries
            // stale once before and a new attempt with no action would have slipped through.
            val trace = if (attempt == VibrationAttempt.MEASURED) {
                measured(resting = 0.05, active = 6.0)
            } else {
                VibrationTrace(attempt)
            }
            assertThat(VibrationCheck.evaluate(trace).action).isNotEmpty()
        }
    }

    // ------------------------------------------------------ the app's own bugs are not the phone's fault

    @Test
    fun a_missing_permission_is_reported_as_the_apps_fault_and_not_the_phones() {
        // This test exists because of a real bug. android.permission.VIBRATE was missing from the manifest,
        // so vibrate() threw SecurityException, the driver flattened it to false, and a working realme was
        // told "the phone would not let the app run the motor. Check Do Not Disturb is off". The app blamed
        // a stranger's handset for its own missing manifest line.
        val result = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.NOT_PERMITTED))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("fault in this app")
        assertThat(result.headline).contains("not in the phone")
    }

    @Test
    fun the_app_fault_message_never_sends_the_buyer_hunting_through_their_settings() {
        // The distinction that makes NOT_PERMITTED worth having as a separate state at all. Naming Do Not
        // Disturb here would imply the phone is misconfigured, which is the accusation being avoided.
        val result = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.NOT_PERMITTED))

        assertThat(result.action).doesNotContain("Do Not Disturb")
        assertThat(result.action).contains("nothing here counts against the phone")
    }

    @Test
    fun neither_kind_of_failure_ever_produces_a_verdict_against_the_motor() {
        // Both are silences. Only a real measurement may reach PASS or CAUTION.
        for (attempt in listOf(VibrationAttempt.REFUSED, VibrationAttempt.NOT_PERMITTED)) {
            val result = VibrationCheck.evaluate(VibrationTrace(attempt))

            assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
            assertThat(result.measurements.map { it.label })
                .doesNotContain("Movement while buzzing")
        }
    }

    @Test
    fun a_declined_request_still_mentions_do_not_disturb_because_that_one_may_be_the_phone() {
        // The counterpart to the test above: when the platform declines for its own reasons, Do Not Disturb
        // is a genuinely useful thing to point at. The two messages must not be merged back together.
        val declined = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.REFUSED))
        val appFault = VibrationCheck.evaluate(VibrationTrace(VibrationAttempt.NOT_PERMITTED))

        assertThat(declined.action).contains("Do Not Disturb")
        assertThat(declined.headline).isNotEqualTo(appFault.headline)
    }
}
