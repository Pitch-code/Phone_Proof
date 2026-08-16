package com.phoneproof.checks.sensors

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ties the meter the buyer watches to the verdict they get afterwards.
 *
 * The screen fills a bar as the phone is tilted. If the bar and the analysis used different thresholds,
 * the app could show a full bar and then report "could not tell" about the same gesture — which would
 * look like a bug and would be one. These tests are what make that impossible.
 */
class SensorGestureTest {

    @Test
    fun a_full_tilt_meter_always_produces_a_conclusive_motion_verdict() {
        val tilted = accelerometerTilted()
        assertThat(SensorGesture.tiltProgress(tilted.stats)).isEqualTo(1f)

        // The buyer saw a full bar, so the verdict must be an answer rather than a shrug.
        val findings = SensorLiveness.analyse(
            listOf(tilted, gyroscopeAtRest()),
            setOf(SensorKind.ACCELEROMETER, SensorKind.GYROSCOPE),
        )
        assertThat(findings.map { it.state }).doesNotContain(SensorState.NOT_EXERCISED)
    }

    @Test
    fun a_full_turn_meter_always_produces_a_conclusive_gyroscope_verdict() {
        val turning = gyroscopeTurning()
        assertThat(SensorGesture.turnProgress(turning.stats)).isEqualTo(1f)

        val findings = SensorLiveness.analyse(
            listOf(accelerometerLatched(), turning),
            setOf(SensorKind.ACCELEROMETER, SensorKind.GYROSCOPE),
        )
        // The gyroscope proved itself, and in doing so convicted the latched accelerometer.
        assertThat(findings.stateOf(SensorKind.GYROSCOPE)).isEqualTo(SensorState.ALIVE)
        assertThat(findings.stateOf(SensorKind.ACCELEROMETER)).isEqualTo(SensorState.STUCK)
    }

    @Test
    fun an_untouched_phone_leaves_both_meters_empty() {
        assertThat(SensorGesture.tiltProgress(accelerometerAtRest().stats)).isLessThan(0.05f)
        assertThat(SensorGesture.turnProgress(gyroscopeAtRest().stats)).isLessThan(0.05f)
    }

    @Test
    fun the_meters_never_leave_the_zero_to_one_range() {
        // A violent shake produces numbers far past the target, and a progress bar handed 4.7 draws
        // itself off the side of the screen.
        val violent = SensorTrace(
            kind = SensorKind.ACCELEROMETER,
            readings = listOf(SensorReading(-60f, 0f, 0f), SensorReading(60f, 0f, 0f)),
        )
        assertThat(SensorGesture.tiltProgress(violent.stats)).isEqualTo(1f)
        assertThat(SensorGesture.tiltProgress(silent(SensorKind.ACCELEROMETER).stats)).isEqualTo(0f)
    }

    @Test
    fun the_cover_indicators_match_what_the_analysis_will_conclude() {
        val proximity = proximityCovered()
        val light = lightCovered()

        assertThat(SensorGesture.proximityResponded(proximity.stats)).isTrue()
        assertThat(SensorGesture.lightWentDark(light.stats)).isTrue()

        val findings = SensorLiveness.analyse(
            listOf(accelerometerAtRest(), proximity, light),
            setOf(SensorKind.PROXIMITY, SensorKind.LIGHT),
        )
        assertThat(findings.map { it.state }.distinct()).containsExactly(SensorState.ALIVE)
    }

    @Test
    fun going_dark_is_a_higher_bar_than_merely_changing() {
        // The dim-room trace is enough to prove the light sensor works and not enough to prove a palm
        // was ever over it. Both statements have to stay true, or the screen would tell the buyer their
        // palm had registered while the verdict quietly disagreed.
        val dim = lightCoveredInADimRoom().stats

        assertThat(SensorGesture.lightResponded(dim)).isTrue()
        assertThat(SensorGesture.lightWentDark(dim)).isFalse()
    }
}
