package com.phoneproof.feature.cameratest

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
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
        // Real-looking specs, and a 90 degree sensor mounting, which is what almost every phone has. The
        // rotation is the part most likely to be wrong on hardware, so it has to be in the render.
        CameraInfo(
            id = "0",
            facing = CameraFacing.BACK,
            hasFlash = true,
            sensorMegapixels = 50.3f,
            largestPhotoMegapixels = 12.6f,
            maxZoom = 10f,
            sensorOrientation = 90,
        ),
        CameraInfo(
            id = "1",
            facing = CameraFacing.FRONT,
            hasFlash = false,
            sensorMegapixels = 8.0f,
            largestPhotoMegapixels = 8.0f,
            maxZoom = 4f,
            sensorOrientation = 270,
        ),
    )

    private val backStats = CameraFrameStats(
        facing = CameraFacing.BACK,
        framesReceived = 8,
        meanLuma = 0.42f,
        lumaVariation = 0.21f,
        framesIdentical = false,
        sensorMegapixels = 50.3f,
        largestPhotoMegapixels = 12.6f,
        maxZoom = 10f,
    )

    private val frontStats = CameraFrameStats(
        facing = CameraFacing.FRONT,
        framesReceived = 8,
        meanLuma = 0.38f,
        lumaVariation = 0.17f,
        framesIdentical = false,
        sensorMegapixels = 8.0f,
        largestPhotoMegapixels = 8.0f,
        maxZoom = 4f,
    )

    /**
     * A frame with a marker in a known place, so the render proves the rotation is right.
     *
     * The first version put a bright block flush into one corner. It rendered correctly and looked like a
     * clipping bug — the block's own square edges read as a broken rounded corner — which made the review
     * harder rather than easier. A test fixture that looks like a defect is worse than no fixture.
     *
     * So: a soft gradient, one vertical bar, and a bright disc inset in the upper-left quadrant. The disc
     * never touches the clip, and where it ends up on screen says which way the frame was turned — upper
     * left means no rotation, upper right means 90 degrees, and so on.
     */
    private fun sceneFrame(width: Int = 320, height: Int = 240): ImageBitmap {
        val discX = width / 4
        val discY = height / 4
        val discRadius = height / 8

        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width

            val gradient = 45 + (y * 110 / height)
            val bar = if (x in (width * 2 / 3)..(width * 2 / 3 + 8)) 165 else gradient
            val dx = x - discX
            val dy = y - discY
            val value = if (dx * dx + dy * dy < discRadius * discRadius) 240 else bar

            0xFF shl 24 or (value shl 16) or (value shl 8) or value
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /**
     * Renders the screen the way the app actually composes it, which it did not used to.
     *
     * `CameraTestScreen` is a bare `Column(fillMaxWidth())`: the scrolling and the window insets live in
     * `CameraTestRoute`, one level up. This test called the screen directly, so every camera render was
     * missing both — a shape the app never draws. In a tall portrait viewport that made no visible
     * difference, which is exactly why it went unnoticed; rendered in landscape it showed the heading
     * clipped off the top of the screen with no way to reach it, and that turned out to be an artefact of
     * this helper rather than a fault in the app.
     *
     * A render that flatters the screen is worse than no render, because the whole point of committing
     * these is to review what a buyer will see. So the container is reproduced here, and any future
     * clipping this shows will be real.
     */
    private fun render(name: String, state: CameraTestUiState, themeMode: ThemeMode = ThemeMode.LIGHT) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PhoneProofTheme.colors.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
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
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun in_landscape() {
        // Camera previews have a fixed aspect and no scroll container above them, which is the
        // combination most likely to overflow a short viewport.
        render("camera-10-landscape", CameraTestUiState(cameras = twoCameras))
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

    @Test
    fun the_camera_being_tested_shows_what_it_is_sending() {
        // The request was for a small window per camera showing what is being captured. This is the frame
        // arriving while the rear camera is still open — the same data the verdict is computed from, which
        // is why it is greyscale and why it is captioned as brightness only.
        render(
            "6-live-frame",
            CameraTestUiState(
                stage = CameraStage.TESTING,
                cameras = twoCameras,
                testing = CameraFacing.BACK.label,
                frames = mapOf(CameraFacing.BACK.label to sceneFrame()),
            ),
        )
    }

    @Test
    fun each_result_carries_its_own_camera_picture_and_specs() {
        // Two pictures and two verdicts, paired. Getting this wrong would put the front camera's frame above
        // the rear camera's result, which is worse than showing no picture at all.
        render(
            "7-frames-with-results",
            CameraTestUiState(
                stage = CameraStage.CAMERAS_DONE,
                cameras = twoCameras,
                results = listOf(
                    CameraCheck.evaluate(backStats),
                    CameraCheck.evaluate(frontStats),
                ),
                frames = mapOf(
                    CameraFacing.BACK.label to sceneFrame(),
                    CameraFacing.FRONT.label to sceneFrame(),
                ),
            ),
        )
    }

    @Test
    fun a_camera_that_would_not_open_still_reports_its_resolution() {
        // Characteristics need neither a permission nor an open camera, so the megapixel figure survives a
        // camera that refuses to start — and that is exactly when a buyer comparing the phone to its advert
        // still wants the number.
        render(
            "8-specs-without-frames",
            CameraTestUiState(
                stage = CameraStage.CAMERAS_DONE,
                cameras = twoCameras,
                results = listOf(
                    CameraCheck.evaluate(
                        backStats.copy(framesReceived = 0, meanLuma = 0f, lumaVariation = 0f),
                    ),
                ),
            ),
        )
    }

    @Test
    fun the_megapixel_note_never_accuses_the_seller() {
        // A phone advertised at 50 MP handing apps 12.6 is normal, not fraud: full-resolution modes are kept
        // for the manufacturer's own camera app. The note has to say so, because a buyer taking this to a
        // seller as proof would be wrong.
        assertThat(CameraCheck.specNote(backStats)).contains("not proof of anything")
        assertThat(CameraCheck.specNote(backStats)).contains("50.3 MP")
        assertThat(CameraCheck.specNote(frontStats.copy(sensorMegapixels = null))).isNull()
    }
}
