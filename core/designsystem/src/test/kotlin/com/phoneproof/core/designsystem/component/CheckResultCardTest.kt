package com.phoneproof.core.designsystem.component

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A result card is one thing to hear, not eight.
 *
 * This is invisible, so it is tested rather than eyeballed: a screenshot of the card is identical before and
 * after. What changed is that a screen reader used to stop on every line of it — title, the badge glyph read
 * as "multiplication sign", the SHOUTED outcome word, the category, the headline, the consequence, the
 * action, each measurement label and value separately, and the caveat. A dozen swipes to hear one verdict,
 * with the outcome buried in the middle. Now the card is a single node whose spoken form is composed in
 * reading order, outcome first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CheckResultCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val failing = CheckResult(
        id = "hardware.ghost-touch",
        title = "Touches nobody made",
        outcome = CheckOutcome.FAIL,
        confidence = Confidence.HIGH,
        headline = "The screen registered 3 touches nobody made",
        consequence = "A screen that taps itself opens apps and answers calls in a pocket.",
        action = "Get the screen quoted and take that off, or walk away.",
        measurements = listOf(
            Measurement("Watched for", "30", "s"),
            Measurement("Touches nobody made", "3"),
        ),
        falsePositiveCauses = listOf("A finger resting on the edge of the screen."),
    )

    @Test
    fun the_spoken_summary_leads_with_the_check_and_then_its_verdict() {
        // The outcome is what a buyer swiping through a dozen verdicts is listening for, so it comes second,
        // right after the name — not left to fall out of a glyph halfway down.
        val spoken = failing.spokenSummary()
        assertThat(spoken).startsWith("Touches nobody made. Problem.")
    }

    @Test
    fun the_spoken_summary_carries_the_whole_verdict_in_reading_order() {
        val spoken = failing.spokenSummary()
        // Everything a sighted reader sees, in the order they see it, and a measurement tied to its label.
        assertThat(spoken).contains(failing.headline)
        assertThat(spoken).contains("A screen that taps itself")
        assertThat(spoken).contains("Get the screen quoted")
        assertThat(spoken).contains("Watched for, 30 s")
        assertThat(spoken).contains("Could this be wrong? A finger resting")
    }

    @Test
    fun a_can_t_tell_result_never_speaks_the_word_unknown() {
        val unknown = CheckResult(
            id = "hardware.imei",
            title = "IMEI",
            outcome = CheckOutcome.UNKNOWN,
            confidence = Confidence.HIGH,
            headline = "Android will not reveal the IMEI to an app.",
        )
        // "Can't tell", matching the badge — a limit of Android, not a defect in the phone.
        assertThat(unknown.spokenSummary()).contains("Can't tell")
        assertThat(unknown.spokenSummary().lowercase()).doesNotContain("unknown")
    }

    @Test
    fun the_card_is_a_single_node_and_the_fragments_are_gone() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) { CheckResultCard(failing) }
        }

        // The whole verdict is reachable as one node...
        composeRule.onNodeWithContentDescription(failing.spokenSummary()).assertExists()

        // ...and the pieces that used to each be their own stop no longer are: the badge word, the
        // headline, and a measurement value are all merged away rather than separately focusable.
        composeRule.onNodeWithText("PROBLEM").assertDoesNotExist()
        composeRule.onNodeWithText(failing.headline).assertDoesNotExist()
        composeRule.onNodeWithText("Could this be wrong? A finger resting on the edge of the screen.")
            .assertDoesNotExist()
    }
}
