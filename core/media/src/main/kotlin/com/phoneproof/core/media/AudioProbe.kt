package com.phoneproof.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.phoneproof.checks.media.AudioWindow
import com.phoneproof.checks.media.ToneDetector
import com.phoneproof.core.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** What the media volume was set to, which decides whether a speaker test can mean anything. */
data class MediaVolume(val current: Int, val max: Int) {
    val fraction: Float get() = if (max <= 0) 0f else current.toFloat() / max
    val isMuted: Boolean get() = current <= 0
    /** Below a fifth, a phone speaker in a shop is inaudible and a failed test would prove nothing. */
    val isTooLow: Boolean get() = fraction < 0.2f
}

/**
 * Records from the microphone, optionally while playing a tone through the speaker.
 *
 * The one class for both tests, because the speaker test *is* a recording — the loopback plays 1 kHz and
 * listens for it, so separating "record" from "record while playing" would duplicate all of the
 * `AudioRecord` handling to save one boolean.
 *
 * Everything here is blocking I/O moved to [Dispatchers.IO], and every failure path returns null rather
 * than throwing. A refused permission, a microphone held by another app, and an OEM that rejects a
 * sample rate are all ordinary outcomes on this screen, and the check above is written to say so in
 * words. A crash would be the one response that helps nobody.
 */
class AudioProbe(private val context: Context) {

    /**
     * @param seconds how long to record.
     * @param playTone whether to drive the speaker with [ToneDetector.TEST_TONE_HZ] while recording.
     * @return the recording, or null if the platform would not give one.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(seconds: Float, playTone: Boolean): AudioWindow? = withContext(Dispatchers.IO) {
        val rate = workingSampleRate() ?: run {
            Diagnostics.error(TAG, "no sample rate accepted by AudioRecord")
            return@withContext null
        }

        val minBuffer = AudioRecord.getMinBufferSize(rate, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            Diagnostics.error(TAG, "getMinBufferSize returned $minBuffer at $rate Hz")
            return@withContext null
        }

        val recorder = runCatching {
            // The permission is guarded by PermissionGate before this screen renders, hence the
            // suppression — but a system-level microphone toggle can still refuse at this point, which is
            // why the result is checked rather than assumed.
            AudioRecord(audioSource(), rate, CHANNEL, ENCODING, minBuffer * 4)
        }.getOrNull()

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            Diagnostics.error(TAG, "AudioRecord would not initialise at $rate Hz")
            recorder?.release()
            return@withContext null
        }

        val tone = if (playTone) startTone(rate) else null

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Diagnostics.error(TAG, "AudioRecord did not enter the recording state")
                return@withContext null
            }

            val wanted = (rate * seconds).toInt()
            val samples = ShortArray(wanted)
            var filled = 0
            while (filled < wanted) {
                val read = recorder.read(samples, filled, wanted - filled)
                // read() returns a negative error code rather than throwing. Stopping on one keeps
                // whatever was captured, which the analysis can still describe honestly.
                if (read <= 0) {
                    Diagnostics.warn(TAG, "read returned $read after $filled samples")
                    break
                }
                filled += read
            }

            if (filled == 0) return@withContext null
            AudioWindow(rate, if (filled == wanted) samples else samples.copyOf(filled))
        } catch (error: IllegalStateException) {
            Diagnostics.error(TAG, "recording failed", error)
            null
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            tone?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }

    fun mediaVolume(): MediaVolume {
        val manager = context.getSystemService(AudioManager::class.java)
        return MediaVolume(
            current = manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0,
            max = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0,
        )
    }

    /**
     * Which input to record from, and this is the single most important line in the file.
     *
     * `AudioSource.MIC` is the obvious choice and it would **break the speaker test**. On most handsets
     * MIC runs through the platform's voice-processing chain, which includes acoustic echo cancellation —
     * hardware and software whose entire job is to remove sound the phone itself just played from what
     * the microphone hears. That is precisely the tone the loopback is listening for. The test would
     * report a working speaker as silent, on a perfect handset, every time.
     *
     * So: `UNPROCESSED` when the device advertises it, which is a raw path with no AEC, no noise
     * suppression and no automatic gain. Failing that `VOICE_RECOGNITION`, which conventionally leaves
     * the signal far less processed than MIC. MIC only as a last resort.
     *
     * Automatic gain control matters for the microphone test too, for a subtler reason: AGC quietly
     * raises the level of a quiet room, which lifts the noise floor and shrinks the very gap between
     * floor and speech that the verdict is measured on.
     */
    private fun audioSource(): Int {
        val manager = context.getSystemService(AudioManager::class.java)
        val unprocessedSupported =
            manager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        return when {
            unprocessedSupported -> MediaRecorder.AudioSource.UNPROCESSED
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    /**
     * The first sample rate `AudioRecord` will accept.
     *
     * Nothing is guaranteed beyond 44.1 kHz being common, and cheap handsets do refuse rates their
     * datasheet claims. The analysis takes the rate as an input precisely so this can be whatever the
     * hardware allows rather than a number the code insists on.
     */
    private fun workingSampleRate(): Int? = CANDIDATE_RATES.firstOrNull { rate ->
        AudioRecord.getMinBufferSize(rate, CHANNEL, ENCODING).let { it > 0 }
    }

    /**
     * Starts a continuous 1 kHz tone on the media stream and returns the track so it can be stopped.
     *
     * One cycle-aligned buffer set to loop, rather than a long buffer written repeatedly: the loop point
     * is sample-exact, so there is no click or phase discontinuity where it wraps. A click is broadband,
     * and broadband energy is exactly what would flatter the tone detector into a false positive.
     */
    private fun startTone(rate: Int): AudioTrack? = runCatching {
        val cyclesPerBuffer = 100
        val samplesPerCycle = (rate / ToneDetector.TEST_TONE_HZ).toInt()
        val count = samplesPerCycle * cyclesPerBuffer
        val buffer = ShortArray(count) { index ->
            val angle = 2.0 * PI * index / samplesPerCycle
            (sin(angle) * TONE_AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // MEDIA rather than a notification or alarm usage. The volume the buyer is being
                    // asked to turn up is the media volume, and a mismatch here would test one stream
                    // while reporting the level of another.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(count * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, count)
        track.setLoopPoints(0, count, -1)
        track.play()
        track
    }.onFailure { Diagnostics.error(TAG, "could not start the test tone", it) }.getOrNull()

    private companion object {
        const val TAG = "AudioProbe"
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** Most likely first, so the common case costs one call. */
        val CANDIDATE_RATES = intArrayOf(44_100, 48_000, 22_050, 16_000, 11_025, 8_000)

        /**
         * Loud, but not full scale. A tone at 1.0 clips through a small speaker's own limiter and
         * spreads energy across the spectrum, which would make the 1 kHz test tone harder to detect
         * rather than easier.
         */
        const val TONE_AMPLITUDE = 0.7
    }
}
