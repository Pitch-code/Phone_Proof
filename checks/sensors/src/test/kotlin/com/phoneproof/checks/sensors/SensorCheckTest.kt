package com.phoneproof.checks.sensors

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

/** The policy layer: which findings are allowed to become an accusation, and how strong. */
class SensorCheckTest {

    private fun resultFor(kind: SensorKind, state: SensorState) = SensorCheck
        .results(listOf(SensorFinding(kind, state, TraceStats.of(List(90) { SensorReading(1f) }))))
        .single()

    @Test
    fun ids_sit_under_the_hardware_heading_so_they_get_the_right_chip() {
        // CheckCategory reads the namespace off the front of the id. A different prefix would file a
        // dead gyroscope under the wrong heading in the report.
        SensorKind.entries.forEach {
            assertThat(SensorCheck.idFor(it)).startsWith("hardware.sensor.")
        }
        assertThat(SensorCheck.idFor(SensorKind.GYROSCOPE))
            .isEqualTo("hardware.sensor.gyroscope")
    }

    @Test
    fun every_sensor_gets_its_own_row() {
        val results = SensorCheck.results(
            SensorLiveness.analyse(
                listOf(
                    accelerometerTilted(),
                    gyroscopeTurning(),
                    magnetometerNormal(),
                    proximityCovered(),
                    lightCovered(),
                ),
                everySensor,
            ),
        )

        // One row each rather than a single "Sensors" verdict, so a dead gyroscope and a dead
        // proximity sensor arrive in the verdict as two separate things to argue about, each with its
        // own price attached.
        assertThat(results).hasSize(5)
        assertThat(results.map { it.id }.distinct()).hasSize(5)
        assertThat(results.map { it.outcome }.distinct()).containsExactly(CheckOutcome.PASS)
    }

    @Test
    fun a_sensor_that_was_never_exercised_is_a_cant_tell_and_never_a_fault() {
        SensorKind.entries.forEach { kind ->
            val result = resultFor(kind, SensorState.NOT_EXERCISED)
            assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
            assertThat(result.confidence).isEqualTo(Confidence.LOW)
        }
    }

    @Test
    fun a_sensor_the_app_could_not_reach_blames_the_app_and_not_the_phone() {
        SensorKind.entries.forEach { kind ->
            val result = resultFor(kind, SensorState.UNAVAILABLE)
            assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
            assertThat(result.headline).contains("could not connect")
            assertThat(result.headline).contains("rather than anything known about the phone")
        }
    }

    @Test
    fun the_two_kinds_of_cant_tell_never_read_the_same() {
        // One is "you did not do the gesture", the other is "we could not connect". Telling a buyer to
        // tilt the phone again when the app failed to subscribe would be blaming them for our bug.
        val notDone = resultFor(SensorKind.GYROSCOPE, SensorState.NOT_EXERCISED)
        val unreachable = resultFor(SensorKind.GYROSCOPE, SensorState.UNAVAILABLE)

        assertThat(notDone.headline).isNotEqualTo(unreachable.headline)
        assertThat(notDone.action).isNotEqualTo(unreachable.action)
    }

    @Test
    fun no_two_sensors_share_a_cant_tell_sentence() {
        // Two cards stacked with word-for-word the same line makes an honest result look like an
        // unfilled template, which is what the first render of this screen showed.
        val headlines = SensorKind.entries.map {
            resultFor(it, SensorState.NOT_EXERCISED).headline
        }
        assertThat(headlines).containsNoDuplicates()
    }

    @Test
    fun an_untilted_accelerometer_still_admits_that_gravity_checks_out() {
        // It proved its calibration by lying still and reading 9.8. Saying only "cannot tell" would
        // throw away the one thing this sensor can establish without being waved about.
        val result = SensorCheck.results(
            SensorLiveness.analyse(
                listOf(
                    SensorTrace(
                        SensorKind.ACCELEROMETER,
                        List(60) { SensorReading(0f, 0f, 9.81f) },
                    ),
                    SensorTrace(
                        SensorKind.GYROSCOPE,
                        List(60) { SensorReading(0.001f, 0.001f, 0.001f) },
                    ),
                ),
                setOf(SensorKind.ACCELEROMETER),
            ),
        ).single()

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("reads gravity correctly at 9.8")
        assertThat(result.headline).contains("not tilted far enough")
    }

    @Test
    fun a_cant_tell_still_says_how_the_buyer_could_get_an_answer() {
        // Unusual for an UNKNOWN to carry an action, and deliberate: this is the one kind of "cannot
        // tell" in the whole app that the buyer can clear themselves in five seconds.
        assertThat(resultFor(SensorKind.PROXIMITY, SensorState.NOT_EXERCISED).action)
            .contains("palm")
        assertThat(resultFor(SensorKind.GYROSCOPE, SensorState.NOT_EXERCISED).action)
            .contains("tilt")
    }

    @Test
    fun silence_and_a_missed_gesture_are_both_failures_for_the_sensors_that_sit_under_your_hand() {
        listOf(
            SensorKind.ACCELEROMETER,
            SensorKind.GYROSCOPE,
            SensorKind.MAGNETOMETER,
            SensorKind.PROXIMITY,
        ).forEach { kind ->
            assertThat(resultFor(kind, SensorState.SILENT).outcome).isEqualTo(CheckOutcome.FAIL)
            assertThat(resultFor(kind, SensorState.DEAD).outcome).isEqualTo(CheckOutcome.FAIL)
        }
    }

    @Test
    fun the_light_sensor_is_only_ever_a_caution_because_it_may_be_under_the_display() {
        // The one exception in the list, and a real one: on a lot of phones this sensor is behind the
        // panel rather than in the earpiece slot, so a palm placed exactly where the screen asked can
        // legitimately miss it.
        val result = resultFor(SensorKind.LIGHT, SensorState.DEAD)

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.falsePositiveCauses.first()).contains("under the display")
    }

    @Test
    fun the_light_sensor_saying_nothing_at_all_is_still_a_failure() {
        // Silence is different. Being in an awkward place does not stop a working sensor answering.
        assertThat(resultFor(SensorKind.LIGHT, SensorState.SILENT).outcome)
            .isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun an_impossible_reading_is_a_caution_never_a_failure() {
        // A fridge magnet on the back of a phone will do this to a perfectly good compass. Being wrong
        // here means telling someone their phone is broken over a magnetic case.
        SensorKind.entries.forEach { kind ->
            val result = resultFor(kind, SensorState.IMPLAUSIBLE)
            assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
            assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        }
    }

    @Test
    fun every_negative_result_names_the_witness_that_convicted_it() {
        // The buyer is about to repeat this sentence to the person selling them the phone, so it has to
        // carry its own evidence rather than assert a fault.
        assertThat(resultFor(SensorKind.GYROSCOPE, SensorState.DEAD).headline)
            .contains("accelerometer followed it")
        assertThat(resultFor(SensorKind.PROXIMITY, SensorState.DEAD).headline)
            .contains("light sensor went dark")
        assertThat(resultFor(SensorKind.LIGHT, SensorState.DEAD).headline)
            .contains("proximity sensor felt your palm")
    }

    @Test
    fun every_problem_carries_a_consequence_an_action_and_its_own_caveats() {
        // CheckResult enforces this structurally, so this test is really asserting that no sensor is
        // missing from the copy tables — a gap there would throw on construction, in front of a buyer.
        val states = listOf(
            SensorState.SILENT,
            SensorState.DEAD,
            SensorState.STUCK,
            SensorState.IMPLAUSIBLE,
        )
        SensorKind.entries.forEach { kind ->
            states.forEach { state ->
                val result = resultFor(kind, state)
                assertThat(result.consequence).isNotEmpty()
                assertThat(result.action).isNotEmpty()
                assertThat(result.falsePositiveCauses).isNotEmpty()
            }
        }
    }

    @Test
    fun the_gravity_a_working_accelerometer_reported_is_shown_next_to_the_real_value() {
        val result = SensorCheck.results(
            SensorLiveness.analyse(
                listOf(accelerometerTilted(), gyroscopeTurning()),
                setOf(SensorKind.ACCELEROMETER),
            ),
        ).single()

        // The number is the whole point. "Sensors: yes" told a buyer nothing they could take to a
        // seller; "reads gravity as 9.8, and 9.8 is correct" is a measurement.
        assertThat(result.headline).contains("9.8 m/s²")
        assertThat(result.measurements.map { it.label })
            .containsAtLeast("Gravity measured", "Gravity expected")
    }

    @Test
    fun a_latched_sensor_reports_how_many_identical_readings_it_sent() {
        val result = SensorCheck.results(
            SensorLiveness.analyse(
                listOf(accelerometerLatched(90), gyroscopeTurning()),
                setOf(SensorKind.ACCELEROMETER),
            ),
        ).single()

        assertThat(result.headline).contains("same reading 90 times")
        assertThat(result.measurements.first { it.label == "Different readings" }.value)
            .isEqualTo("1")
    }

    @Test
    fun numbers_do_not_change_shape_with_the_machines_locale() {
        // These strings end up compared in tests and rendered into committed screenshots, so a build
        // machine set to a decimal-comma locale must not be able to alter them.
        val result = SensorCheck.results(
            SensorLiveness.analyse(
                listOf(accelerometerTilted(), gyroscopeTurning()),
                setOf(SensorKind.ACCELEROMETER),
            ),
        ).single()

        assertThat(result.measurements.first { it.label == "Gravity measured" }.value)
            .contains(".")
    }
}
