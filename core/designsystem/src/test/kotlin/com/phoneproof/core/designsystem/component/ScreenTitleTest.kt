package com.phoneproof.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two things a screen reader needs from a title and a decoration.
 *
 * Both are invisible properties, which is why they are tested rather than eyeballed: a screenshot of a
 * heading and a screenshot of a plain line of text are the same picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenTitleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun a_screen_title_announces_itself_as_a_heading() {
        // TalkBack's main way of skipping past text is "navigate by heading". With none declared, the only
        // way through a screen is to swipe through every word on it — eight cards of prose on the guide
        // before reaching the first control.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) { ScreenTitle("Fingers at once") }
        }

        composeRule.onNodeWithText("Fingers at once").assert(isHeading)
    }

    @Test
    fun a_screen_title_is_still_ordinary_visible_text() {
        // The semantics must not come at the cost of the thing being readable by everyone else.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) { ScreenTitle("Charging") }
        }

        composeRule.onNodeWithText("Charging").assertIsDisplayed()
    }

    @Test
    fun a_decorative_glyph_is_removed_from_the_traversal_entirely() {
        // Not merely given an empty description: an empty description still leaves a node to be stopped
        // on. The chevrons on navigation rows are the case this exists for — "greater-than sign" read out
        // inside a row whose own text already says where it goes.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                Column {
                    Text(text = "Saved reports")
                    Text(text = "›", modifier = Modifier.decorative())
                }
            }
        }

        composeRule.onNodeWithText("›").assertDoesNotExist()
        // ...while the row's real label is untouched.
        composeRule.onNodeWithText("Saved reports").assertIsDisplayed()
    }

    @Test
    fun a_title_is_not_accidentally_marked_decorative() {
        // Guards the obvious mix-up, since both are one-line modifiers applied to a Text.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) { ScreenTitle("Wi-Fi and Bluetooth") }
        }

        composeRule.onNodeWithText("Wi-Fi and Bluetooth").assertExists()
    }
}
