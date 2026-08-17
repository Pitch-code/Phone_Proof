package com.phoneproof.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Both ways of reaching Settings, asserted rather than eyeballed.
 *
 * This exists because the bottom Settings row was deleted in the same change that added the gear to the
 * header, which nobody asked for and no test noticed — the screenshots simply showed a page that no
 * longer had it. A rendered PNG only catches that if somebody compares it against the previous one and
 * remembers what used to be there. These two assertions do not depend on anyone remembering.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HomeSettingsRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var settingsOpened = 0

    private fun show() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                HomeScreen(
                    onStartFullTest = {},
                    onOpenChecks = {},
                    onOpenGuide = {},
                    onOpenReports = {},
                    onOpenSettings = { settingsOpened++ },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun the_gear_in_the_header_opens_settings() {
        show()

        // Found by its content description, which is also the only label a screen reader gets for it.
        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(settingsOpened).isEqualTo(1)
    }

    @Test
    fun the_row_at_the_bottom_of_the_page_also_opens_settings() {
        show()

        // Scrolled to first: it is below the fold on a phone-sized screen, which is exactly why the gear
        // was added and not a reason to have removed this.
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()

        assertThat(settingsOpened).isEqualTo(1)
    }

    @Test
    fun the_bottom_row_says_what_is_inside_rather_than_relying_on_an_icon() {
        show()

        // The row's job that the gear cannot do. If this subtitle ever grows a claim the app does not
        // yet honour — a language picker, say — this is where it will be noticed.
        composeRule
            .onNodeWithText("Appearance, your shop's name on reports, and plans")
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun both_routes_are_clickable_and_neither_replaced_the_other() {
        show()

        // The regression stated directly: two independent affordances, not one moved from A to B.
        composeRule.onNodeWithContentDescription("Settings").assert(hasClickAction())
        composeRule.onNodeWithText("Settings").performScrollTo().assert(hasClickAction())

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        assertThat(settingsOpened).isEqualTo(2)
    }
}
