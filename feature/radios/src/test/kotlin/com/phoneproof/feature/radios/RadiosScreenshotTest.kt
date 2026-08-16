package com.phoneproof.feature.radios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.radios.RadioKind
import com.phoneproof.checks.radios.RadioObservation
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
class RadiosScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun wifi(
        present: Boolean = true,
        enabled: Boolean = true,
        associated: Boolean = true,
        internetWorking: Boolean = true,
        signalDbm: Int? = -54,
    ) = RadioObservation(
        kind = RadioKind.WIFI,
        present = present,
        enabled = enabled,
        associated = associated,
        internetWorking = internetWorking,
        signalDbm = signalDbm,
    )

    private fun bluetooth(
        present: Boolean = true,
        enabled: Boolean = true,
        stateReadable: Boolean = true,
    ) = RadioObservation(
        kind = RadioKind.BLUETOOTH,
        present = present,
        enabled = enabled,
        stateReadable = stateReadable,
    )

    private fun state(
        wifi: RadioObservation = wifi(),
        bluetooth: RadioObservation = bluetooth(),
        stage: RadiosStage = RadiosStage.WATCHING,
        wifiVisited: Boolean = false,
        bluetoothVisited: Boolean = false,
        wifiClaim: Boolean? = null,
        bluetoothClaim: Boolean? = null,
    ) = RadiosUiState(
        stage = stage,
        wifi = RadioPanel(
            kind = RadioKind.WIFI,
            observation = wifi,
            visitedSettings = wifiVisited,
            enableClaim = wifiClaim,
        ),
        bluetooth = RadioPanel(
            kind = RadioKind.BLUETOOTH,
            observation = bluetooth,
            visitedSettings = bluetoothVisited,
            enableClaim = bluetoothClaim,
        ),
    )

    @Test
    fun both_radios_off_as_the_screen_opens() {
        // The commonest opening state on a shop demo phone, and the one where the screen has to explain what
        // it wants without accusing the handset of anything.
        render(
            "radios-1-both-off",
            state(
                wifi = wifi(enabled = false, associated = false, internetWorking = false, signalDbm = null),
                bluetooth = bluetooth(enabled = false),
            ),
        )
    }

    @Test
    fun wifi_on_but_not_joined_to_anything() {
        // The state that separates this check from every competitor: on is not proof, joined is.
        render(
            "radios-2-wifi-not-joined",
            state(wifi = wifi(associated = false, internetWorking = false, signalDbm = null)),
        )
    }

    @Test
    fun both_radios_proved() {
        render("radios-3-both-proved", state())
    }

    @Test
    fun joined_a_network_that_has_no_internet() {
        // Must still read as a pass. The radio did its job; the shop's broadband is not the phone's fault.
        render("radios-4-no-internet", state(wifi = wifi(internetWorking = false)))
    }

    @Test
    fun asking_whether_the_buyer_actually_flipped_the_switch() {
        // The only route to a negative verdict here, so both answers get identical weight on screen.
        render(
            "radios-5-asking",
            state(
                wifi = wifi(enabled = false, associated = false, internetWorking = false, signalDbm = null),
                wifiVisited = true,
            ),
        )
    }

    @Test
    fun a_radio_that_was_switched_on_and_stayed_off() {
        render(
            "radios-6-refused",
            state(
                wifi = wifi(enabled = false, associated = false, internetWorking = false, signalDbm = null),
                wifiVisited = true,
                wifiClaim = true,
            ),
        )
    }

    @Test
    fun hardware_the_phone_says_it_does_not_have() {
        render(
            "radios-7-absent",
            state(
                wifi = wifi(present = false, enabled = false, associated = false, internetWorking = false, signalDbm = null),
                bluetooth = bluetooth(present = false, enabled = false),
            ),
        )
    }

    @Test
    fun the_saved_results_for_two_healthy_radios() {
        // Two cards, because the report has to be able to say Wi-Fi is proved and Bluetooth only partly.
        render("radios-8-done", state(stage = RadiosStage.DONE))
    }

    @Test
    fun the_saved_results_when_one_radio_refused() {
        render(
            "radios-9-done-mixed",
            state(
                stage = RadiosStage.DONE,
                bluetooth = bluetooth(enabled = false),
                bluetoothVisited = true,
                bluetoothClaim = true,
            ),
        )
    }

    @Test
    fun both_radios_off_in_light_mode() {
        render(
            "radios-10-both-off-light",
            state(
                wifi = wifi(enabled = false, associated = false, internetWorking = false, signalDbm = null),
                bluetooth = bluetooth(enabled = false),
            ),
            ThemeMode.LIGHT,
        )
    }

    @Test
    fun the_saved_results_in_light_mode() {
        render("radios-11-done-light", state(stage = RadiosStage.DONE), ThemeMode.LIGHT)
    }

    private fun render(
        name: String,
        state: RadiosUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                RadiosScreen(
                    state = state,
                    onOpenSettings = {},
                    onAnswerEnableClaim = { _, _ -> },
                    onFinish = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }
}
