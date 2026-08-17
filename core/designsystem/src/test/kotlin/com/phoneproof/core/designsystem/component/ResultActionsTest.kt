package com.phoneproof.core.designsystem.component

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The two buttons that end every test.
 *
 * The interesting assertions here are about the way out. A result screen used to offer only "Test
 * again", so the sole exit was the system back gesture — which on a phone held by a stranger in a shop
 * is neither obvious nor reliable to perform.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ResultActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var retests = 0
    private var backs = 0

    private fun show(label: String = "Test again", withOnBack: Boolean = true) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ResultActions(
                    retestLabel = label,
                    onRetest = { retests++ },
                    onBack = if (withOnBack) ({ backs++ }) else null,
                )
            }
        }
    }

    @Test
    fun both_ways_out_of_a_result_screen_are_present_and_clickable() {
        show()

        composeRule.onNodeWithText("Back").assertHasClickAction()
        composeRule.onNodeWithText("Test again").assertHasClickAction()
    }

    @Test
    fun back_goes_back_and_does_not_rerun_the_test() {
        // The two must not be confusable. Re-running a test the buyer wanted to leave would cost them
        // the result they were looking at.
        show()

        composeRule.onNodeWithText("Back").performClick()

        assertThat(backs).isEqualTo(1)
        assertThat(retests).isEqualTo(0)
    }

    @Test
    fun retest_reruns_the_test_and_does_not_navigate() {
        show()

        composeRule.onNodeWithText("Test again").performClick()

        assertThat(retests).isEqualTo(1)
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun with_no_callback_the_back_button_falls_through_to_the_system_dispatcher_without_crashing() {
        // The default path used by all twelve screens: no lambda is threaded down from navigation, so
        // this button and the system back gesture are the same call and cannot drift apart. What is
        // asserted here is that the fallback is safe when nothing is listening.
        show(withOnBack = false)

        composeRule.onNodeWithText("Back").performClick()

        assertThat(retests).isEqualTo(0)
    }

    @Test
    fun the_retest_label_is_the_callers_words() {
        // The scan screen says "Scan again"; everything else says "Test again". One component, and the
        // wording still belongs to the screen.
        show(label = "Scan again")

        composeRule.onNodeWithText("Scan again").assertHasClickAction()
    }

    @Test
    fun tapping_retest_more_than_once_still_reaches_the_screen_each_time() {
        // The pulse stops after the first tap. That must not also stop the button working: an internal
        // "already tapped" flag guarding the animation is easy to accidentally wire to the click.
        show()

        composeRule.onNodeWithText("Test again").performClick()
        composeRule.onNodeWithText("Test again").performClick()

        assertThat(retests).isEqualTo(2)
    }
}
