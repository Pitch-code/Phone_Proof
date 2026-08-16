package com.phoneproof.checks.media

import kotlin.math.PI
import kotlin.math.cos

/**
 * Looks for one known frequency in a recording, which is what turns "did you hear it?" into a
 * measurement.
 *
 * ## The trick this makes possible
 *
 * A speaker cannot be tested by asking the phone about it — nothing in Android reports whether a
 * transducer physically moves. But the phone has a microphone a few centimetres from the speaker, so
 * it can play a tone and listen for it. If the microphone hears the tone, the speaker demonstrably
 * works: sound was produced and it travelled. That is a measurement of the real thing rather than a
 * question put to the buyer.
 *
 * ## Why Goertzel rather than an FFT
 *
 * An FFT computes every frequency bin. This needs exactly one, which Goertzel gives in a single pass
 * with two running variables and no allocation — a few lines instead of a library, no new dependency in
 * an app watching its size, and it runs comfortably on the cheap handsets this app exists for.
 */
/** The best match found in a band, and the frequency it was found at. */
data class ToneMatch(
    val ratio: Float,
    val frequencyHz: Float,
)

object ToneDetector {

    /**
     * How much of a window's energy sits at [targetHz], from 0 to about 1.
     *
     * Normalised so that a pure sine at exactly the target reads **1.0** regardless of its amplitude or
     * the length of the window. That is deliberate: the useful question is "is the tone there", not "how
     * loud", because how loud depends on how close the buyer is holding the phone and on the volume the
     * previous owner left it at.
     *
     * Returns 0 for silence rather than dividing by zero.
     */
    fun toneRatio(window: AudioWindow, targetHz: Float): Float {
        val samples = window.samples
        if (samples.isEmpty() || targetHz <= 0f || targetHz >= window.sampleRate / 2f) return 0f

        val omega = 2.0 * PI * targetHz / window.sampleRate
        val coefficient = 2.0 * cos(omega)

        var previous = 0.0
        var beforeThat = 0.0
        var totalEnergy = 0.0

        for (sample in samples) {
            val normalised = sample.toDouble() / AudioWindow.FULL_SCALE
            totalEnergy += normalised * normalised

            val current = normalised + coefficient * previous - beforeThat
            beforeThat = previous
            previous = current
        }

        if (totalEnergy <= 0.0) return 0f

        // |X(k)|^2 for the target bin, from the two final state variables.
        val magnitudeSquared =
            previous * previous + beforeThat * beforeThat - coefficient * previous * beforeThat

        // The 2/n factor is what makes a pure tone read 1.0. For a sine of amplitude A over n samples,
        // |X(k)|^2 is (A*n/2)^2 while the total energy is A^2*n/2, so the raw quotient would grow with
        // the window length — a bug that hides itself completely if every test uses one buffer size.
        val toneEnergy = magnitudeSquared * 2.0 / samples.size
        return (toneEnergy / totalEnergy).toFloat().coerceIn(0f, 1f)
    }

    /**
     * The strongest tone found near [targetHz], and where it was.
     *
     * ## Why a band and not a single frequency
     *
     * [toneRatio] answers a very precise question, and precision is a liability here. Over a three-second
     * window its bins are about a third of a hertz wide, so a tone even two hertz off reads as nothing at
     * all. Two separate things push the real tone off the mark:
     *
     *  - **Arithmetic.** The tone generator used to truncate its samples-per-cycle and emit 1002.27 Hz
     *    while this looked at 1000. [TonePlan] fixes that at the source, and this is the second line of
     *    defence rather than a substitute for it.
     *  - **Clocks.** Playback and capture run off separate clocks on a lot of handsets, and a few hertz of
     *    genuine offset over three seconds is ordinary. No amount of care in the generator removes that,
     *    because it is the hardware disagreeing with itself.
     *
     * Searching a narrow band costs one extra pass per probe — Goertzel allocates nothing — and turns a
     * test that demands the universe cooperate to the third decimal place into one that works on a cheap
     * phone in a shop.
     *
     * ## Why this does not invite false positives
     *
     * Taking the best of many probes biases upward, which would matter if the bar were near the noise.
     * It is not: broadband noise spreads its energy across every bin, so each probe's *normalised* ratio
     * stays near `2/n` however many are tried, orders of magnitude below
     * [SpeakerCheck.TONE_DETECTED_RATIO]. A test asserts that white noise stays under the bar.
     */
    fun bestToneRatio(
        window: AudioWindow,
        targetHz: Float,
        toleranceHz: Float = DEFAULT_TOLERANCE_HZ,
    ): ToneMatch {
        if (window.samples.isEmpty() || targetHz <= 0f) return ToneMatch(0f, targetHz)
        if (toleranceHz <= 0f) return ToneMatch(toneRatio(window, targetHz), targetHz)

        // Half a bin, so no peak can hide between two probes, clamped so a short window does not produce
        // a needlessly coarse sweep and a long one does not produce thousands of probes.
        val binWidth = window.sampleRate.toFloat() / window.samples.size
        val span = toleranceHz * 2f
        val step = (binWidth / 2f).coerceIn(0.1f, 2f).let { candidate ->
            if (span / candidate > MAX_PROBES) span / MAX_PROBES else candidate
        }

        var best = ToneMatch(toneRatio(window, targetHz), targetHz)
        var hz = targetHz - toleranceHz
        while (hz <= targetHz + toleranceHz) {
            val ratio = toneRatio(window, hz)
            if (ratio > best.ratio) best = ToneMatch(ratio, hz)
            hz += step
        }
        return best
    }

    /** How far either side of the target to look. Eight hertz covers ordinary playback-capture drift. */
    const val DEFAULT_TOLERANCE_HZ: Float = 8f

    /** Bounds the work on a long recording. 64 probes over 3 seconds is a few milliseconds. */
    private const val MAX_PROBES = 64

    /**
     * The frequency the app plays for the speaker test.
     *
     * 1 kHz, and every part of that is a constraint rather than a preference:
     *
     *  - It is where hearing is most sensitive and where phone speakers are least bad, so a working
     *    speaker will certainly produce it and the buyer can also confirm it by ear — which matters,
     *    because when the measurement is inconclusive the app falls back to asking them.
     *  - It is far above the low rumble of a market or a fan, so ambient noise does not sit on top of it.
     *  - It avoids the mains hum at 50 Hz and its harmonics, which in India is 50 Hz and would otherwise
     *    be the loudest thing in a shop full of chargers and fluorescent lights.
     */
    const val TEST_TONE_HZ: Float = 1000f
}
