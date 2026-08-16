package com.phoneproof.feature.buttons

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.buttons.ButtonObservation
import com.phoneproof.checks.buttons.PressedBoth
import com.phoneproof.checks.buttons.VolumeButtonCheck
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The volume-button screen.
 *
 * A jammed key is not something a test can press, and it is the state most worth looking at — so the
 * observations are constructed. The screen being a pure function of them is what makes that possible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class VolumeButtonsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun pressed(times: Int = 1) =
        ButtonObservation(presses = times, releases = times, longestHoldMillis = 130L)

    private fun jammed() = ButtonObservation(
        presses = 1,
        releases = 0,
        longestHoldMillis = VolumeButtonCheck.STUCK_HOLD_MILLIS + 800L,
    )

    @Test
    fun waiting_for_the_first_press() {
        // The line about the volume not changing is the most important thing on this screen: without it, an
        // app that swallows the presses looks exactly like a phone with two dead buttons.
        render("buttons-1-waiting", VolumeButtonsUiState())
    }

    @Test
    fun one_button_heard_and_the_other_still_waiting() {
        render(
            "buttons-2-one-down",
            VolumeButtonsUiState(up = pressed(), down = ButtonObservation()),
        )
    }

    @Test
    fun a_key_being_held_right_now() {
        // Said live, while it is happening. A buyer whose finger is off the phone reads this as the app
        // telling them the key is stuck — which is exactly what it is telling them.
        render(
            "buttons-3-held-now",
            VolumeButtonsUiState(up = pressed(), down = jammed()),
        )
    }

    @Test
    fun both_buttons_working() {
        render(
            "buttons-4-pass",
            VolumeButtonsUiState(
                stage = VolumeStage.DONE,
                up = pressed(times = 2),
                down = pressed(),
                result = VolumeButtonCheck.evaluate(pressed(times = 2), pressed()),
            ),
        )
    }

    @Test
    fun a_jammed_key_names_recovery_mode_and_screenshots() {
        render(
            "buttons-5-jammed",
            VolumeButtonsUiState(
                stage = VolumeStage.DONE,
                up = pressed(),
                down = jammed(),
                result = VolumeButtonCheck.evaluate(pressed(), jammed()),
            ),
        )
    }

    @Test
    fun a_dead_button_confirmed_by_the_buyer() {
        render(
            "buttons-6-dead-key",
            VolumeButtonsUiState(
                stage = VolumeStage.DONE,
                up = pressed(),
                down = ButtonObservation(),
                result = VolumeButtonCheck.evaluate(
                    up = pressed(),
                    down = ButtonObservation(),
                    pressedBoth = PressedBoth.YES,
                ),
            ),
        )
    }

    @Test
    fun nothing_heard_at_all_blames_the_app_and_not_the_phone() {
        // The case this whole check is arranged around. Two silent buttons are at least as likely to be the
        // app failing to receive the keys, and there is nothing here to tell those apart.
        render(
            "buttons-7-nothing-heard",
            VolumeButtonsUiState(
                stage = VolumeStage.DONE,
                result = VolumeButtonCheck.evaluate(ButtonObservation(), ButtonObservation()),
            ),
        )
    }

    @Test
    fun the_question_when_one_button_stayed_silent() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                VolumeButtonsScreen(
                    state = VolumeButtonsUiState(
                        stage = VolumeStage.ASKING,
                        up = pressed(),
                        down = ButtonObservation(),
                    ),
                    onAnswerPressedBoth = {},
                    onFinish = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNode(isDialog()).captureRoboImage("$outputDir/buttons-8-question.png")
    }

    @Test
    fun waiting_in_light_mode() {
        render("buttons-9-waiting-light", VolumeButtonsUiState(up = pressed()), ThemeMode.LIGHT)
    }

    private fun render(
        name: String,
        state: VolumeButtonsUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                VolumeButtonsScreen(
                    state = state,
                    onAnswerPressedBoth = {},
                    onFinish = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }
}
