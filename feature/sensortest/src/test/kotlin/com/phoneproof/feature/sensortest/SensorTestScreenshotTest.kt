package com.phoneproof.feature.sensortest

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.sensors.SensorCheck
import com.phoneproof.checks.sensors.SensorKind
import com.phoneproof.checks.sensors.SensorLiveness
import com.phoneproof.checks.sensors.SensorReading
import com.phoneproof.checks.sensors.SensorTrace
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the live sensor test.
 *
 * Every state here needs real hardware to reach — a half-filled tilt meter needs a hand, and a dead
 * gyroscope needs a broken phone — so the states are constructed instead. The traces are the same
 * synthetic ones the analysis is tested against, which means these renders show the copy a buyer would
 * actually be shown for those readings rather than placeholder text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SensorTestScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private val everything = SensorKind.entries.toSet()

    @Test
    fun the_two_gestures_are_explained_before_anything_starts() {
        render("sensors-1-ready", SensorTestUiState(available = everything))
    }

    @Test
    fun the_tilt_meter_partway_through() {
        render(
            "sensors-2-tilting",
            SensorTestUiState(
                phase = SensorPhase.MOTION,
                available = everything,
                secondsLeft = 5,
                tiltProgress = 0.55f,
                turnProgress = 0.2f,
            ),
        )
    }

    @Test
    fun both_meters_full_so_the_verdict_cannot_come_back_as_a_shrug() {
        render(
            "sensors-3-motion-done",
            SensorTestUiState(
                phase = SensorPhase.MOTION,
                available = everything,
                secondsLeft = 3,
                tiltProgress = 1f,
                turnProgress = 1f,
                gestureComplete = true,
            ),
        )
    }

    @Test
    fun a_phone_with_no_gyroscope_is_told_why_there_is_only_one_meter() {
        render(
            "sensors-4-no-gyroscope",
            SensorTestUiState(
                phase = SensorPhase.MOTION,
                available = everything - SensorKind.GYROSCOPE,
                secondsLeft = 6,
                tiltProgress = 0.8f,
            ),
        )
    }

    @Test
    fun the_cover_phase_with_one_of_the_two_satisfied() {
        render(
            "sensors-5-covering",
            SensorTestUiState(
                phase = SensorPhase.COVER,
                available = everything,
                secondsLeft = 4,
                proximityFelt = true,
                lightWentDark = false,
            ),
        )
    }

    @Test
    fun everything_alive() {
        render(
            "sensors-6-all-pass",
            SensorTestUiState(
                phase = SensorPhase.DONE,
                available = everything,
                results = resultsFor(
                    accelerometerTilted(),
                    gyroscopeTurning(),
                    magnetometerNormal(),
                    proximityCovered(),
                    lightCovered(),
                ),
            ),
        )
    }

    @Test
    fun a_dead_gyroscope_named_with_the_witness_that_convicted_it() {
        render(
            "sensors-7-dead-gyroscope",
            SensorTestUiState(
                phase = SensorPhase.DONE,
                available = everything,
                results = resultsFor(
                    accelerometerTilted(),
                    gyroscopeAtRest(),
                    magnetometerNormal(),
                    proximityCovered(),
                    lightCovered(),
                ),
            ),
        )
    }

    @Test
    fun a_dead_proximity_sensor_which_is_the_expensive_one_to_find_out_about_later() {
        render(
            "sensors-8-dead-proximity",
            SensorTestUiState(
                phase = SensorPhase.DONE,
                available = everything,
                results = resultsFor(
                    accelerometerTilted(),
                    gyroscopeTurning(),
                    magnetometerNormal(),
                    SensorTrace(SensorKind.PROXIMITY, List(60) { SensorReading(5f) }),
                    lightCovered(),
                ),
            ),
        )
    }

    @Test
    fun a_buyer_who_did_nothing_is_told_so_rather_than_shown_five_faults() {
        // The state this whole module is arranged around. Every sensor on this phone works.
        render(
            "sensors-9-nothing-done",
            SensorTestUiState(
                phase = SensorPhase.DONE,
                available = everything,
                results = resultsFor(
                    SensorTrace(SensorKind.ACCELEROMETER, List(60) { SensorReading(0f, 0f, 9.81f) }),
                    gyroscopeAtRest(),
                    magnetometerNormal(),
                    SensorTrace(SensorKind.PROXIMITY, List(60) { SensorReading(5f) }),
                    SensorTrace(SensorKind.LIGHT, List(60) { SensorReading(300f) }),
                ),
            ),
        )
    }

    @Test
    fun the_lower_half_of_a_finished_test() {
        render(
            "sensors-10-all-pass-scrolled",
            SensorTestUiState(
                phase = SensorPhase.DONE,
                available = everything,
                results = resultsFor(
                    accelerometerTilted(),
                    gyroscopeTurning(),
                    magnetometerNormal(),
                    proximityCovered(),
                    lightCovered(),
                ),
            ),
            scrollTo = "Light sensor",
        )
    }

    @Test
    fun the_tilt_meter_in_light_mode() {
        render(
            "sensors-11-tilting-light",
            SensorTestUiState(
                phase = SensorPhase.MOTION,
                available = everything,
                secondsLeft = 5,
                tiltProgress = 0.55f,
                turnProgress = 1f,
            ),
            themeMode = ThemeMode.LIGHT,
        )
    }

    // ------------------------------------------------------------------ plumbing

    private fun resultsFor(vararg traces: SensorTrace) =
        SensorCheck.results(SensorLiveness.analyse(traces.toList(), everything))

    private fun render(
        name: String,
        state: SensorTestUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
        scrollTo: String? = null,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                SensorTestScreen(
                    state = state,
                    onStart = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // onRoot captures only what is on screen, so anything below the fold goes unreviewed unless it
        // is scrolled into view first.
        scrollTo?.let { composeRule.onNodeWithText(it).performScrollTo() }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    // The same synthetic traces the analysis is tested against, so these renders show the real copy.

    private fun accelerometerTilted(samples: Int = 60) = SensorTrace(
        kind = SensorKind.ACCELEROMETER,
        readings = List(samples) { i ->
            val angle = (i.toFloat() / (samples - 1)) * (Math.PI / 2).toFloat()
            SensorReading(
                x = 9.81f * kotlin.math.sin(angle),
                y = 0.02f,
                z = 9.81f * kotlin.math.cos(angle),
            )
        },
    )

    private fun gyroscopeTurning(samples: Int = 60) = SensorTrace(
        kind = SensorKind.GYROSCOPE,
        readings = List(samples) { i ->
            SensorReading(x = 0.01f, y = 0.01f, z = 1.4f * kotlin.math.sin(i * 0.2f))
        },
    )

    private fun gyroscopeAtRest(samples: Int = 60) = SensorTrace(
        kind = SensorKind.GYROSCOPE,
        readings = List(samples) { i ->
            val jitter = if (i % 2 == 0) 0.004f else -0.004f
            SensorReading(x = jitter, y = jitter, z = jitter)
        },
    )

    private fun magnetometerNormal(samples: Int = 60) = SensorTrace(
        kind = SensorKind.MAGNETOMETER,
        readings = List(samples) { i -> SensorReading(x = 22f + i * 0.02f, y = -14f, z = 40f) },
    )

    private fun proximityCovered() = SensorTrace(
        kind = SensorKind.PROXIMITY,
        readings = List(20) { SensorReading(5f) } +
            List(20) { SensorReading(0f) } +
            List(20) { SensorReading(5f) },
    )

    private fun lightCovered() = SensorTrace(
        kind = SensorKind.LIGHT,
        readings = List(20) { SensorReading(320f) } +
            List(20) { SensorReading(4f) } +
            List(20) { SensorReading(318f) },
    )
}
