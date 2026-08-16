package com.phoneproof.checks.vibration

import com.google.common.truth.Truth.assertThat
import com.phoneproof.checks.sensors.SensorReading
import kotlin.math.sin
import org.junit.Test

class JerkTest {

    @Test
    fun a_phone_lying_perfectly_still_has_no_jerk() {
        val still = List(50) { SensorReading(0f, 0f, 9.81f) }

        assertThat(meanJerk(still)).isEqualTo(0.0)
    }

    @Test
    fun a_phone_held_in_a_hand_has_a_little() {
        val handHeld = List(50) { index ->
            val tremor = sin(index * 0.6f) * 0.03f
            SensorReading(tremor, -tremor, 9.81f + tremor)
        }

        val jerk = meanJerk(handHeld)
        assertThat(jerk).isGreaterThan(0.0)
        assertThat(jerk).isLessThan(VibrationCheck.TOO_RESTLESS)
    }

    @Test
    fun a_buzzing_phone_has_an_order_of_magnitude_more() {
        val buzzing = List(50) { index ->
            val shake = sin(index * 2.2f) * 1.5f
            SensorReading(shake, shake * 0.7f, 9.81f + shake * 0.5f)
        }

        assertThat(meanJerk(buzzing)).isGreaterThan(VibrationCheck.MINIMUM_ACTIVE_JERK)
    }

    @Test
    fun a_wobble_that_leaves_the_magnitude_alone_is_still_measured() {
        // The reason this uses the vector difference and not the difference in magnitude. Here the phone is
        // rotating through gravity: the length of the reading never changes at all, while its direction
        // changes constantly. Measuring magnitude would report a perfectly still phone.
        val rotating = List(50) { index ->
            val angle = index * 0.9f
            SensorReading(9.81f * sin(angle), 9.81f * kotlin.math.cos(angle), 0f)
        }

        val magnitudes = rotating.map { it.magnitude }
        assertThat(magnitudes.max() - magnitudes.min()).isLessThan(0.01)
        assertThat(meanJerk(rotating)).isGreaterThan(1.0)
    }

    @Test
    fun a_single_sample_cannot_show_change() {
        assertThat(meanJerk(listOf(SensorReading(0f, 0f, 9.81f)))).isEqualTo(0.0)
        assertThat(meanJerk(emptyList())).isEqualTo(0.0)
    }

    @Test
    fun the_average_does_not_depend_on_how_long_the_recording_was() {
        // A mean rather than a total, so a two-second capture and a four-second one of the same movement
        // produce the same number. Getting this wrong would make the thresholds depend on the timing.
        fun shake(count: Int) = List(count) { index ->
            SensorReading(sin(index * 2.2f) * 1.5f, 0f, 9.81f)
        }

        assertThat(meanJerk(shake(40))).isWithin(0.05).of(meanJerk(shake(80)))
    }
}
