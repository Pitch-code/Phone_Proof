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
     * Every check the app actually offers, not a token two.
     *
     * The old test passed two, which is why nobody noticed Home had outgrown its fixed layout: with
     * two rows everything fitted, while the real app pushed Settings off the bottom of the screen.
     * A screenshot test that renders less than the real screen cannot catch a screen that overflows.
     */
    private fun realChecks(): List<HomeCheck> = listOf(
        HomeCheck("Instant scan", "Software, storage, sensors and screen — no waiting") {},
        HomeCheck("Remote lock control", "Can a lender brick this phone after you pay?") {},
        HomeCheck("Touch response", "Find dead patches on the screen") {},
        HomeCheck("Dead pixels and burn-in", "Plain colours that make screen faults obvious") {},
        HomeCheck("Claimed against measured", "Is it the phone you were promised?") {},
    )

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
    @Config(qualifiers = "w411dp-h1800dp-xhdpi")
    fun home_full_column() {
        // The whole scrolling column in one image. This is the render that proves Settings and Saved
        // reports exist at all, which the phone-sized shot cannot show now that Home scrolls.
        render("home-2-full")
    }
}
