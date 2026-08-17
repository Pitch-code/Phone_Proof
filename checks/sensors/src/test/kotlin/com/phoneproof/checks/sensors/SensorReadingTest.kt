package com.phoneproof.checks.sensors

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Trimming platform events down to the slots that mean something.
 *
 * This exists because of a real bug on a real phone. Every sensor event had all three slots copied in,
 * and `magnitude` squares all three — so for a light sensor whose driver publishes an extra channel, the
 * number being compared against the "is it dark" threshold was inflated by data that was never lux. The
 * palm indicator could not be satisfied, and the lux printed in the report was wrong as well.
 */
class SensorReadingTest {

    @Test
    fun a_light_reading_keeps_only_the_lux() {
        // The exact shape of the bug: an OEM driver reporting 25 lux plus two channels of its own.
        val reading = SensorReading.of(SensorKind.LIGHT, x = 25f, y = 900f, z = 4000f)

        assertThat(reading.x).isEqualTo(25f)
        assertThat(reading.y).isEqualTo(0f)
        assertThat(reading.z).isEqualTo(0f)
        // And therefore the figure every threshold is compared against is the lux and nothing else.
        assertThat(reading.magnitude).isEqualTo(25.0)
    }

    @Test
    fun a_proximity_reading_keeps_only_the_distance() {
        val reading = SensorReading.of(SensorKind.PROXIMITY, x = 5f, y = 1f, z = 1f)

        assertThat(reading.magnitude).isEqualTo(5.0)
    }

    @Test
    fun the_three_axis_sensors_keep_all_three() {
        // The counterpart risk: trimming a real vector would break the tilt test, which depends on
        // gravity moving between axes.
        for (kind in listOf(SensorKind.ACCELEROMETER, SensorKind.GYROSCOPE, SensorKind.MAGNETOMETER)) {
            val reading = SensorReading.of(kind, x = 3f, y = 4f, z = 0f)

            assertThat(reading.y).isEqualTo(4f)
            assertThat(reading.magnitude).isEqualTo(5.0)
        }
    }

    @Test
    fun every_kind_declares_how_many_axes_it_has() {
        // Driven off the enum so a sensor added later cannot default to being treated as a vector.
        SensorKind.entries.forEach { assertThat(it.axes).isIn(listOf(1, 3)) }

        assertThat(SensorKind.LIGHT.axes).isEqualTo(1)
        assertThat(SensorKind.PROXIMITY.axes).isEqualTo(1)
    }

    @Test
    fun a_single_axis_reading_is_unchanged_by_surplus_slots() {
        // Two drivers reporting the same lux differently must produce identical readings, or the same
        // phone would score differently depending on which OEM wrote its sensor driver.
        val tidy = SensorReading.of(SensorKind.LIGHT, x = 25f, y = 0f, z = 0f)
        val chatty = SensorReading.of(SensorKind.LIGHT, x = 25f, y = 900f, z = 4000f)

        assertThat(chatty).isEqualTo(tidy)
    }
}
