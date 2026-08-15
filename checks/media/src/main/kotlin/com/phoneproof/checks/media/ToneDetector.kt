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
