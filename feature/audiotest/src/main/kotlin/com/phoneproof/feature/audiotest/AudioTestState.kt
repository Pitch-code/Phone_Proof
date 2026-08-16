package com.phoneproof.feature.audiotest

import androidx.compose.runtime.Immutable
import com.phoneproof.core.media.MediaVolume
import com.phoneproof.core.model.CheckResult

/** Which of the two tests the screen is on. */
enum class AudioStage {
    /** Explaining, before anything has been recorded. */
    READY,

    /** Recording while the buyer speaks. */
    LISTENING,

    /** The microphone verdict is on screen. */
    MICROPHONE_DONE,

    /** Playing the tone and recording at the same time. */
    PLAYING_TONE,

    /** The speaker measurement was inconclusive, so the buyer is being asked. */
    ASKING,

    /** Both verdicts are in. */
    FINISHED,
}

/**
 * Everything the audio screen draws.
 *
 * [levels] is the frame-by-frame level of the last recording, kept so the screen can draw the waveform.
 * A buyer watching a bar chart move while they speak learns more about whether the microphone works than
 * any verdict card can tell them afterwards — and if the app is wrong, the waveform is the evidence they
 * can see for themselves.
 */
@Immutable
data class AudioTestUiState(
    val stage: AudioStage = AudioStage.READY,
    val levels: List<Float> = emptyList(),
    val microphone: CheckResult? = null,
    val speaker: CheckResult? = null,
    val volume: MediaVolume = MediaVolume(0, 0),
    /** Set when the platform refused to record at all, which is not the phone's fault. */
    val captureFailed: Boolean = false,
    /**
     * The speaker measurement, held while the buyer is asked whether they heard the tone.
     *
     * Kept so their answer can be folded into the *same* measurement rather than triggering a second
     * recording. Re-recording would move the ground under the question: the room changes between takes,
     * and an answer about one tone applied to a different one is not evidence about anything.
     */
    val pendingToneRatio: Float = 0f,
    val pendingRoomFloor: Float = 0f,
) {
    val isBusy: Boolean
        get() = stage == AudioStage.LISTENING || stage == AudioStage.PLAYING_TONE
}
