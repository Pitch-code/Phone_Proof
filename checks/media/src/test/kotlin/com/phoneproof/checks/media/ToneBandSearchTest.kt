package com.phoneproof.checks.media

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Test

/**
 * The band search, which is the defence against the hardware disagreeing with itself.
 *
 * Playback and capture run off separate clocks on a lot of handsets. A few hertz of genuine offset over
 * three seconds is ordinary, and a single-bin Goertzel over that window has bins a third of a hertz wide
 * — so an otherwise perfect test can be defeated by two phones' worth of crystal tolerance.
 */
class ToneBandSearchTest {

    private val rate = 44_100

    private fun tone(hz: Float, seconds: Float = 1.5f, amplitude: Double = 0.7): AudioWindow {
        val samples = ShortArray((rate * seconds).toInt()) { index ->
            val phase = 2.0 * PI * hz * index / rate
            (sin(phase) * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        return AudioWindow(rate, samples)
    }

    @Test
    fun a_tone_exactly_on_target_is_found_at_the_target() {
        val match = ToneDetector.bestToneRatio(tone(1000f), 1000f)

        assertThat(match.ratio).isGreaterThan(0.9f)
        assertThat(match.frequencyHz).isWithin(0.5f).of(1000f)
    }

    @Test
    fun a_few_hertz_of_clock_drift_no_longer_defeats_the_test() {
        listOf(996f, 998f, 1002f, 1004f, 1007f).forEach { actual ->
            val single = ToneDetector.toneRatio(tone(actual), 1000f)
            val searched = ToneDetector.bestToneRatio(tone(actual), 1000f)

            assertThat(searched.ratio).isGreaterThan(SpeakerCheck.TONE_DETECTED_RATIO)
            assertThat(searched.frequencyHz).isWithin(1.5f).of(actual)
            // And the search is strictly better than the single bin it replaces, which is the point.
            assertThat(searched.ratio).isAtLeast(single)
        }
    }

    @Test
    fun a_tone_well_outside_the_band_is_still_not_found() {
        // The band is a tolerance, not a licence to accept any tone. A phone playing 1200 Hz when asked
        // for 1000 has something wrong with it, and this must not paper over that.
        val match = ToneDetector.bestToneRatio(tone(1200f), 1000f)

        assertThat(match.ratio).isLessThan(SpeakerCheck.TONE_DETECTED_RATIO)
    }

    @Test
    fun white_noise_does_not_become_a_tone_by_being_searched_sixty_four_times() {
        // The multiple-comparisons worry, tested rather than argued. Taking the best of many probes biases
        // upward; broadband energy spreads across every bin, so each normalised ratio stays tiny and the
        // maximum stays far below the bar.
        val random = Random(20260816)
        val samples = ShortArray((rate * 1.5f).toInt()) {
            (random.nextDouble(-1.0, 1.0) * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
        val match = ToneDetector.bestToneRatio(AudioWindow(rate, samples), 1000f)

        assertThat(match.ratio).isLessThan(SpeakerCheck.TONE_DETECTED_RATIO)
    }

    @Test
    fun silence_is_not_a_tone() {
        val match = ToneDetector.bestToneRatio(AudioWindow(rate, ShortArray(rate)), 1000f)

        assertThat(match.ratio).isEqualTo(0f)
    }

    @Test
    fun an_empty_recording_returns_the_target_and_no_confidence() {
        val match = ToneDetector.bestToneRatio(AudioWindow(rate, ShortArray(0)), 1000f)

        assertThat(match.ratio).isEqualTo(0f)
        assertThat(match.frequencyHz).isEqualTo(1000f)
    }

    @Test
    fun a_quiet_tone_under_room_noise_is_still_found() {
        // The real situation in a shop: the speaker works and the room is not silent. The ratio is lower
        // because the noise owns some of the energy, but the tone is still there to be found.
        val random = Random(7)
        val samples = ShortArray((rate * 1.5f).toInt()) { index ->
            val toneValue = sin(2.0 * PI * 1003f * index / rate) * 0.5
            val noise = random.nextDouble(-1.0, 1.0) * 0.15
            ((toneValue + noise).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        val match = ToneDetector.bestToneRatio(AudioWindow(rate, samples), 1000f)

        assertThat(match.ratio).isGreaterThan(SpeakerCheck.TONE_DETECTED_RATIO)
    }

    @Test
    fun a_zero_tolerance_search_is_just_the_single_bin() {
        val window = tone(1000f)

        assertThat(ToneDetector.bestToneRatio(window, 1000f, toleranceHz = 0f).ratio)
            .isEqualTo(ToneDetector.toneRatio(window, 1000f))
    }
}
