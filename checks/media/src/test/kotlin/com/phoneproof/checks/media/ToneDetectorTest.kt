package com.phoneproof.checks.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToneDetectorTest {

    private val target = ToneDetector.TEST_TONE_HZ

    @Test
    fun `a pure tone at the target reads essentially all of the energy`() {
        val ratio = ToneDetector.toneRatio(Signals.sine(target, seconds = 0.5f), target)

        // Normalised so a clean tone is 1.0. Anything much below would mean the normalisation is wrong.
        assertThat(ratio).isGreaterThan(0.95f)
    }

    @Test
    fun `the reading does not depend on how loud the tone is`() {
        // The property that makes the check usable: how loud depends on the volume the previous owner
        // left set and on how the buyer is holding the phone, neither of which is being tested.
        val quiet = ToneDetector.toneRatio(Signals.sine(target, 0.5f, amplitude = 0.05f), target)
        val loud = ToneDetector.toneRatio(Signals.sine(target, 0.5f, amplitude = 0.9f), target)

        assertThat(quiet).isGreaterThan(0.95f)
        assertThat(loud).isGreaterThan(0.95f)
    }

    @Test
    fun `the reading does not depend on the length of the window`() {
        // Guards the 2/n normalisation specifically. Without it the ratio grows with the buffer size,
        // which a suite using one duration everywhere would never notice.
        val short = ToneDetector.toneRatio(Signals.sine(target, 0.1f), target)
        val long = ToneDetector.toneRatio(Signals.sine(target, 1.0f), target)

        assertThat(short).isGreaterThan(0.95f)
        assertThat(long).isGreaterThan(0.95f)
        assertThat(kotlin.math.abs(short - long)).isLessThan(0.05f)
    }

    @Test
    fun `a tone at a different frequency is not mistaken for the target`() {
        val ratio = ToneDetector.toneRatio(Signals.sine(hz = 400f, seconds = 0.5f), target)

        assertThat(ratio).isLessThan(0.05f)
    }

    @Test
    fun `mains hum is not mistaken for the target`() {
        // 50 Hz in India, and a shop full of chargers and fluorescent tubes is full of it. Choosing
        // 1 kHz was partly to stay clear of this, and this asserts the choice pays off.
        val ratio = ToneDetector.toneRatio(Signals.sine(hz = 50f, seconds = 0.5f), target)

        assertThat(ratio).isLessThan(0.05f)
    }

    @Test
    fun `broadband noise does not read as a tone`() {
        val ratio = ToneDetector.toneRatio(Signals.noise(0.5f, amplitude = 0.5f), target)

        assertThat(ratio).isLessThan(0.05f)
    }

    @Test
    fun `a tone still reads through noise as loud as itself`() {
        // The realistic case: a phone speaker at arm's length in a shop. The detection threshold is 0.25,
        // so this has to land clearly above it or the check fails in the place it is meant to work.
        val ratio = ToneDetector.toneRatio(
            Signals.sineInNoise(target, seconds = 0.5f, toneAmplitude = 0.3f, noiseAmplitude = 0.3f),
            target,
        )

        assertThat(ratio).isGreaterThan(SpeakerCheck.TONE_DETECTED_RATIO)
    }

    @Test
    fun `a tone buried under much louder noise does not reach the threshold`() {
        // And this is the honest other half: when the room genuinely drowns the speaker, the detector
        // must not claim a detection. That is what sends the check to the "ask the buyer" branch.
        val ratio = ToneDetector.toneRatio(
            Signals.sineInNoise(target, seconds = 0.5f, toneAmplitude = 0.02f, noiseAmplitude = 0.8f),
            target,
        )

        assertThat(ratio).isLessThan(SpeakerCheck.TONE_DETECTED_RATIO)
    }

    @Test
    fun `silence reads zero rather than dividing by zero`() {
        assertThat(ToneDetector.toneRatio(Signals.digitalSilence(0.2f), target)).isEqualTo(0f)
    }

    @Test
    fun `impossible targets are rejected rather than aliased`() {
        val window = Signals.sine(target, 0.2f)

        // Zero and negative are meaningless.
        assertThat(ToneDetector.toneRatio(window, 0f)).isEqualTo(0f)
        assertThat(ToneDetector.toneRatio(window, -100f)).isEqualTo(0f)
        // At or above Nyquist a frequency cannot be represented, and Goertzel would happily return a
        // number for it — an alias of something else entirely.
        assertThat(ToneDetector.toneRatio(window, Signals.RATE / 2f)).isEqualTo(0f)
        assertThat(ToneDetector.toneRatio(window, Signals.RATE.toFloat())).isEqualTo(0f)
    }

    @Test
    fun `an empty window is zero rather than a crash`() {
        assertThat(ToneDetector.toneRatio(AudioWindow(Signals.RATE, ShortArray(0)), target)).isEqualTo(0f)
    }

    @Test
    fun `the detector works at the sample rates a phone might hand back`() {
        // AudioRecord does not guarantee 44100. Whatever rate comes back, the maths has to hold, because
        // the rate is an input to the frequency calculation.
        listOf(8_000, 16_000, 22_050, 44_100, 48_000).forEach { rate ->
            val ratio = ToneDetector.toneRatio(Signals.sine(target, 0.3f, rate = rate), target)
            assertThat(ratio).isGreaterThan(0.9f)
        }
    }
}
