package com.phoneproof.checks.media

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

/**
 * The regression suite for a bug that made the speaker test useless on most phones.
 *
 * The old generator computed `(rate / 1000f).toInt()` and emitted 1002.27 Hz at 44.1 kHz while the
 * detector listened at 1000. The existing tests all passed, because every one of them synthesised a
 * perfect 1000.000 Hz sine — they tested the ideal while the device shipped the truncation.
 *
 * So these tests do the opposite: they build the samples the way the hardware will really be handed them,
 * and then ask whether the detector can find them.
 */
class TonePlanTest {

    private val rates = listOf(8_000, 11_025, 16_000, 22_050, 44_100, 48_000)

    @Test
    fun every_sample_rate_the_recorder_might_pick_plays_the_frequency_asked_for() {
        rates.forEach { rate ->
            val plan = TonePlan.of(rate, ToneDetector.TEST_TONE_HZ)
            assertThat(plan.frequencyHz).isWithin(0.001f).of(ToneDetector.TEST_TONE_HZ)
        }
    }

    @Test
    fun the_case_that_was_broken_is_exact_now() {
        // 44,100 is the first rate the recorder tries, so this is the configuration nearly every phone
        // ended up in. 44100 / 1000 = 44.1, and truncating that was the whole bug.
        val plan = TonePlan.of(44_100, 1000f)

        assertThat(plan.frequencyHz).isEqualTo(1000f)
        // 441 samples hold exactly 10 cycles; the buffer is that period repeated to reach 100 ms.
        assertThat(plan.samples % 441).isEqualTo(0)
        assertThat(plan.sampleRate * plan.cycles).isEqualTo(1000 * plan.samples)
    }

    @Test
    fun the_buffer_always_holds_a_whole_number_of_cycles_so_the_loop_cannot_click() {
        rates.forEach { rate ->
            val plan = TonePlan.of(rate, ToneDetector.TEST_TONE_HZ)
            // Exact integer arithmetic, deliberately: cycles/samples must be rate-proportionate with no
            // remainder at all. A click at the wrap point is broadband, and broadband energy is what
            // would flatter the detector into a false positive.
            assertThat(plan.sampleRate.toLong() * plan.cycles)
                .isEqualTo(ToneDetector.TEST_TONE_HZ.toLong() * plan.samples)
        }
    }

    @Test
    fun the_last_sample_leaves_the_wave_where_the_first_one_found_it() {
        val plan = TonePlan.of(44_100, 1000f)
        val pcm = plan.pcm16(0.7)

        // Continuity across the loop join, checked on the samples themselves rather than on the maths:
        // the step from the final sample back to the first must be no larger than the steps inside.
        val join = abs(pcm.first().toInt() - pcm.last().toInt())
        val largestInternalStep = (1 until pcm.size).maxOf { abs(pcm[it] - pcm[it - 1]) }
        assertThat(join).isAtMost(largestInternalStep)
    }

    @Test
    fun the_buffer_is_long_enough_not_to_underrun_and_not_much_longer() {
        val plan = TonePlan.of(44_100, 1000f)

        assertThat(plan.samples).isAtLeast(4_410)
        assertThat(plan.samples).isAtMost(9_000)
    }

    @Test
    fun the_amplitude_stays_short_of_clipping() {
        val pcm = TonePlan.of(44_100, 1000f).pcm16(0.7)
        val peak = pcm.maxOf { abs(it.toInt()) }

        // A tone at full scale clips through a small speaker's own limiter and spreads energy across the
        // spectrum, which makes the test tone harder to find rather than easier.
        assertThat(peak).isAtMost((Short.MAX_VALUE * 0.71).toInt())
        assertThat(peak).isAtLeast((Short.MAX_VALUE * 0.68).toInt())
    }

    // ------------------------------------------------------------------ the end-to-end guard

    @Test
    fun the_tone_the_hardware_is_handed_is_the_tone_the_detector_finds() {
        // The test that was missing. It generates through TonePlan — the same code path the phone uses —
        // and requires the detector to find it at every sample rate.
        rates.forEach { rate ->
            val plan = TonePlan.of(rate, ToneDetector.TEST_TONE_HZ)
            val window = AudioWindow(rate, loop(plan, seconds = 1.5f))

            val ratio = ToneDetector.toneRatio(window, ToneDetector.TEST_TONE_HZ)
            assertThat(ratio).isGreaterThan(SpeakerCheck.TONE_DETECTED_RATIO)
        }
    }

    @Test
    fun the_old_truncated_generator_would_still_fail_this() {
        // Kept as documentation of the failure. Reproduces the old arithmetic exactly, and shows that a
        // single-bin search finds nothing — which is what the phone reported as "0%" in a quiet room.
        val rate = 44_100
        val samplesPerCycle = (rate / ToneDetector.TEST_TONE_HZ).toInt() // 44, not 44.1
        val brokenHz = rate.toFloat() / samplesPerCycle // 1002.27 Hz
        assertThat(brokenHz).isGreaterThan(1002f)

        val samples = ShortArray((rate * 1.5f).toInt()) { index ->
            val phase = 2.0 * Math.PI * index / samplesPerCycle
            (kotlin.math.sin(phase) * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
        val window = AudioWindow(rate, samples)

        // Looking at exactly 1000 Hz: nothing, on a recording that is nothing but tone.
        assertThat(ToneDetector.toneRatio(window, 1000f))
            .isLessThan(SpeakerCheck.TONE_DETECTED_RATIO)

        // And the second line of defence catches it anyway.
        val match = ToneDetector.bestToneRatio(window, 1000f)
        assertThat(match.ratio).isGreaterThan(SpeakerCheck.TONE_DETECTED_RATIO)
        assertThat(match.frequencyHz).isWithin(1.5f).of(brokenHz)
    }

    /** Repeats a plan's buffer to fill [seconds], the way an AudioTrack loop does. */
    private fun loop(plan: TonePlan, seconds: Float): ShortArray {
        val one = plan.pcm16(0.7)
        val total = (plan.sampleRate * seconds).toInt()
        return ShortArray(total) { one[it % one.size] }
    }
}
