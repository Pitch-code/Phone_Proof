package com.phoneproof.checks.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WaveformTest {

    @Test
    fun `the rms of a sine is its amplitude over root two`() {
        // A known closed form, so this checks the arithmetic against theory rather than against itself.
        val analysis = analyse(Signals.sine(1000f, seconds = 0.5f, amplitude = 0.5f))
        val expected = 0.5f / kotlin.math.sqrt(2f)

        assertThat(analysis.loudest).isWithin(0.02f).of(expected)
        assertThat(analysis.noiseFloor).isWithin(0.02f).of(expected)
    }

    @Test
    fun `peak is the loudest single sample`() {
        val analysis = analyse(Signals.sine(1000f, 0.5f, amplitude = 0.8f))

        assertThat(analysis.peak).isWithin(0.01f).of(0.8f)
    }

    @Test
    fun `digital silence is distinguished from a quiet room`() {
        // The distinction the microphone check leans on: exact zeros mean no audio arrived, while a real
        // capsule in a silent room still returns self-noise.
        assertThat(analyse(Signals.digitalSilence(0.5f)).isDigitalSilence).isTrue()
        assertThat(analyse(Signals.noise(0.5f, amplitude = 0.001f)).isDigitalSilence).isFalse()
    }

    @Test
    fun `a burst of speech rises well above the floor`() {
        val analysis = analyse(
            Signals.speechBurst(seconds = 2f, floorAmplitude = 0.01f, burstAmplitude = 0.3f),
        )

        // 0.3 over 0.01 is about 30 dB, which is where speech at arm's length actually sits.
        assertThat(analysis.signalOverFloorDb).isGreaterThan(MicrophoneCheck.MIN_SIGNAL_OVER_FLOOR_DB)
        assertThat(analysis.signalOverFloorDb).isGreaterThan(20f)
    }

    @Test
    fun `a steady room with nobody speaking does not rise`() {
        val analysis = analyse(Signals.noise(seconds = 2f, amplitude = 0.05f))

        // The case that must not pass: constant noise averages the same everywhere, so the loud frames
        // and the quiet frames agree and there is no event to find.
        assertThat(analysis.signalOverFloorDb).isLessThan(MicrophoneCheck.MIN_SIGNAL_OVER_FLOOR_DB)
    }

    @Test
    fun `a loud steady room still does not rise, however loud it is`() {
        // Proves the measurement is a rise rather than a level. A market lane is twenty decibels above a
        // quiet flat before anyone speaks, and an absolute threshold would pass this as speech.
        val analysis = analyse(Signals.noise(seconds = 2f, amplitude = 0.4f))

        assertThat(analysis.loudest).isGreaterThan(0.1f)
        assertThat(analysis.signalOverFloorDb).isLessThan(MicrophoneCheck.MIN_SIGNAL_OVER_FLOOR_DB)
    }

    @Test
    fun `a single click does not read as somebody speaking`() {
        // Why the signal level is the 95th percentile and not the maximum. One sample of table knock
        // must not pass a dead microphone.
        val samples = ShortArray(Signals.RATE) { 0 }
        samples[Signals.RATE / 2] = Short.MAX_VALUE
        val analysis = analyse(AudioWindow(Signals.RATE, samples))

        assertThat(analysis.peak).isWithin(0.01f).of(1f)
        // The click is in one frame out of fifty, so the 95th percentile never sees it.
        assertThat(analysis.loudest).isEqualTo(0f)
    }

    @Test
    fun `a dropout does not set the noise floor`() {
        // The mirror image, and why the floor is the 20th percentile and not the minimum. A single
        // buffer glitch of pure zeros would otherwise make the floor zero and every rise infinite.
        val window = Signals.noise(seconds = 1f, amplitude = 0.05f)
        for (index in 0 until Signals.RATE / 100) window.samples[index] = 0

        val analysis = analyse(window)
        assertThat(analysis.noiseFloor).isGreaterThan(0f)
    }

    @Test
    fun `full scale reads as clipping`() {
        val analysis = analyse(Signals.sine(1000f, 0.3f, amplitude = 1.0f))

        assertThat(analysis.isClipping).isTrue()
    }

    @Test
    fun `a healthy level is not clipping`() {
        assertThat(analyse(Signals.sine(1000f, 0.3f, amplitude = 0.5f)).isClipping).isFalse()
    }

    @Test
    fun `a window too short for one frame reports nothing rather than throwing`() {
        // A real outcome on this screen: a denied permission produces an empty or tiny buffer, and it is
        // the check's job to explain that in words rather than this function's job to crash.
        val analysis = analyse(AudioWindow(Signals.RATE, ShortArray(10)))

        assertThat(analysis.frameCount).isEqualTo(0)
        assertThat(analysis.signalOverFloorDb).isEqualTo(0f)
    }

    @Test
    fun `an all-silent recording reports no rise rather than an infinity`() {
        // An unguarded division here would produce Infinity, which compares as "very loud" and would
        // pass the most broken case of all.
        val analysis = analyse(Signals.digitalSilence(1f))

        assertThat(analysis.signalOverFloorDb).isEqualTo(0f)
        assertThat(analysis.signalOverFloorDb.isFinite()).isTrue()
    }

    @Test
    fun `the most negative sample cannot overflow the peak`() {
        // abs(Short.MIN_VALUE) is Short.MIN_VALUE again. Done in Int, or the peak of the loudest possible
        // recording comes out negative.
        val samples = ShortArray(Signals.RATE) { Short.MIN_VALUE }
        val analysis = analyse(AudioWindow(Signals.RATE, samples))

        assertThat(analysis.peak).isGreaterThan(0.99f)
        assertThat(analysis.peak).isAtMost(1f)
    }

    @Test
    fun `frame count follows the duration and the frame length`() {
        val window = Signals.noise(seconds = 1f, amplitude = 0.1f)

        // 1 second of 20 ms frames.
        assertThat(analyse(window).frameCount).isEqualTo(50)
        assertThat(analyse(window, frameMillis = 100).frameCount).isEqualTo(10)
    }

    @Test
    fun `duration is reported from the sample count and rate`() {
        assertThat(Signals.sine(1000f, seconds = 2f).durationSeconds).isWithin(0.01f).of(2f)
    }

    @Test
    fun `a zero or negative sample rate is rejected at construction`() {
        runCatching { AudioWindow(0, ShortArray(10)) }.also { assertThat(it.isFailure).isTrue() }
        runCatching { AudioWindow(-1, ShortArray(10)) }.also { assertThat(it.isFailure).isTrue() }
    }

    @Test
    fun `equality compares samples by value rather than by reference`() {
        // The generated equals for a data class holding an array compares references, which would make
        // any future assertion on two identical windows quietly wrong.
        val a = AudioWindow(Signals.RATE, shortArrayOf(1, 2, 3))
        val b = AudioWindow(Signals.RATE, shortArrayOf(1, 2, 3))

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(AudioWindow(Signals.RATE, shortArrayOf(1, 2, 4)))
    }
}
