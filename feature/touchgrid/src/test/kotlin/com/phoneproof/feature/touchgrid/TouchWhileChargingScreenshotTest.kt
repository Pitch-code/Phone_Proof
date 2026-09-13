package com.phoneproof.feature.touchgrid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.touch.GhostTouchCheck
import com.phoneproof.checks.touch.TouchWhileChargingCheck
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every state of the touch-while-charging watch.
 *
 * The verdict here never rises above CAUTION on purpose — the charger is a suspect the buyer is not buying —
 * so the copy has to lead with "try another charger" rather than "walk away". These renders exist so that
 * balance can be read rather than assumed, and the results are built from the real
 * [TouchWhileChargingCheck] so the screen and the verdict cannot drift apart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class TouchWhileChargingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(name: String, state: ChargeTouchUiState, themeMode: ThemeMode = ThemeMode.DARK) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                TouchWhileChargingScreen(
                    state = state,
                    onStart = {},
                    onContact = { _, _ -> },
                    onStop = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    private fun watch(watchedMillis: Long, events: Int, chargerThroughout: Boolean = true) =
        TouchWhileChargingCheck.Watch(
            watchedMillis = watchedMillis,
            contacts = (0 until events).map {
                GhostTouchCheck.Contact(atMillis = it * 1_000L, xFraction = 0.7f, yFraction = 0.8f)
            },
            chargerConnectedThroughout = chargerThroughout,
        )

    @Test
    fun waiting_for_a_charger() {
        render("charge-touch-1-needs-charger", ChargeTouchUiState())
    }

    @Test
    fun charger_in_ready_to_start() {
        render(
            "charge-touch-2-ready",
            ChargeTouchUiState(stage = ChargeTouchStage.READY, plugged = true),
        )
    }

    @Test
    fun watching_on_the_charger_clean() {
        render(
            "charge-touch-3-watching",
            ChargeTouchUiState(
                stage = ChargeTouchStage.WATCHING,
                plugged = true,
                watchedMillis = 9_000,
                contactCount = 0,
            ),
        )
    }

    @Test
    fun a_phantom_touch_under_charge() {
        render(
            "charge-touch-4-caught",
            ChargeTouchUiState(
                stage = ChargeTouchStage.WATCHING,
                plugged = true,
                watchedMillis = 14_000,
                contactCount = 2,
            ),
        )
    }

    @Test
    fun found_something_try_the_charger_first() {
        render(
            "charge-touch-5-caution",
            ChargeTouchUiState(
                stage = ChargeTouchStage.DONE,
                result = TouchWhileChargingCheck.evaluate(
                    watch(TouchWhileChargingCheck.FULL_WATCH_MILLIS, events = 2),
                ),
            ),
        )
    }

    @Test
    fun quiet_on_the_charger_is_a_cautious_pass() {
        render(
            "charge-touch-6-pass",
            ChargeTouchUiState(
                stage = ChargeTouchStage.DONE,
                result = TouchWhileChargingCheck.evaluate(
                    watch(TouchWhileChargingCheck.FULL_WATCH_MILLIS, events = 0),
                ),
            ),
        )
    }

    @Test
    fun the_cable_came_out_partway() {
        // The result that only this check can give: the one condition it controls for was not held.
        render(
            "charge-touch-7-cable-gone",
            ChargeTouchUiState(
                stage = ChargeTouchStage.DONE,
                result = TouchWhileChargingCheck.evaluate(
                    watch(9_000, events = 1, chargerThroughout = false),
                ),
            ),
        )
    }

    @Test
    fun waiting_for_a_charger_in_light() {
        render("charge-touch-8-needs-charger-light", ChargeTouchUiState(), ThemeMode.LIGHT)
    }
}
