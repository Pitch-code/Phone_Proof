package com.phoneproof.checks.media

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * A window of recorded audio, reduced to the few numbers a verdict actually needs.
 *
 * 16-bit signed samples, because that is what `AudioRecord` produces in
 * `ENCODING_PCM_16BIT` — the one encoding every Android device is required to support. Mono, because
 * a fault is "this microphone heard nothing" and averaging channels would hide exactly that.
 *
 * ## Why frames rather than one number for the whole recording
 *
 * A single RMS over three seconds cannot tell "the buyer spoke for half a second" from "there was a
 * steady hum the whole time" — both average to something middling. Splitting into short frames makes
 * the shape of the recording available: the quiet frames establish what the room sounds like, and the
 * loud ones are the event being looked for. That difference is the measurement.
 */
data class AudioWindow(
    val sampleRate: Int,
    val samples: ShortArray,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
    }

    val durationSeconds: Float get() = samples.size.toFloat() / sampleRate

    /**
     * Root-mean-square level per frame, as a fraction of full scale.
     *
     * ~20 ms frames: long enough that a single sample cannot move the number, short enough that a
     * spoken syllable spans several. The tail shorter than a frame is dropped rather than measured as
     * a partial frame, which would report a quieter level purely because it held fewer samples.
     */
    fun frameLevels(frameMillis: Int = DEFAULT_FRAME_MILLIS): List<Float> {
        val frameSize = sampleRate * frameMillis / 1000
        if (frameSize <= 0 || samples.size < frameSize) return emptyList()

        return (0..samples.size - frameSize step frameSize).map { start ->
            var sumOfSquares = 0.0
            for (index in start until start + frameSize) {
                val normalised = samples[index].toDouble() / FULL_SCALE
                sumOfSquares += normalised * normalised
            }
            sqrt(sumOfSquares / frameSize).toFloat()
        }
    }

    /** The loudest single sample, as a fraction of full scale. */
    val peak: Float
        get() {
            var loudest = 0
            for (sample in samples) {
                // toInt() before abs, because abs(Short.MIN_VALUE) overflows back to itself.
                val magnitude = abs(sample.toInt())
                if (magnitude > loudest) loudest = magnitude
            }
            // Divide in Double, then narrow. FULL_SCALE is a Double, so converting first produced a
            // Double where a Float was declared — and coerceAtMost has no Double/Float overload to
            // paper over it.
            return (loudest / FULL_SCALE).toFloat().coerceAtMost(1f)
        }

    /**
     * True when every sample is exactly zero.
     *
     * Distinct from "quiet", and the distinction is the whole point. A real microphone in a silent room
     * still returns a thin layer of self-noise; a stream of exact zeros means no audio reached the app
     * at all, which is a different fault with different causes — a muted mic at the OS level, another
     * app holding the input, or a dead capsule.
     */
    val isDigitalSilence: Boolean get() = samples.all { it == 0.toShort() }

    companion object {
        const val DEFAULT_FRAME_MILLIS: Int = 20

        /** 16-bit signed full scale. 32768 rather than 32767, matching the negative extreme. */
        const val FULL_SCALE: Double = 32768.0
    }

    // ShortArray is an array, so the generated equals would compare references. Overridden because a
    // data class silently promising value equality it does not have is a trap for a future test.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioWindow) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * sampleRate + samples.contentHashCode()
}

/**
 * What a recording turned out to contain.
 *
 * [noiseFloor] is the 20th percentile of frame levels rather than the minimum. The minimum is one
 * frame, and one frame is where a dropout or a buffer glitch lands, so it describes the worst instant
 * rather than the room. A low percentile describes the room while ignoring the quietest outliers.
 *
 * [loudest] is the 95th percentile for the mirror-image reason: the single loudest frame is where a
 * table knock or a click lands, and treating that as "the buyer spoke" would pass a dead microphone
 * that happened to be tapped.
 */
data class AudioAnalysis(
    val noiseFloor: Float,
    val loudest: Float,
    val peak: Float,
    val isDigitalSilence: Boolean,
    val frameCount: Int,
) {
    /**
     * How far the loud frames rose above the room, in decibels.
     *
     * Decibels rather than a ratio because hearing is logarithmic and the useful thresholds are much
     * easier to state: 6 dB is a doubling, and speech in a normal room sits 15–30 dB above the floor.
     *
     * Guarded against a zero floor, which is not hypothetical — a digitally silent recording has one,
     * and an unguarded division would produce an infinity that compares as "very loud" and pass the
     * broken case.
     */
    val signalOverFloorDb: Float
        get() {
            if (noiseFloor <= 0f || loudest <= 0f) return 0f
            return (20.0 * log10(loudest.toDouble() / noiseFloor)).toFloat()
        }

    /** Full-scale samples mean the input stage ran out of headroom, which distorts everything above it. */
    val isClipping: Boolean get() = peak >= CLIPPING_THRESHOLD

    companion object {
        /**
         * Not 1.0. A signal that reaches 0.99 of full scale is already being flattened by the time it
         * gets there, and demanding an exact maximum would only detect the most extreme case.
         */
        const val CLIPPING_THRESHOLD: Float = 0.99f
    }
}

/**
 * Reduces a window to the numbers a check reasons about.
 *
 * Returns an all-zero analysis for a window too short to hold a single frame, rather than throwing.
 * A recording that produced nothing usable is a normal outcome on this screen — the buyer may have
 * denied the permission or another app may hold the microphone — and it is the check's job to say so
 * in words, not this function's job to crash.
 */
fun analyse(window: AudioWindow, frameMillis: Int = AudioWindow.DEFAULT_FRAME_MILLIS): AudioAnalysis {
    val levels = window.frameLevels(frameMillis)
    if (levels.isEmpty()) {
        return AudioAnalysis(
            noiseFloor = 0f,
            loudest = 0f,
            peak = window.peak,
            isDigitalSilence = window.isDigitalSilence,
            frameCount = 0,
        )
    }

    val sorted = levels.sorted()
    return AudioAnalysis(
        noiseFloor = sorted.percentile(FLOOR_PERCENTILE),
        loudest = sorted.percentile(SIGNAL_PERCENTILE),
        peak = window.peak,
        isDigitalSilence = window.isDigitalSilence,
        frameCount = levels.size,
    )
}

/** Nearest-rank percentile on an already-sorted list. */
private fun List<Float>.percentile(fraction: Float): Float {
    if (isEmpty()) return 0f
    val index = ((size - 1) * fraction).toInt().coerceIn(0, size - 1)
    return this[index]
}

private const val FLOOR_PERCENTILE = 0.20f
private const val SIGNAL_PERCENTILE = 0.95f
