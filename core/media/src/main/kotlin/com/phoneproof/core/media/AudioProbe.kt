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
import com.phoneproof.checks.media.TonePlan
import com.phoneproof.core.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

    /**
     * Plays a recording back through the loudspeaker, and returns when it has finished.
     *
     * Asked for after a hardware test: a level meter and a verdict tell a buyer the microphone *works*,
     * and hearing their own voice back tells them what it sounds like — muffled, crackly, distant. No
     * measurement substitutes for that, and it costs nothing because the samples are already in hand.
     *
     * **Nothing is written to disk.** The samples came from a recording held in memory and go straight to
     * an `AudioTrack`, which keeps the promise the permission screen makes to a stranger about their own
     * phone. That promise is the reason this does not offer to save the clip.
     */
    suspend fun play(window: AudioWindow): Unit = withContext(Dispatchers.IO) {
        val samples = window.samples
        if (samples.isEmpty()) return@withContext

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(window.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.onFailure { Diagnostics.error(TAG, "could not build a playback track", it) }.getOrNull()
            ?: return@withContext

        try {
            track.write(samples, 0, samples.size)
            track.play()
            // MODE_STATIC gives no completion callback, so the wait is the clip's own length plus a
            // margin. Overshooting by a little is harmless; cutting a buyer's own voice off early is the
            // one outcome that would make them doubt the recording rather than the phone.
            delay((window.durationSeconds * 1000).toLong() + PLAYBACK_TAIL_MILLIS)
        } catch (error: IllegalStateException) {
            Diagnostics.error(TAG, "playback failed", error)
        } finally {
            runCatching { track.stop() }
            track.release()
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
     * The buffer comes from [TonePlan], which exists because this is where the speaker test was broken.
     * It used to compute `(rate / TEST_TONE_HZ).toInt()` — `44.1` truncated to `44` at 44.1 kHz — so the
     * phone emitted 1002.27 Hz while the detector listened at exactly 1000, and the test reported nothing
     * in a silent room on every handset that settled on 44.1 kHz. That is the first rate tried.
     *
     * The plan yields a buffer holding a whole number of cycles at the true frequency, so the loop point
     * stays sample-exact — a click is broadband, and broadband energy is what would flatter the detector
     * into a false positive — while the frequency is now the one being looked for.
     */
    private fun startTone(rate: Int): AudioTrack? = runCatching {
        val plan = TonePlan.of(rate, ToneDetector.TEST_TONE_HZ)
        val count = plan.samples
        val buffer = plan.pcm16(TONE_AMPLITUDE)
        Diagnostics.info(
            TAG,
            "tone: ${plan.frequencyHz} Hz, ${plan.cycles} cycles in $count samples at $rate Hz",
        )

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

        /** Slack on the playback wait, so the tail of a word is never clipped. */
        const val PLAYBACK_TAIL_MILLIS = 250L
    }
}
