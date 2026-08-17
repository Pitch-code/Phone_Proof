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

    private fun render(name: String) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                HomeScreen(
                    onStartFullTest = {},
                    onOpenChecks = {},
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
                    onStartFullTest = {},
                    onOpenChecks = {},
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
        // The whole column in one image. Home now fits a phone screen without scrolling, which is the
        // point of moving the nine checks off it — but this render is what proves it stays that way.
        render("home-2-full")
    }

    @Test
    fun the_checks_on_their_own() {
        // Every check the app offers, read from the list the navigation graph uses. That list used to be
        // hand-copied into this file and fell five entries behind the real screen, so the microphone, the
        // cameras and the IMEI were all missing while their PRs were reviewed against these renders.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ChecksScreen(
                    checks = HomeCatalogue.map { HomeCheck(it.title, it.subtitle) {} },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/checks-1-list.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xhdpi")
    fun the_checks_on_their_own_full_column() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ChecksScreen(
                    checks = HomeCatalogue.map { HomeCheck(it.title, it.subtitle) {} },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/checks-2-full.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun home_in_landscape() {
        // Home scrolls, so this should hold. Rendered because it is the screen every buyer sees first and
        // the one where a broken layout would be noticed by everybody.
        render("home-6-landscape")
    }

    @Test
    fun home_in_light_mode() {
        // The header gear is new and light mode has produced two real bugs in this project already.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(
                    onStartFullTest = {},
                    onOpenChecks = {},
                    onOpenGuide = {},
                    onOpenReports = {},
                    onOpenSettings = {},
                    freeScansLeft = 2,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/home-5-light.png")
    }
}
