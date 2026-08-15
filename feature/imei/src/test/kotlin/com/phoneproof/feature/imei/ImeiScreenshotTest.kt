package com.phoneproof.feature.imei

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three states of a fifteen-digit form.
 *
 * Rendered because the wording is the substance of this screen. The arithmetic is covered
 * exhaustively in `checks:imei`; what cannot be unit tested is whether a buyer reading the pass card
 * comes away thinking the app has cleared the phone. That is a question about layout and emphasis, and
 * it can only be answered by looking.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ImeiScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(name: String, typed: String, themeMode: ThemeMode = ThemeMode.LIGHT) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                ImeiScreen(
                    typed = typed,
                    onTypedChanged = {},
                    onOpenCeir = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun nothing_entered_yet() {
        // The empty state has to teach *#06#, because a buyer who does not know that cannot start.
        render("imei-1-empty", typed = "")
    }

    @Test
    fun a_well_formed_number() {
        // The render that matters most. A green PASS on a screen with the word IMEI on it is the one
        // thing Play policy forbids implying, so the headline has to disclaim in the same breath and
        // the CEIR instruction has to survive next to a pass.
        render("imei-2-valid", typed = "490154203237518")
    }

    @Test
    fun the_checksum_does_not_add_up() {
        // One digit changed from the valid example. CAUTION rather than FAIL, because a typo and a
        // cloned handset look identical from here.
        render("imei-3-checksum-failed", typed = "490154203237510")
    }

    @Test
    fun half_typed_in_dark_mode() {
        // Mid-entry, and in dark, so the grouped digits and the count are checked in both palettes.
        render("imei-4-partial-dark", typed = "4901542032", themeMode = ThemeMode.DARK)
    }
}
