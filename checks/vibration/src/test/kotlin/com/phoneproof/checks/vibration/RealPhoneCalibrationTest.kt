package com.phoneproof.checks.vibration

import com.google.common.truth.Truth.assertThat
import com.phoneproof.checks.sensors.SensorReading
import com.phoneproof.core.model.CheckOutcome
import org.junit.Test

/**
 * The readings a real phone actually produced, pinned so this cannot regress again.
 *
 * Every threshold in this check was a guess until a handset reported these numbers. It was lying on a hard
 * desk, its motor ran for the full 700 ms, and its owner felt it clearly:
 *
 * ```
 * Movement while still     0.01 m/s²
 * Movement while buzzing   0.04 m/s²
 * Times stronger           2.07×
 * Motor run for            700 ms
 * ```
 *
 * The check demanded a ratio of 3 **and** 0.35 m/s² absolute — nine times what the phone produced — and
 * told its owner the motor "is a repair — worth 800 off". This class exists so that particular sentence can
 * never be shown to that phone again.
 */
class RealPhoneCalibrationTest {

    /** Exactly what the screen reported, rounded as the screen rounds it. */
    private val onAHardDesk = VibrationTrace(
        attempt = VibrationAttempt.MEASURED,
        restingJerk = 0.01,
        activeJerk = 0.04,
        requestedMillis = 700,
        hasAmplitudeControl = true,
    )

    @Test
    fun the_phone_that_was_wrongly_accused_now_passes() {
        val result = VibrationCheck.evaluate(onAHardDesk)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun the_reported_ratio_had_been_halved_by_the_divisor_floor() {
        // The subtler half of the bug, found only by reproducing the arithmetic.
        //
        // The phone reported 2.07x. It rested at 0.01 while RATIO_FLOOR clamped the divisor at 0.02, so the
        // clamp substituted a baseline twice as large as the real one: 0.0414 / 0.02 = 2.07, where the
        // honest figure was 0.0414 / 0.01 = about 4. A guard against dividing by almost-nothing had become
        // the thing deciding the verdict.
        //
        // With the floor below real sensor noise, the same phone now clears the bar with room to spare
        // rather than scraping it.
        val ratio = VibrationCheck.ratio(onAHardDesk)

        assertThat(ratio).isWithin(0.01).of(4.0)
        assertThat(ratio).isGreaterThan(VibrationCheck.SHAKE_RATIO * 1.5)
    }

    @Test
    fun the_absolute_floor_is_below_what_a_working_motor_on_a_desk_produces() {
        // The single worst threshold in the app: 0.35 against a real 0.04.
        assertThat(VibrationCheck.MINIMUM_ACTIVE_JERK).isLessThan(0.04)
    }

    @Test
    fun a_weak_but_present_reading_is_never_a_verdict_against_the_motor() {
        // The middle band. Anything the app cannot be confident about must say so, not guess at a fault:
        // this is the change that matters more than any threshold.
        val weak = onAHardDesk.copy(restingJerk = 0.01, activeJerk = 0.015)

        val result = VibrationCheck.evaluate(weak)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun only_a_phone_that_did_not_move_at_all_is_doubted() {
        val flat = onAHardDesk.copy(restingJerk = 0.01, activeJerk = 0.01)

        val result = VibrationCheck.evaluate(flat)

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("did not move at all")
    }

    @Test
    fun no_outcome_names_a_price_off_the_back_of_a_measurement() {
        // The old action said the motor "is a repair — worth 800 off", on the strength of a threshold
        // nobody had measured, on a screen where being wrong costs the seller money. Advice yes; a figure
        // derived from this number, no.
        val everyOutcome = listOf(
            onAHardDesk,
            onAHardDesk.copy(activeJerk = 0.016),
            onAHardDesk.copy(activeJerk = 0.01),
            onAHardDesk.copy(restingJerk = 1.5, activeJerk = 3.0),
        ).map { VibrationCheck.evaluate(it) }

        everyOutcome.forEach { result ->
            assertThat(result.action.orEmpty()).doesNotContain("800")
        }
    }

    @Test
    fun the_advice_no_longer_sends_the_buyer_back_to_the_desk_they_already_tried() {
        // The reported case exactly: the phone was already on a hard table, and the app's only suggestion
        // was to put it on a hard table. A heavy surface takes the movement into itself, so the hand is
        // the more useful second try.
        val flat = onAHardDesk.copy(activeJerk = 0.01)

        val result = VibrationCheck.evaluate(flat)

        assertThat(result.action).contains("hand")
        assertThat(result.action).doesNotContain("not your hand")
    }

    // ---------------------------------------------------------------- the statistic, not the thresholds

    @Test
    fun a_percentile_keeps_the_peak_a_mean_averages_away() {
        // Why the measurement changed as well as the numbers. An oscillation spends part of every swing
        // barely moving, and a mean counts those moments against the peak the motor actually reached.
        val oscillating = buildList {
            repeat(20) { index ->
                val swing = if (index % 2 == 0) 0.0f else 0.3f
                add(SensorReading(swing, 0f, 9.8f))
            }
        }

        val mean = meanJerk(oscillating)
        val percentile = jerkPercentile(oscillating)

        assertThat(percentile).isGreaterThan(mean)
    }

    @Test
    fun one_freak_sample_does_not_decide_the_verdict() {
        // Which is why it is a percentile and not a maximum. A single knock of the table, or one dropped
        // sample, would otherwise be enough to pass a dead motor.
        val stillWithOneBump = buildList {
            repeat(40) { add(SensorReading(0f, 0f, 9.8f)) }
            add(SensorReading(4f, 0f, 9.8f))
            repeat(40) { add(SensorReading(0f, 0f, 9.8f)) }
        }

        assertThat(jerkPercentile(stillWithOneBump)).isLessThan(0.01)
    }

    @Test
    fun the_same_statistic_is_used_for_both_halves_of_the_ratio() {
        // Taking a mean at rest and a peak while buzzing would inflate every ratio for free, which is
        // exactly the kind of accidental thumb on the scale this check cannot afford.
        val still = List(40) { SensorReading(0.002f * (it % 2), 0f, 9.8f) }

        assertThat(jerkPercentile(still)).isGreaterThan(0.0)
    }
}
