package com.phoneproof.checks.sensors

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SensorLivenessTest {

    private fun analyse(vararg traces: SensorTrace, present: Set<SensorKind> = everySensor) =
        SensorLiveness.analyse(traces.toList(), present)

    // ------------------------------------------------------------------ the happy path

    @Test
    fun a_phone_that_was_tilted_turned_and_covered_passes_everything() {
        val findings = analyse(
            accelerometerTilted(),
            gyroscopeTurning(),
            magnetometerNormal(),
            proximityCovered(),
            lightCovered(),
        )

        assertThat(findings.map { it.state }.distinct()).containsExactly(SensorState.ALIVE)
    }

    @Test
    fun only_the_sensors_the_phone_has_are_reported_on() {
        val findings = analyse(
            accelerometerTilted(),
            gyroscopeTurning(),
            present = setOf(SensorKind.ACCELEROMETER, SensorKind.GYROSCOPE),
        )

        // Absence is the inventory check's job. Reporting a missing gyroscope here as well would put
        // the same fault in the report twice under two different headings.
        assertThat(findings.map { it.kind })
            .containsExactly(SensorKind.ACCELEROMETER, SensorKind.GYROSCOPE)
    }

    @Test
    fun findings_come_out_in_a_fixed_order_whatever_order_the_traces_arrived_in() {
        val findings = analyse(
            lightCovered(),
            gyroscopeTurning(),
            proximityCovered(),
            accelerometerTilted(),
            magnetometerNormal(),
        )

        assertThat(findings.map { it.kind }).containsExactly(
            SensorKind.ACCELEROMETER,
            SensorKind.GYROSCOPE,
            SensorKind.MAGNETOMETER,
            SensorKind.PROXIMITY,
            SensorKind.LIGHT,
        ).inOrder()
    }

    // ------------------------------------------------------------------ nothing happened

    @Test
    fun a_phone_nobody_touched_produces_no_accusations_at_all() {
        // The single most important test in this file. Every sensor here is in perfect working order
        // and the buyer simply put the phone down. Anything other than "cannot tell" is a lie that
        // costs a seller money.
        val findings = analyse(
            accelerometerAtRest(),
            gyroscopeAtRest(),
            magnetometerNormal(),
            proximityUnchanged(),
            lightUnchanged(),
        )

        assertThat(findings.filter { it.state != SensorState.ALIVE }.map { it.kind to it.state })
            .containsExactly(
                SensorKind.ACCELEROMETER to SensorState.NOT_EXERCISED,
                SensorKind.GYROSCOPE to SensorState.NOT_EXERCISED,
                SensorKind.PROXIMITY to SensorState.NOT_EXERCISED,
                SensorKind.LIGHT to SensorState.NOT_EXERCISED,
            )
    }

    @Test
    fun a_compass_still_reads_the_earth_even_on_a_phone_that_was_never_moved() {
        // It needs no gesture: the planet supplies the signal. So this one can still come back clean
        // from a run where the buyer did nothing.
        val findings = analyse(accelerometerAtRest(), gyroscopeAtRest(), magnetometerNormal())

        assertThat(findings.stateOf(SensorKind.MAGNETOMETER)).isEqualTo(SensorState.ALIVE)
    }

    @Test
    fun a_refused_subscription_is_never_reported_as_a_fault_in_the_phone() {
        val findings = analyse(
            accelerometerTilted(),
            gyroscopeTurning(),
            unsubscribed(SensorKind.PROXIMITY),
        )

        // Its own state, not merely "not exercised". The buyer did everything asked of them here, so a
        // line telling them to cover the phone again would be blaming them for our failure.
        assertThat(findings.stateOf(SensorKind.PROXIMITY)).isEqualTo(SensorState.UNAVAILABLE)
    }

    @Test
    fun a_sensor_the_phone_claims_but_never_sends_a_trace_for_is_also_unavailable() {
        val findings = analyse(accelerometerTilted(), gyroscopeTurning())

        // Nothing arrived about the compass at all, which is not the same as the compass being silent.
        assertThat(findings.stateOf(SensorKind.MAGNETOMETER)).isEqualTo(SensorState.UNAVAILABLE)
    }

    // ------------------------------------------------------------------ real faults

    @Test
    fun silence_from_a_sensor_that_accepted_the_connection_needs_no_witness() {
        val findings = analyse(
            accelerometerAtRest(),
            gyroscopeAtRest(),
            silent(SensorKind.PROXIMITY),
        )

        // Nothing was moved and nothing was covered, and it is still conclusive: three seconds of
        // silence from a subscription the system accepted is the sensor's own answer.
        assertThat(findings.stateOf(SensorKind.PROXIMITY)).isEqualTo(SensorState.SILENT)
    }

    @Test
    fun a_gyroscope_that_missed_a_turn_the_accelerometer_followed_is_dead() {
        val findings = analyse(accelerometerTilted(), gyroscopeAtRest())

        assertThat(findings.stateOf(SensorKind.GYROSCOPE)).isEqualTo(SensorState.DEAD)
    }

    @Test
    fun a_gyroscope_reporting_zeroes_while_the_phone_turned_is_dead() {
        val findings = analyse(accelerometerTilted(), allZeroes(SensorKind.GYROSCOPE))

        assertThat(findings.stateOf(SensorKind.GYROSCOPE)).isEqualTo(SensorState.STUCK)
    }

    @Test
    fun a_latched_accelerometer_is_caught_by_the_gyroscope() {
        // Neither sensor's own readings can tell this from a phone left on a table. The gyroscope
        // reporting a turn is the only thing that separates the two.
        val findings = analyse(accelerometerLatched(), gyroscopeTurning())

        assertThat(findings.stateOf(SensorKind.ACCELEROMETER)).isEqualTo(SensorState.STUCK)
    }

    @Test
    fun a_latched_accelerometer_on_a_phone_nobody_turned_is_only_unproven() {
        val findings = analyse(accelerometerLatched(), gyroscopeAtRest())

        assertThat(findings.stateOf(SensorKind.ACCELEROMETER))
            .isEqualTo(SensorState.NOT_EXERCISED)
    }

    @Test
    fun an_accelerometer_reporting_zeroes_is_condemned_without_anything_being_moved() {
        // Gravity does not switch off. Zero is not a reading a working accelerometer can produce
        // sitting on a table, so this needs no gesture and no witness.
        val findings = analyse(allZeroes(SensorKind.ACCELEROMETER), gyroscopeAtRest())

        assertThat(findings.stateOf(SensorKind.ACCELEROMETER)).isEqualTo(SensorState.SILENT)
    }

    @Test
    fun an_accelerometer_that_disagrees_with_gravity_is_flagged_however_still_the_phone_was() {
        val wrongGravity = SensorTrace(
            kind = SensorKind.ACCELEROMETER,
            readings = List(60) { SensorReading(x = 0f, y = 0f, z = 3.2f) },
        )
        val findings = analyse(wrongGravity, gyroscopeAtRest())

        assertThat(findings.stateOf(SensorKind.ACCELEROMETER))
            .isEqualTo(SensorState.IMPLAUSIBLE)
    }

    @Test
    fun a_broken_accelerometer_does_not_get_to_condemn_the_gyroscope() {
        // The witness has to be trustworthy before its testimony counts. A sensor reporting 3.2 m/s²
        // of gravity cannot be used to prove the phone was turned.
        val wrongGravity = SensorTrace(
            kind = SensorKind.ACCELEROMETER,
            readings = List(60) { i -> SensorReading(x = i * 0.5f, y = 0f, z = 3.2f) },
        )
        val findings = analyse(wrongGravity, gyroscopeAtRest())

        assertThat(findings.stateOf(SensorKind.GYROSCOPE)).isEqualTo(SensorState.NOT_EXERCISED)
    }

    // ------------------------------------------------------------------ the covered pair

    @Test
    fun proximity_that_missed_a_palm_the_light_sensor_saw_is_dead() {
        val findings = analyse(
            accelerometerAtRest(),
            proximityUnchanged(),
            lightCovered(),
        )

        assertThat(findings.stateOf(SensorKind.PROXIMITY)).isEqualTo(SensorState.DEAD)
    }

    @Test
    fun a_light_sensor_that_missed_a_palm_the_proximity_sensor_felt_is_dead() {
        val findings = analyse(
            accelerometerAtRest(),
            proximityCovered(),
            lightUnchanged(),
        )

        assertThat(findings.stateOf(SensorKind.LIGHT)).isEqualTo(SensorState.DEAD)
    }

    @Test
    fun a_dim_room_still_proves_the_light_sensor_alive_on_the_ratio_alone() {
        // 14 lux down to 3 is only an 11 lux drop. No absolute threshold could ever pass in a room
        // this dark, which is why the ratio rule exists.
        val findings = analyse(accelerometerAtRest(), lightCoveredInADimRoom())

        assertThat(findings.stateOf(SensorKind.LIGHT)).isEqualTo(SensorState.ALIVE)
    }

    @Test
    fun a_dim_room_is_not_enough_to_accuse_the_proximity_sensor() {
        // The light sensor is alive here, but 14 lux down to 3 does not prove a palm was ever there —
        // a shadow does that. So the proximity sensor is unproven rather than broken.
        val findings = analyse(
            accelerometerAtRest(),
            lightCoveredInADimRoom(),
            proximityUnchanged(),
        )

        assertThat(findings.stateOf(SensorKind.LIGHT)).isEqualTo(SensorState.ALIVE)
        assertThat(findings.stateOf(SensorKind.PROXIMITY)).isEqualTo(SensorState.NOT_EXERCISED)
    }

    @Test
    fun a_flickering_shop_light_does_not_get_to_condemn_the_proximity_sensor() {
        // Someone walked past a fluorescent tube and the reading doubled from 30 to 70 lux. That makes
        // the light sensor demonstrably alive and proves nothing whatsoever about a palm.
        val flicker = SensorTrace(
            kind = SensorKind.LIGHT,
            readings = List(30) { SensorReading(30f) } + List(30) { SensorReading(70f) },
        )
        val findings = analyse(accelerometerAtRest(), flicker, proximityUnchanged())

        assertThat(findings.stateOf(SensorKind.LIGHT)).isEqualTo(SensorState.ALIVE)
        assertThat(findings.stateOf(SensorKind.PROXIMITY)).isEqualTo(SensorState.NOT_EXERCISED)
    }

    @Test
    fun neither_half_of_the_pair_can_be_judged_without_the_other() {
        val findings = analyse(
            accelerometerAtRest(),
            proximityUnchanged(),
            present = setOf(SensorKind.ACCELEROMETER, SensorKind.PROXIMITY),
        )

        // No light sensor on this phone, so nothing can vouch for the palm.
        assertThat(findings.stateOf(SensorKind.PROXIMITY)).isEqualTo(SensorState.NOT_EXERCISED)
    }

    // ------------------------------------------------------------------ the compass

    @Test
    fun a_compass_reading_three_times_the_planet_is_a_caution_not_a_fault() {
        val findings = analyse(accelerometerTilted(), gyroscopeTurning(), magnetometerSwamped())

        assertThat(findings.stateOf(SensorKind.MAGNETOMETER))
            .isEqualTo(SensorState.IMPLAUSIBLE)
    }

    @Test
    fun a_compass_reporting_nothing_is_condemned() {
        val findings = analyse(accelerometerAtRest(), allZeroes(SensorKind.MAGNETOMETER))

        assertThat(findings.stateOf(SensorKind.MAGNETOMETER)).isEqualTo(SensorState.SILENT)
    }

    @Test
    fun a_compass_repeating_itself_on_a_still_phone_is_not_called_stuck() {
        // A field that is not moving relative to a phone that is not moving is allowed to read the
        // same twice. Calling that a fault would invent one out of a quiet room.
        val steady = SensorTrace(
            kind = SensorKind.MAGNETOMETER,
            readings = List(60) { SensorReading(x = 22f, y = -14f, z = 40f) },
        )
        val findings = analyse(accelerometerAtRest(), gyroscopeAtRest(), steady)

        assertThat(findings.stateOf(SensorKind.MAGNETOMETER)).isEqualTo(SensorState.ALIVE)
    }

    @Test
    fun a_compass_repeating_itself_while_the_phone_turned_is_stuck() {
        val steady = SensorTrace(
            kind = SensorKind.MAGNETOMETER,
            readings = List(60) { SensorReading(x = 22f, y = -14f, z = 40f) },
        )
        val findings = analyse(accelerometerTilted(), gyroscopeTurning(), steady)

        assertThat(findings.stateOf(SensorKind.MAGNETOMETER)).isEqualTo(SensorState.STUCK)
    }
}
