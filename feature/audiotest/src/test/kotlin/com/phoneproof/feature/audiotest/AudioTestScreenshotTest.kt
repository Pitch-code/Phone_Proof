package com.phoneproof.feature.audiotest

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.media.AudioAnalysis
import com.phoneproof.checks.media.EarpieceCheck
import com.phoneproof.checks.media.EarpieceRouting
import com.phoneproof.checks.media.HeardTone
import com.phoneproof.checks.media.MicrophoneCheck
import com.phoneproof.checks.media.SpeakerCheck
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.media.MediaVolume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The audio screen in the states that matter.
 *
 * Robolectric has no microphone, so every state here is constructed rather than recorded — which is
 * exactly why the verdicts come from `checks:media` and the recording from `core:media`. The split means
 * the screen can be rendered in a state that would otherwise need a shop, a stranger's phone and a
 * broken speaker to reach.
 *
 * The waveform is worth rendering in particular. It is the one part of this screen a buyer will trust
 * over the verdict, so it has to look like an instrument rather than a decoration.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AudioTestScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    /** A recording shaped like someone speaking once: quiet, loud, quiet. */
    private fun spokenLevels(): List<Float> = List(60) { index ->
        when (index) {
            in 22..38 -> 0.28f + (index % 5) * 0.05f
            else -> 0.012f + (index % 3) * 0.004f
        }
    }

    private fun render(name: String, state: AudioTestUiState, themeMode: ThemeMode = ThemeMode.LIGHT) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                AudioTestScreen(
                    state = state,
                    onStartMicrophone = {},
                    onStartSpeaker = {},
                    onAnswerHeard = {},
                    onDeclineToAnswer = {},
                    onStartEarpiece = {},
                    onAnswerEarpieceHeard = {},
                    onDeclineEarpieceAnswer = {},
                    onPlayBack = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/audio-$name.png")
    }

    @Test
    fun before_anything_is_recorded() {
        // The empty waveform has to read as an instrument waiting for a signal, not a failed component.
        render("1-ready", AudioTestUiState(volume = MediaVolume(current = 9, max = 15)))
    }

    @Test
    fun the_microphone_heard_clearly() {
        render(
            "2-microphone-pass",
            AudioTestUiState(
                stage = AudioStage.MICROPHONE_DONE,
                levels = spokenLevels(),
                microphone = MicrophoneCheck.evaluate(
                    AudioAnalysis(
                        noiseFloor = 0.012f,
                        loudest = 0.32f,
                        peak = 0.4f,
                        isDigitalSilence = false,
                        frameCount = 60,
                    ),
                ),
                volume = MediaVolume(current = 9, max = 15),
            ),
        )
    }

    @Test
    fun the_media_volume_is_muted_before_the_speaker_test() {
        // The warning has to arrive *before* the test rather than explaining a failure afterwards.
        render(
            "3-volume-muted",
            AudioTestUiState(
                stage = AudioStage.MICROPHONE_DONE,
                levels = spokenLevels(),
                microphone = MicrophoneCheck.evaluate(
                    AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60),
                ),
                volume = MediaVolume(current = 0, max = 15),
            ),
        )
    }

    @Test
    fun the_tone_could_not_be_measured_so_the_buyer_is_asked() {
        // What is left on the screen behind the dialog. Note there is no speaker card: the provisional
        // CAN'T TELL used to be published here as a verdict while the question sat underneath it, so the
        // app answered and then asked. Reported from a real phone.
        render(
            "4-asking",
            askingState(),
        )
    }

    @Test
    fun the_question_itself_interrupts() {
        // The question used to be the last thing in a scrolling column, below two result cards, and on a
        // real phone it was off the bottom of the screen — so most buyers never saw it and the test looked
        // as though it had simply finished inconclusively.
        //
        // Captured as the dialog node rather than the root, because a Dialog is its own window and
        // onRoot() would render the screen behind it.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.LIGHT) {
                AudioTestScreen(
                    state = askingState(),
                    onStartMicrophone = {},
                    onStartSpeaker = {},
                    onAnswerHeard = {},
                    onDeclineToAnswer = {},
                    onStartEarpiece = {},
                    onAnswerEarpieceHeard = {},
                    onDeclineEarpieceAnswer = {},
                    onPlayBack = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNode(isDialog()).captureRoboImage("$outputDir/audio-7-tone-question.png")
    }

    @Test
    fun the_microphone_can_be_played_back() {
        // "Play back what you said" only exists once there is a recording, and it has to sit with the
        // microphone result rather than at the end of the screen: this is the moment the buyer still
        // remembers what they said.
        render(
            "8-playback-offered",
            AudioTestUiState(
                stage = AudioStage.MICROPHONE_DONE,
                levels = spokenLevels(),
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                volume = MediaVolume(current = 12, max = 15),
                canPlayBack = true,
            ),
        )
    }

    @Test
    fun playback_in_progress_cannot_be_started_twice() {
        render(
            "9-playing-back",
            AudioTestUiState(
                stage = AudioStage.MICROPHONE_DONE,
                levels = spokenLevels(),
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                volume = MediaVolume(current = 12, max = 15),
                canPlayBack = true,
                isPlayingBack = true,
            ),
        )
    }

    private fun askingState() = AudioTestUiState(
        stage = AudioStage.ASKING,
        levels = spokenLevels(),
        microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
        volume = MediaVolume(current = 12, max = 15),
        pendingToneRatio = 0.04f,
        pendingRoomFloor = 0.3f,
    )

    @Test
    fun neither_the_app_nor_the_buyer_heard_it() {
        // The only route to a speaker FAIL, and the card has to carry the consequence and the action.
        render(
            "5-speaker-fail",
            AudioTestUiState(
                stage = AudioStage.FINISHED,
                levels = spokenLevels(),
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                speaker = SpeakerCheck.evaluate(0.01f, roomFloor = 0.005f, heard = HeardTone.NO),
                volume = MediaVolume(current = 15, max = 15),
            ),
        )
    }

    @Test
    fun the_phone_refused_to_record_at_all_in_dark_mode() {
        // Not a fault in the handset, and the copy has to say so — a mic held by another app or a
        // system-level privacy toggle produces exactly this.
        render(
            "6-capture-failed-dark",
            AudioTestUiState(captureFailed = true, volume = MediaVolume(current = 9, max = 15)),
            themeMode = ThemeMode.DARK,
        )
    }

    // ------------------------------------------------------------------ the earpiece

    @Test
    fun the_earpiece_is_offered_as_a_separate_part() {
        // Most buyers have never thought about the earpiece, so the offer has to explain what it is and
        // why a working loudspeaker says nothing about it.
        render(
            "10-earpiece-offered",
            AudioTestUiState(
                stage = AudioStage.SPEAKER_DONE,
                levels = spokenLevels(),
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                speaker = SpeakerCheck.evaluate(toneRatio = 0.6f, roomFloor = 0.01f),
                volume = MediaVolume(current = 12, max = 15),
                canPlayBack = true,
            ),
        )
    }

    @Test
    fun holding_it_to_your_ear() {
        render(
            "11-earpiece-playing",
            AudioTestUiState(
                stage = AudioStage.PLAYING_EARPIECE,
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                speaker = SpeakerCheck.evaluate(toneRatio = 0.6f, roomFloor = 0.01f),
                volume = MediaVolume(current = 12, max = 15),
            ),
        )
    }

    @Test
    fun the_earpiece_question_explains_why_a_miss_means_little() {
        // A different sentence from the loudspeaker's. On the loudspeaker a miss usually means the room was
        // loud; on the earpiece a miss is the normal outcome even on perfect hardware, and the buyer must
        // not read the question as a hint that something is wrong.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.LIGHT) {
                AudioTestScreen(
                    state = AudioTestUiState(
                        stage = AudioStage.ASKING_EARPIECE,
                        microphone = MicrophoneCheck.evaluate(
                            AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60),
                        ),
                        speaker = SpeakerCheck.evaluate(toneRatio = 0.6f, roomFloor = 0.01f),
                        volume = MediaVolume(current = 12, max = 15),
                        pendingToneRatio = 0.02f,
                        pendingRoomFloor = 0.01f,
                    ),
                    onStartMicrophone = {},
                    onStartSpeaker = {},
                    onAnswerHeard = {},
                    onDeclineToAnswer = {},
                    onStartEarpiece = {},
                    onAnswerEarpieceHeard = {},
                    onDeclineEarpieceAnswer = {},
                    onPlayBack = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNode(isDialog()).captureRoboImage("$outputDir/audio-12-earpiece-question.png")
    }

    @Test
    fun a_dead_earpiece_names_the_consequence_that_actually_matters() {
        // Not "no sound" but "no private calls": every call on speakerphone, in public.
        render(
            "13-earpiece-dead",
            AudioTestUiState(
                stage = AudioStage.FINISHED,
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                speaker = SpeakerCheck.evaluate(toneRatio = 0.6f, roomFloor = 0.01f),
                earpiece = EarpieceCheck.evaluate(
                    routing = EarpieceRouting.CONFIRMED,
                    toneRatio = 0.01f,
                    roomFloor = 0.01f,
                    heard = HeardTone.NO,
                ),
                volume = MediaVolume(current = 12, max = 15),
            ),
        )
    }

    @Test
    fun a_phone_that_would_not_route_to_its_earpiece_says_so() {
        // The honest dead end, and the one that protects the seller: nothing was tested, so nothing is
        // claimed, and the buyer is told the only way left to check it.
        render(
            "14-earpiece-refused",
            AudioTestUiState(
                stage = AudioStage.FINISHED,
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                speaker = SpeakerCheck.evaluate(toneRatio = 0.6f, roomFloor = 0.01f),
                earpiece = EarpieceCheck.evaluate(EarpieceRouting.REFUSED),
                volume = MediaVolume(current = 12, max = 15),
            ),
        )
    }
}
