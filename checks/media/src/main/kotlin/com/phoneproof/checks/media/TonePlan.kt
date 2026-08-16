package com.phoneproof.checks.media

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How to build a loopable buffer that plays a given frequency **exactly**.
 *
 * ## The bug this exists to prevent
 *
 * The speaker test plays a tone and listens for it, so the frequency played and the frequency listened
 * for have to be the same number. They were not. The buffer used to be built like this:
 *
 * ```
 * val samplesPerCycle = (rate / ToneDetector.TEST_TONE_HZ).toInt()
 * ```
 *
 * At 44,100 Hz that is `44.1` truncated to `44`, so the phone emitted `44100 / 44` = **1002.27 Hz**
 * while the detector looked at exactly 1000 Hz. A Goertzel filter over a three-second window has bins
 * about a third of a hertz wide, which put the real tone some seven bins away from where anything was
 * looking. **It reported nothing in a silent room**, every time, on any handset that settled on 44.1 kHz
 * — which is the first rate the recorder tries. At 48 kHz the division came out even and the test
 * worked, which is exactly the kind of split that makes a bug survive.
 *
 * The unit tests never caught it because they synthesised a perfect 1000.000 Hz sine. They tested the
 * ideal while the device shipped the truncation, so the fix belongs here, in pure Kotlin, generating the
 * same samples the hardware will actually be handed.
 *
 * ## How it is exact
 *
 * Pick a buffer holding a whole number of cycles. The shortest one is `sampleRate / gcd(sampleRate, hz)`
 * samples long — 441 samples for 10 cycles at 1 kHz on a 44.1 kHz device — and any multiple of it is
 * also cycle-exact. So the buffer is that period repeated until it is long enough to hand to an
 * `AudioTrack` without underrunning, and because the phase at the end of the buffer is a whole number of
 * turns, looping it produces no click.
 *
 * That last part matters more than it sounds: a click is broadband, and broadband energy is exactly what
 * would flatter a tone detector into a false positive.
 */
data class TonePlan(
    val sampleRate: Int,
    /** The frequency this buffer really plays. Equal to the request whenever both are whole numbers. */
    val frequencyHz: Float,
    /** Buffer length in samples, always a whole number of cycles. */
    val samples: Int,
    /** How many complete cycles [samples] holds. */
    val cycles: Int,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(samples > 0) { "samples must be positive, was $samples" }
        require(cycles > 0) { "cycles must be positive, was $cycles" }
    }

    /**
     * The buffer, as 16-bit PCM.
     *
     * Phase is taken from [cycles] over [samples] rather than from the frequency and the rate, so the
     * wrap point is exact by construction instead of by rounding.
     */
    fun pcm16(amplitude: Double): ShortArray = ShortArray(samples) { index ->
        val phase = 2.0 * PI * cycles * index / samples
        (sin(phase) * amplitude * Short.MAX_VALUE).toInt().toShort()
    }

    companion object {

        /**
         * @param minimumSeconds how much audio to hand the hardware. Rounded up to a cycle-exact length.
         *   100 ms is long enough that an `AudioTrack` loop does not underrun on a cheap handset, and
         *   short enough that stopping the tone is immediate.
         */
        fun of(sampleRate: Int, requestedHz: Float, minimumSeconds: Float = 0.1f): TonePlan {
            require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
            require(requestedHz > 0f) { "requestedHz must be positive, was $requestedHz" }
            require(requestedHz < sampleRate / 2f) {
                "requestedHz $requestedHz is at or above the Nyquist limit for $sampleRate Hz"
            }

            val hz = requestedHz.roundToInt().coerceAtLeast(1)
            val divisor = gcd(sampleRate, hz)
            val period = sampleRate / divisor
            val cyclesPerPeriod = hz / divisor

            val wanted = (minimumSeconds * sampleRate).coerceAtLeast(period.toFloat())
            val repeats = ceil(wanted / period).toInt().coerceAtLeast(1)

            val samples = period * repeats
            val cycles = cyclesPerPeriod * repeats
            return TonePlan(
                sampleRate = sampleRate,
                // Derived rather than copied from the request, so a plan can never claim a frequency it
                // does not play. With whole numbers on both sides this is the request exactly.
                frequencyHz = sampleRate.toFloat() * cycles / samples,
                samples = samples,
                cycles = cycles,
            )
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
