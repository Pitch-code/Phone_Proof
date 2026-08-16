package com.phoneproof.feature.cameratest

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.media.CameraCheck
import com.phoneproof.checks.media.CameraFacing
import com.phoneproof.checks.media.CameraFrameStats
import com.phoneproof.checks.media.HeardTone
import com.phoneproof.checks.media.TorchCheck
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.media.CameraInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The camera screen, in states Robolectric cannot reach on its own.
 *
 * There is no camera here, so every result is constructed. That is only possible because the verdicts live
 * in `checks:media` and the capture in `core:media` — the same split that lets the flat-black-frame case be
 * reviewed without finding a phone with a dead sensor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CameraTestScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private val twoCameras = listOf(
        CameraInfo(id = "0", facing = CameraFacing.BACK, hasFlash = true),
        CameraInfo(id = "1", facing = CameraFacing.FRONT, hasFlash = false),
    )

    private fun render(name: String, state: CameraTestUiState, themeMode: ThemeMode = ThemeMode.LIGHT) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                CameraTestScreen(
                    state = state,
                    onTestCameras = {},
                    onLightTorch = {},
                    onAnswerLit = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/camera-$name.png")
    }

    @Test
    fun before_testing_anything() {
        // Has to say what it found and what to point the phone at. "Not a blank wall" is the instruction
        // that stops a working camera reporting a flat field.
        render("1-ready", CameraTestUiState(cameras = twoCameras))
    }

    @Test
    fun both_cameras_alive() {
        render(
            "2-both-pass",
            CameraTestUiState(
                stage = CameraStage.CAMERAS_DONE,
                cameras = twoCameras,
                results = listOf(
                    CameraCheck.evaluate(
                        CameraFrameStats(CameraFacing.BACK, 8, 0.42f, 0.21f, false),
                    ),
                    CameraCheck.evaluate(
                        CameraFrameStats(CameraFacing.FRONT, 8, 0.38f, 0.17f, false),
                    ),
                ),
            ),
        )
    }

    @Test
    fun a_finger_over_the_rear_lens() {
        // The most common real outcome that is not a fault, and the card has to blame the finger before
        // the sensor. CAUTION, never FAIL.
        render(
            "3-flat-black",
            CameraTestUiState(
                stage = CameraStage.CAMERAS_DONE,
                cameras = twoCameras,
                results = listOf(
                    CameraCheck.evaluate(
                        CameraFrameStats(CameraFacing.BACK, 8, 0.01f, 0.002f, false),
                    ),
                ),
            ),
        )
    }

    @Test
    fun the_torch_is_lit_and_the_buyer_is_asked() {
        // Measure-then-ask again. Both answers must be weighted identically — the app has no stake in
        // which is true, and "Yes" as a primary action would nudge toward the reassuring one.
        render(
            "4-torch-asking",
            CameraTestUiState(
                stage = CameraStage.TORCH_LIT,
                cameras = twoCameras,
                results = listOf(
                    CameraCheck.evaluate(CameraFrameStats(CameraFacing.BACK, 8, 0.42f, 0.21f, false)),
                ),
                torch = TorchCheck.evaluate(flashAvailable = true, accepted = true),
            ),
        )
    }

    @Test
    fun a_frozen_sensor_in_dark_mode() {
        // The subtle one: brightness and detail both look healthy while nothing changes between frames,
        // which a live sensor cannot do.
        render(
            "5-frozen-dark",
            CameraTestUiState(
                stage = CameraStage.FINISHED,
                cameras = twoCameras,
                results = listOf(
                    CameraCheck.evaluate(
                        CameraFrameStats(CameraFacing.BACK, 8, 0.40f, 0.30f, true),
                    ),
                ),
                torch = TorchCheck.evaluate(true, accepted = true, lit = HeardTone.NO),
            ),
            themeMode = ThemeMode.DARK,
        )
    }
}
