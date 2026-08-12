package com.phoneproof.feature.screentest

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.device.ScreenDefectCheck
import com.phoneproof.checks.device.ScreenFinding
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenTestScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(name: String, state: ScreenTestUiState) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ScreenTestScreen(
                    state = state,
                    onStart = {},
                    onPatternSeen = {},
                    onStopEarly = {},
                    onAnswer = {},
                    onRetest = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun intro_warns_about_dust_before_starting() {
        render("screentest-1-intro", ScreenTestUiState())
    }

    @Test
    fun a_white_pattern_fills_the_screen() {
        // The render that proves the point of this screen: the colour must reach every edge, with
        // only the small corner hint drawn over it.
        render(
            "screentest-2-white",
            ScreenTestUiState(phase = ScreenTestPhase.PATTERN, index = 0, viewed = 0),
        )
    }

    @Test
    fun a_dark_pattern_flips_the_hint_to_light_ink() {
        // Black is index 1. Hint text has to invert or it vanishes into the pattern.
        render(
            "screentest-3-black",
            ScreenTestUiState(phase = ScreenTestPhase.PATTERN, index = 1, viewed = 1),
        )
    }

    @Test
    fun the_question_lists_clean_first() {
        render(
            "screentest-4-question",
            ScreenTestUiState(phase = ScreenTestPhase.QUESTION, index = 5, viewed = 6),
        )
    }

    @Test
    fun burn_in_reported_as_a_failure() {
        render(
            "screentest-5-burn-in",
            ScreenTestUiState(
                phase = ScreenTestPhase.FINISHED,
                index = 5,
                viewed = 6,
                result = ScreenDefectCheck.evaluate(ScreenFinding.LARGE_PATCHES, 6, 6),
            ),
        )
    }

    @Test
    fun an_abandoned_run_reports_unknown_rather_than_a_pass() {
        render(
            "screentest-6-incomplete",
            ScreenTestUiState(
                phase = ScreenTestPhase.FINISHED,
                index = 1,
                viewed = 2,
                result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, 2, 6),
            ),
        )
    }
}
