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

    // ------------------------------------------------- a palm in a bright room, which used to fail

    private fun light(vararg lux: Float): TraceStats =
        SensorTrace(SensorKind.LIGHT, lux.map { SensorReading(it) }).stats

    @Test
    fun a_palm_in_a_lit_room_counts_as_dark_even_though_it_never_reaches_eight_lux() {
        // Measured on a real handset: roughly 300 lux, a palm laid over the earpiece, and the reading
        // bottomed out in the dozens rather than near zero — light leaks around a hand and the screen
        // itself is lit. The old absolute-only rule scored that as no hand at all, so the indicator was
        // impossible to satisfy and the phase ran its full 25 seconds every time.
        assertThat(SensorGesture.lightWentDark(light(300f, 300f, 25f, 25f, 298f))).isTrue()
        assertThat(SensorGesture.lightWentDark(light(600f, 600f, 60f, 61f, 590f))).isTrue()
    }

    @Test
    fun a_room_that_was_always_dark_is_not_mistaken_for_a_hand() {
        // Both routes insist on a real drop, so an unchanging reading proves nothing however low it is.
        // Without that, a phone in a drawer would testify that a palm had covered it, and could then be
        // used to call a working proximity sensor dead.
        assertThat(SensorGesture.lightWentDark(light(0f, 0f, 0f))).isFalse()
        assertThat(SensorGesture.lightWentDark(light(3f, 3f, 4f, 3f))).isFalse()
    }

    @Test
    fun a_flickering_room_is_not_mistaken_for_a_hand() {
        // The reason the fractional route also demands absolute lux. Fluorescent tubes and people
        // walking past swing a low reading by large proportions, and on proportion alone that flicker
        // would have been enough to call a perfectly good proximity sensor dead.
        assertThat(SensorGesture.lightWentDark(light(40f, 20f, 38f, 22f))).isFalse()
        assertThat(SensorGesture.lightWentDark(light(100f, 45f, 98f, 50f))).isFalse()
    }

    @Test
    fun the_light_indicator_is_never_stricter_than_the_light_verdict() {
        // The incoherence this fix removes, stated as a property over a spread of shop conditions: the
        // tick the buyer is asked to satisfy tracks lightResponded, so it must never demand more than
        // the verdict does. A tick that cannot be earned by a sensor the app calls fine reads as a bug.
        val conditions = listOf(
            light(300f, 25f, 300f),
            light(14f, 3f),
            light(600f, 60f, 590f),
            light(40f, 20f, 38f),
            light(0f, 0f),
            light(320f, 4f, 318f),
        )

        conditions.forEach { stats ->
            if (SensorGesture.lightWentDark(stats)) {
                assertThat(SensorGesture.lightResponded(stats)).isTrue()
            }
        }
    }
}
