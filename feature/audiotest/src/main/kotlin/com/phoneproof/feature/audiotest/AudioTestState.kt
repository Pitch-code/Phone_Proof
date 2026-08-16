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

    /**
     * The speaker measurement was inconclusive, so the buyer is being asked.
     *
     * No speaker verdict exists in this stage, deliberately. See [AudioTestUiState.speaker].
     */
    ASKING,

    /** The loudspeaker is settled; the earpiece has not been tested yet. */
    SPEAKER_DONE,

    /** Playing the tone into the earpiece and recording at the same time. */
    PLAYING_EARPIECE,

    /** The earpiece measurement was inconclusive, so the buyer is being asked. */
    ASKING_EARPIECE,

    /** All three verdicts are in. */
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
    /**
     * The speaker verdict, or null while there isn't one.
     *
     * Null throughout [AudioStage.ASKING], and that is the fix for something seen on a real phone: the
     * provisional CAN'T TELL result used to be published *and* the question asked, so the screen showed a
     * verdict badge above a question whose answer was about to replace it. A buyer reading "CAN'T TELL"
     * has been given an answer; being asked for one in the next breath makes the app look confused about
     * what it knows.
     */
    val speaker: CheckResult? = null,
    /**
     * The earpiece verdict — the speaker held against your ear, which is a different part entirely.
     *
     * Null while [AudioStage.ASKING_EARPIECE], for the same reason [speaker] is: a provisional verdict on
     * screen above the question that decides it makes the app look confused about what it knows.
     */
    val earpiece: CheckResult? = null,
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
    /**
     * Whether there is a recording of the buyer's voice to play back.
     *
     * The samples live in the ViewModel rather than in here: three seconds at 44.1 kHz is a quarter of a
     * megabyte, and state objects get compared on every recomposition. What the screen needs is the
     * boolean.
     */
    val canPlayBack: Boolean = false,
    val isPlayingBack: Boolean = false,
) {
    val isBusy: Boolean
        get() = stage == AudioStage.LISTENING ||
            stage == AudioStage.PLAYING_TONE ||
            stage == AudioStage.PLAYING_EARPIECE ||
            isPlayingBack
}
