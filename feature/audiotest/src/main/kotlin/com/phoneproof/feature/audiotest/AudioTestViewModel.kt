package com.phoneproof.feature.audiotest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.media.HeardTone
import com.phoneproof.checks.media.MicrophoneCheck
import com.phoneproof.checks.media.SpeakerCheck
import com.phoneproof.checks.media.ToneDetector
import com.phoneproof.checks.media.analyse
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.media.AudioProbe
import com.phoneproof.core.model.CheckOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the two audio tests.
 *
 * The division of labour is the same as everywhere else in this app: [AudioProbe] gets samples out of
 * Android, `checks:media` decides what they mean, and this class only sequences the two and publishes
 * state. Nothing here decides whether a microphone is faulty, which is why nothing here needs a device
 * to be tested.
 */
class AudioTestViewModel(
    private val probe: AudioProbe,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioTestUiState(volume = probe.mediaVolume()))
    val uiState: StateFlow<AudioTestUiState> = _uiState.asStateFlow()

    /** Re-read on resume, because the buyer is told to turn the volume up and may go and do it. */
    fun refreshVolume() {
        _uiState.value = _uiState.value.copy(volume = probe.mediaVolume())
    }

    fun startMicrophoneTest() {
        _uiState.value = _uiState.value.copy(
            stage = AudioStage.LISTENING,
            levels = emptyList(),
            microphone = null,
            captureFailed = false,
        )

        viewModelScope.launch {
            val window = probe.record(seconds = LISTEN_SECONDS, playTone = false)
            if (window == null) {
                _uiState.value = _uiState.value.copy(
                    stage = AudioStage.READY,
                    captureFailed = true,
                )
                return@launch
            }

            val analysis = analyse(window)
            _uiState.value = _uiState.value.copy(
                stage = AudioStage.MICROPHONE_DONE,
                levels = window.frameLevels(),
                microphone = MicrophoneCheck.evaluate(analysis),
            )
        }
    }

    fun startSpeakerTest() {
        _uiState.value = _uiState.value.copy(
            stage = AudioStage.PLAYING_TONE,
            levels = emptyList(),
            speaker = null,
            captureFailed = false,
            volume = probe.mediaVolume(),
        )

        viewModelScope.launch {
            val window = probe.record(seconds = TONE_SECONDS, playTone = true)
            if (window == null) {
                _uiState.value = _uiState.value.copy(
                    stage = AudioStage.MICROPHONE_DONE,
                    captureFailed = true,
                )
                return@launch
            }

            // bestToneRatio, not toneRatio: playback and capture clocks differ by a few hertz on a lot
            // of handsets, and a single-bin search over three seconds misses a tone two hertz off.
            val match = ToneDetector.bestToneRatio(window, ToneDetector.TEST_TONE_HZ)
            val ratio = match.ratio
            Diagnostics.info(
                TAG,
                "tone match: ${"%.3f".format(ratio)} at ${"%.1f".format(match.frequencyHz)} Hz",
            )
            val analysis = analyse(window)
            val result = SpeakerCheck.evaluate(toneRatio = ratio, roomFloor = analysis.noiseFloor)

            // Measured, then asked — and only asked when measuring did not settle it. An UNKNOWN here is
            // the one case where the buyer's ear is worth more than the app's, so the screen moves to the
            // question instead of presenting a shrug as a result.
            val settled = result.outcome != CheckOutcome.UNKNOWN
            _uiState.value = _uiState.value.copy(
                stage = if (settled) AudioStage.FINISHED else AudioStage.ASKING,
                levels = window.frameLevels(),
                speaker = result,
                // Kept so the answer can be folded into the same measurement rather than re-recording.
                pendingToneRatio = ratio,
                pendingRoomFloor = analysis.noiseFloor,
            )
        }
    }

    fun answerHeard(heard: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(
            stage = AudioStage.FINISHED,
            speaker = SpeakerCheck.evaluate(
                toneRatio = state.pendingToneRatio,
                roomFloor = state.pendingRoomFloor,
                heard = if (heard) HeardTone.YES else HeardTone.NO,
            ),
        )
    }

    fun restart() {
        _uiState.value = AudioTestUiState(volume = probe.mediaVolume())
    }

    private companion object {
        const val TAG = "AudioTest"

        /**
         * Three seconds to speak. Long enough to hold several syllables and their gaps, which is what the
         * floor-versus-signal measurement needs, and short enough that nobody abandons it.
         */
        const val LISTEN_SECONDS = 3f

        /**
         * A second and a half of tone. The detector needs only a few cycles of 1 kHz, and a longer tone
         * in a shop is an unkind thing to inflict on everyone standing nearby.
         */
        const val TONE_SECONDS = 1.5f
    }
}
