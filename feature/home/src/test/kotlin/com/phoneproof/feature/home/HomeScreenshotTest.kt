package com.phoneproof.feature.home

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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    /**
     * Every check the app actually offers, taken from the list the navigation graph uses.
     *
     * This was a hand-written copy, and the copy fell five entries behind the real screen — the
     * microphone, the cameras and the IMEI were all missing from it while their PRs were reviewed
     * against these renders. Reading [HomeCatalogue] means a check added to Home cannot be left out of
     * the picture that is supposed to prove Home still fits.
     */
    private fun realChecks(): List<HomeCheck> =
        HomeCatalogue.map { HomeCheck(it.title, it.subtitle) {} }

    private fun render(name: String) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                HomeScreen(
                    checks = realChecks(),
                    onStartFullTest = {},
                    onOpenGuide = {},
                    onOpenReports = {},
                    onOpenSettings = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun home() {
        // A phone-sized viewport, so this shows what a buyer sees before scrolling.
        render("home")
    }

    @Test
    fun home_on_the_free_trial_with_scans_left() {
        // The counter had no render at all: every existing shot leaves freeScansLeft null, which is
        // the paid case, so the line a free user actually sees was never drawn.
        renderWithScans("home-3-scans-left", scansLeft = 2)
    }

    @Test
    fun home_with_the_trial_used_up() {
        // Amber, and worded as a state to act on rather than an error.
        renderWithScans("home-4-trial-used-up", scansLeft = 0)
    }

    private fun renderWithScans(name: String, scansLeft: Int) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                HomeScreen(
                    checks = realChecks(),
                    onStartFullTest = {},
                    onOpenGuide = {},
                    onOpenReports = {},
                    onOpenSettings = {},
                    freeScansLeft = scansLeft,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xhdpi")
    fun home_full_column() {
        // The whole scrolling column in one image. This is the render that proves Settings and Saved
        // reports exist at all, which the phone-sized shot cannot show now that Home scrolls.
        render("home-2-full")
    }
}
