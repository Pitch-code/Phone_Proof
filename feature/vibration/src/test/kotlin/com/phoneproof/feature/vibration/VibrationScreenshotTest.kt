package com.phoneproof.feature.vibration

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.vibration.VibrationAttempt
import com.phoneproof.checks.vibration.VibrationCheck
import com.phoneproof.checks.vibration.VibrationTrace
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
class VibrationScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    @Test
    fun the_promise_before_it_starts() {
        // The line worth reviewing is "you will not be asked whether you felt it", which is what makes this
        // test different from the same test in every other app.
        render("vibration-1-ready", VibrationUiState())
    }

    @Test
    fun taking_the_baseline_with_a_steady_hand() {
        render(
            "vibration-2-resting-still",
            VibrationUiState(stage = VibrationStage.RESTING, liveJerk = 0.05),
        )
    }

    @Test
    fun taking_the_baseline_with_a_moving_hand() {
        // The buyer's cue to stop moving, given before the test can be spoiled rather than afterwards.
        render(
            "vibration-3-resting-restless",
            VibrationUiState(
                stage = VibrationStage.RESTING,
                liveJerk = VibrationCheck.TOO_RESTLESS + 0.4,
            ),
        )
    }

    @Test
    fun the_motor_proving_itself_in_front_of_the_buyer() {
        render(
            "vibration-4-buzzing",
            VibrationUiState(stage = VibrationStage.BUZZING, restingJerk = 0.05, liveJerk = 1.9),
        )
    }

    @Test
    fun a_motor_that_moved_the_phone() {
        render(
            "vibration-5-pass",
            done(
                VibrationTrace(
                    attempt = VibrationAttempt.MEASURED,
                    restingJerk = 0.04,
                    activeJerk = 1.85,
                    requestedMillis = 700L,
                    hasAmplitudeControl = true,
                ),
            ),
        )
    }

    @Test
    fun a_motor_that_did_not() {
        render(
            "vibration-6-no-movement",
            done(
                VibrationTrace(
                    attempt = VibrationAttempt.MEASURED,
                    restingJerk = 0.05,
                    activeJerk = 0.06,
                    requestedMillis = 700L,
                    hasAmplitudeControl = true,
                ),
            ),
        )
    }

    @Test
    fun a_phone_that_was_never_still_enough_to_judge() {
        render(
            "vibration-7-too-restless",
            done(
                VibrationTrace(
                    attempt = VibrationAttempt.MEASURED,
                    restingJerk = 1.4,
                    activeJerk = 5.2,
                    requestedMillis = 700L,
                ),
            ),
        )
    }

    @Test
    fun a_phone_with_no_accelerometer_to_feel_it_with() {
        // The premise of the check fails here, and it says so rather than falling back to asking.
        render("vibration-8-no-accelerometer", done(VibrationTrace(VibrationAttempt.NO_ACCELEROMETER)))
    }

    @Test
    fun ready_on_a_phone_with_no_motor() {
        render(
            "vibration-9-no-motor",
            VibrationUiState(hasMotor = false),
        )
    }

    @Test
    fun buzzing_in_light_mode() {
        render(
            "vibration-10-buzzing-light",
            VibrationUiState(stage = VibrationStage.BUZZING, restingJerk = 0.05, liveJerk = 1.9),
            ThemeMode.LIGHT,
        )
    }

    @Test
    fun the_app_admitting_its_own_bug_rather_than_blaming_the_phone() {
        // Rendered so the wording gets looked at. A real phone once saw the old version of this state and
        // was told to check its Do Not Disturb setting for a permission missing from the app's manifest.
        render(
            "vibration-11-app-fault",
            done(VibrationTrace(VibrationAttempt.NOT_PERMITTED, hasAmplitudeControl = true)),
        )
    }

    private fun done(trace: VibrationTrace) = VibrationUiState(
        stage = VibrationStage.DONE,
        restingJerk = trace.restingJerk,
        activeJerk = trace.activeJerk,
        result = VibrationCheck.evaluate(trace),
    )

    private fun render(
        name: String,
        state: VibrationUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                VibrationScreen(
                    state = state,
                    onStart = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }
}
