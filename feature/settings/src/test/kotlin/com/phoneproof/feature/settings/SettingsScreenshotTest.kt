package com.phoneproof.feature.settings

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
class SettingsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                SettingsScreen(
                    state = SettingsUiState(
                        themeMode = themeMode,
                        versionName = "0.1.0",
                        versionCode = 1L,
                        billingAvailable = false,
                    ),
                    onThemeSelected = {},
                    onOpenPrivacyPolicy = {},
                    onShareApp = {},
                    onOpenDiagnostics = {},
                    onChoosePlan = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun settings_dark() {
        render("settings-1-dark", ThemeMode.DARK)
    }

    @Test
    fun settings_light() {
        // The reason this test exists: light mode was previously broken by construction. Material
        // component colours switched while every custom surface, border and outcome colour stayed
        // dark, so "light" rendered as a dark app with pale buttons. A render is the only way to
        // catch that, because it compiles perfectly either way.
        render("settings-2-light", ThemeMode.LIGHT)
    }

    // The two shots above are cut off by the viewport at the Shop card, so everything below it —
    // version, build, privacy policy, share, and the link to Diagnostics — was shipping unrendered
    // by any test. A tall viewport is used rather than scrolling because it captures the whole
    // column in one image, which is what makes a missing or misworded row visible.
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xhdpi")
    fun settings_full_column() {
        render("settings-3-full", ThemeMode.DARK)
    }
}
