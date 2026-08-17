package com.phoneproof.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A prompt tied to a live condition.
 *
 * The important test here is the pass-through one. This component exists instead of a `Dialog` precisely
 * because a dialog would steal the touches it is drawn over, and on the multi-touch screen that would have
 * destroyed the measurement the prompt was announcing. That is a property, not an implementation detail, so
 * it is asserted rather than trusted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ConditionPromptTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var actions = 0

    @Composable
    private fun Harness(visible: Boolean, withAction: Boolean = false, onTouch: () -> Unit = {}) {
        PhoneProofTheme(themeMode = ThemeMode.DARK) {
            Box(Modifier.fillMaxSize()) {
                // Stands in for the touch pad: something underneath that must keep receiving touches.
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("underneath")
                        .clickable(onClick = onTouch),
                )
                ConditionPrompt(
                    visible = visible,
                    headline = "You can lift your fingers now",
                    detail = "This closes by itself when you let go.",
                    action = if (withAction) "There is no charger here" else null,
                    onAction = if (withAction) ({ actions++ }) else null,
                )
            }
        }
    }

    @Test
    fun nothing_is_shown_while_the_condition_is_false() {
        composeRule.setContent { Harness(visible = false) }

        composeRule.onNodeWithText("You can lift your fingers now").assertDoesNotExist()
    }

    @Test
    fun the_prompt_appears_while_the_condition_holds() {
        composeRule.setContent { Harness(visible = true) }

        composeRule.onNodeWithText("You can lift your fingers now").assertIsDisplayed()
    }

    @Test
    fun it_takes_itself_away_when_the_condition_clears() {
        // The whole point: no dismiss button, no timeout. It is a function of state, so it cannot be left
        // on screen after the thing it asked for has happened.
        var showing by mutableStateOf(true)
        composeRule.setContent { Harness(visible = showing) }
        composeRule.onNodeWithText("You can lift your fingers now").assertIsDisplayed()

        showing = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("You can lift your fingers now").assertDoesNotExist()
    }

    @Test
    fun a_prompt_with_no_action_does_not_swallow_the_touches_underneath_it() {
        // The reason this is not a Dialog. A dialog is a separate window: it would take these touches, and
        // on the multi-touch screen it would also cancel the gesture, making every finger report as lifted
        // and destroying the count the prompt was announcing.
        var touches = 0
        composeRule.setContent { Harness(visible = true, onTouch = { touches++ }) }

        composeRule.onNodeWithTag("underneath").performClick()

        assertThat(touches).isEqualTo(1)
    }

    @Test
    fun the_escape_route_still_works_when_one_is_offered() {
        // For a condition that may never clear on its own — no charger in the room — the way out lives in
        // the card. Only this button is interactive; the rest of the prompt still passes touches through.
        composeRule.setContent { Harness(visible = true, withAction = true) }

        composeRule.onNodeWithText("There is no charger here").performClick()

        assertThat(actions).isEqualTo(1)
    }

    @Test
    fun an_action_is_only_drawn_when_both_a_label_and_a_handler_are_given() {
        // Half-configured would otherwise render a dead button, which on the charging screen would look
        // like the only way out of a screen the app cannot finish by itself.
        composeRule.setContent { Harness(visible = true, withAction = false) }

        composeRule.onNodeWithText("There is no charger here").assertDoesNotExist()
    }
}
