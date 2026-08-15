package com.phoneproof.checks.media

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesised audio, so every test has an exactly known right answer.
 *
 * This is the whole reason the analysis lives in a pure-Kotlin module. There is no microphone in the
 * build environment and no device to hold up to a speaker, so the only way to know that a tone detector
 * detects tones is to hand it a tone that was constructed rather than recorded.
 */
internal object Signals {

    const val RATE = 44_100

    /** A pure sine at [hz], amplitude as a fraction of full scale. */
    fun sine(hz: Float, seconds: Float, amplitude: Float = 0.5f, rate: Int = RATE): AudioWindow {
        val count = (rate * seconds).toInt()
        val samples = ShortArray(count) { index ->
            val angle = 2.0 * PI * hz * index / rate
            (sin(angle) * amplitude * (AudioWindow.FULL_SCALE - 1)).toInt().toShort()
        }
        return AudioWindow(rate, samples)
    }

    /** Exact zeros: no audio reached the app at all. */
    fun digitalSilence(seconds: Float, rate: Int = RATE): AudioWindow =
        AudioWindow(rate, ShortArray((rate * seconds).toInt()))

    /**
     * Uniform noise at a given amplitude, standing in for a room.
     *
     * Seeded, because a test that fails one run in fifty is worse than no test: it teaches whoever sees
     * it to re-run the build instead of reading the failure.
     */
    fun noise(seconds: Float, amplitude: Float, rate: Int = RATE, seed: Int = 42): AudioWindow {
        val random = Random(seed)
        val count = (rate * seconds).toInt()
        val samples = ShortArray(count) {
            val value = (random.nextFloat() * 2f - 1f) * amplitude
            (value * (AudioWindow.FULL_SCALE - 1)).toInt().toShort()
        }
        return AudioWindow(rate, samples)
    }

    /**
     * Quiet room, then something loud, then quiet again — the shape of someone speaking once.
     *
     * Built by adding a burst of noise on top of a continuous floor rather than by splicing, because a
     * splice creates a discontinuity that reads as a click, and a click is precisely the artefact the
     * 95th-percentile choice in [analyse] exists to ignore.
     */
    fun speechBurst(
        seconds: Float,
        floorAmplitude: Float,
        burstAmplitude: Float,
        burstStartFraction: Float = 0.4f,
        burstLengthFraction: Float = 0.2f,
        rate: Int = RATE,
        seed: Int = 7,
    ): AudioWindow {
        val random = Random(seed)
        val count = (rate * seconds).toInt()
        val burstStart = (count * burstStartFraction).toInt()
        val burstEnd = burstStart + (count * burstLengthFraction).toInt()

        val samples = ShortArray(count) { index ->
            val amplitude = if (index in burstStart until burstEnd) burstAmplitude else floorAmplitude
            val value = (random.nextFloat() * 2f - 1f) * amplitude
            (value * (AudioWindow.FULL_SCALE - 1)).toInt().toShort()
        }
        return AudioWindow(rate, samples)
    }

    /** A tone buried in noise, which is the speaker test in a real shop. */
    fun sineInNoise(
        hz: Float,
        seconds: Float,
        toneAmplitude: Float,
        noiseAmplitude: Float,
        rate: Int = RATE,
        seed: Int = 11,
    ): AudioWindow {
        val random = Random(seed)
        val count = (rate * seconds).toInt()
        val samples = ShortArray(count) { index ->
            val tone = sin(2.0 * PI * hz * index / rate) * toneAmplitude
            val hiss = (random.nextFloat() * 2f - 1f) * noiseAmplitude
            ((tone + hiss).coerceIn(-1.0, 1.0) * (AudioWindow.FULL_SCALE - 1)).toInt().toShort()
        }
        return AudioWindow(rate, samples)
    }
}
