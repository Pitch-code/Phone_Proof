package com.phoneproof.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.licence.RedeemResult
import com.phoneproof.core.preferences.passes.InspectionPass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every answer the redeem screen can give.
 *
 * Rendered exhaustively because this screen is read under more pressure than any other in the app: the buyer
 * is holding somebody else's phone, the seller wants it back, and they have just typed something they paid
 * for. The wording of a failure here is the product, not decoration — so each one is looked at rather than
 * assumed to be fine.
 *
 * The offline case matters most. It is the expected failure of the whole design, it is not the buyer's fault,
 * and a message implying their paid code is invalid would be the worst thing this app could say.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RedeemScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(
        name: String,
        state: RedeemUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                RedeemScreen(
                    state = state,
                    onTypedChanged = {},
                    onRedeem = {},
                    onDone = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun an_empty_field_before_anything_is_typed() {
        // The button must be disabled here. A tappable button that can only fail is a small cruelty on a
        // screen somebody is using under pressure.
        render("redeem-1-empty", RedeemUiState())
    }

    @Test
    fun a_complete_code_ready_to_send() {
        render("redeem-2-ready", RedeemUiState(typed = "PP-N6WE-DKZE"))
    }

    @Test
    fun waiting_on_the_server() {
        render(
            "redeem-3-working",
            RedeemUiState(typed = "PP-N6WE-DKZE", stage = RedeemStage.WORKING),
        )
    }

    @Test
    fun unlocked() {
        render(
            "redeem-4-unlocked",
            RedeemUiState(
                typed = "PP-N6WE-DKZE",
                stage = RedeemStage.DONE,
                result = RedeemResult.Granted(
                    pass = InspectionPass(
                        code = "N6WEDKZE",
                        // Twelve hours from whenever this renders, so the label reads sensibly rather than
                        // depending on a fixed instant that is in the past by the time anyone looks.
                        expiresAtEpochMs = System.currentTimeMillis() + 12 * InspectionPass.HOUR_MILLIS,
                    ),
                    passesLeft = 4,
                    alreadyActive = false,
                ),
            ),
        )
    }

    @Test
    fun already_unlocked_and_told_that_it_cost_nothing() {
        // The fairness rule made visible. Reopening the app on the same phone inside the window must not
        // cost an inspection, and the screen says so rather than leaving the buyer to wonder.
        render(
            "redeem-5-already-active",
            RedeemUiState(
                typed = "PP-N6WE-DKZE",
                stage = RedeemStage.DONE,
                result = RedeemResult.Granted(
                    pass = InspectionPass(
                        code = "N6WEDKZE",
                        expiresAtEpochMs = System.currentTimeMillis() + 3 * InspectionPass.HOUR_MILLIS,
                    ),
                    passesLeft = 4,
                    alreadyActive = true,
                ),
            ),
        )
    }

    @Test
    fun a_mistyped_code_caught_without_a_network() {
        // Answered offline by the check character, which is the whole reason it exists. It also explains the
        // alphabet's substitutions, because "O reads as zero" is the fix for the commonest mistake.
        render(
            "redeem-6-malformed",
            RedeemUiState(typed = "PP-N6WE-DKZQ", result = RedeemResult.Malformed),
        )
    }

    @Test
    fun a_code_the_server_has_never_heard_of() {
        render(
            "redeem-7-unknown",
            RedeemUiState(typed = "PP-N6WE-DKZE", result = RedeemResult.Unknown),
        )
    }

    @Test
    fun a_pack_with_nothing_left_on_it() {
        // Not framed as an error, because it is not one: the buyer used what they bought. It also says what
        // still works, so the screen is not a dead end.
        render(
            "redeem-8-exhausted",
            RedeemUiState(typed = "PP-N6WE-DKZE", result = RedeemResult.Exhausted(0)),
        )
    }

    @Test
    fun no_signal_in_a_shop() {
        // The one that must not read like an accusation. "Your code is fine" is in the message on purpose,
        // and this render exists so that sentence cannot quietly disappear.
        render(
            "redeem-9-offline",
            RedeemUiState(typed = "PP-N6WE-DKZE", result = RedeemResult.Offline),
        )
    }

    @Test
    fun our_fault_rather_than_theirs() {
        render(
            "redeem-10-server-problem",
            RedeemUiState(typed = "PP-N6WE-DKZE", result = RedeemResult.ServerProblem),
        )
    }

    @Test
    fun the_empty_field_in_light_mode() {
        render("redeem-11-light", RedeemUiState(), ThemeMode.LIGHT)
    }
}
