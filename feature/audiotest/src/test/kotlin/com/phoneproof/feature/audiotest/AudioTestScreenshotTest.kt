package com.phoneproof.feature.audiotest

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.media.AudioAnalysis
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
        // Measure-then-ask, rendered. "No" must not look more dangerous than "Yes": making the honest
        // answer look alarming is how a buyer gets nudged into the reassuring one.
        render(
            "4-asking",
            AudioTestUiState(
                stage = AudioStage.ASKING,
                levels = spokenLevels(),
                microphone = MicrophoneCheck.evaluate(AudioAnalysis(0.012f, 0.32f, 0.4f, false, 60)),
                speaker = SpeakerCheck.evaluate(toneRatio = 0.04f, roomFloor = 0.3f),
                volume = MediaVolume(current = 12, max = 15),
            ),
        )
    }

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
}
