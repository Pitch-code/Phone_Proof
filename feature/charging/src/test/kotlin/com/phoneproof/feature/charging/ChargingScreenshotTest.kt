package com.phoneproof.feature.charging

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.device.ChargeAttempt
import com.phoneproof.checks.device.ChargeTrace
import com.phoneproof.checks.device.ChargingCheck
import com.phoneproof.checks.device.PlugType
import com.phoneproof.core.device.ChargeSample
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
class ChargingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun sample(
        plug: PlugType = PlugType.AC,
        charging: Boolean = true,
        percent: Int = 46,
        milliamps: Int? = 2_900,
    ) = ChargeSample(
        plugType = plug,
        charging = charging,
        percent = percent,
        voltageMillivolts = 4_180,
        currentMilliamps = milliamps,
        temperatureCelsius = 31.2,
    )

    private fun trace(
        attempt: ChargeAttempt = ChargeAttempt.MEASURED,
        milliamps: Int? = 2_900,
        percent: Int = 46,
        plug: PlugType = PlugType.AC,
        dropouts: Int = 0,
    ) = ChargeTrace(
        attempt = attempt,
        plugType = plug,
        batteryPercent = percent,
        voltageMillivolts = 4_180,
        currentMilliamps = milliamps,
        temperatureCelsius = 31.2,
        dropouts = dropouts,
        sampleSeconds = 20,
    )

    @Test
    fun waiting_for_a_cable_that_may_not_exist() {
        // The state no other check in this app has. It explains why the test is worth bothering with — the
        // loose socket, not the speed — because a buyer who thinks this is a benchmark will skip it.
        //
        // Nothing is plugged in here, so this render also covers the "please connect the charger" prompt
        // and the escape route inside it.
        render(
            "charging-1-waiting",
            ChargingUiState(live = sample(plug = PlugType.NONE, charging = false, milliamps = null)),
        )
    }

    @Test
    fun a_charger_already_plugged_in_when_the_screen_opens() {
        render("charging-2-plugged", ChargingUiState(live = sample()))
    }

    @Test
    fun watching_a_healthy_charge() {
        render(
            "charging-3-measuring",
            ChargingUiState(
                stage = ChargingStage.MEASURING,
                live = sample(),
                secondsLeft = 13,
            ),
        )
    }

    @Test
    fun the_socket_letting_go_while_the_test_is_still_running() {
        // Said live, before any verdict. A buyer watching this has found the fault themselves.
        render(
            "charging-4-dropping-out",
            ChargingUiState(
                stage = ChargingStage.MEASURING,
                live = sample(),
                secondsLeft = 6,
                dropouts = 2,
            ),
        )
    }

    @Test
    fun a_healthy_charge_reports_the_wattage() {
        render("charging-5-pass", done(trace()))
    }

    @Test
    fun a_loose_socket_outranks_the_speed() {
        render("charging-6-loose-socket", done(trace(dropouts = 2)))
    }

    @Test
    fun a_trickle_that_is_less_than_a_usb_port_provides() {
        render("charging-7-trickle", done(trace(milliamps = 300)))
    }

    @Test
    fun connected_and_not_charging_at_all() {
        render("charging-8-not-charging", done(trace(attempt = ChargeAttempt.PLUGGED_NOT_CHARGING)))
    }

    @Test
    fun no_charger_was_ever_connected() {
        render("charging-9-not-tested", done(trace(attempt = ChargeAttempt.NOT_PLUGGED)))
    }

    @Test
    fun a_full_battery_has_no_speed_to_measure() {
        render(
            "charging-10-full",
            done(trace(attempt = ChargeAttempt.BATTERY_FULL, percent = 100)),
        )
    }

    @Test
    fun charging_over_usb_names_the_port_as_the_limit() {
        render("charging-11-usb", done(trace(milliamps = 900, plug = PlugType.USB)))
    }

    @Test
    fun waiting_in_light_mode() {
        render(
            "charging-12-waiting-light",
            ChargingUiState(live = sample(plug = PlugType.NONE, charging = false, milliamps = null)),
            ThemeMode.LIGHT,
        )
    }

    private fun done(trace: ChargeTrace) = ChargingUiState(
        stage = ChargingStage.DONE,
        live = sample(percent = trace.batteryPercent),
        dropouts = trace.dropouts,
        result = ChargingCheck.evaluate(trace),
    )

    private fun render(
        name: String,
        state: ChargingUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                ChargingScreen(
                    state = state,
                    onGiveUp = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }
}
