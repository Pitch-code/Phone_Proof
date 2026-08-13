package com.phoneproof.core.designsystem

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.component.LockedFeature
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The paywall, rendered.
 *
 * This screen is where an app is most easily resented, and it shipped with no render at all — three
 * separate locked cases all drawing through a component nobody had looked at. A paywall whose tone
 * has never been read is a paywall nobody has reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class LockedFeatureScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(
        name: String,
        title: String,
        explanation: String,
        whatUnlockingGives: String,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                LockedFeature(
                    title = title,
                    explanation = explanation,
                    whatUnlockingGives = whatUnlockingGives,
                    onOpenSettings = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun the_scan_allowance_running_out() {
        // The wording that matters most: it has to be clear nothing is wrong with the phone or the
        // app, only that the trial ended.
        render(
            name = "locked-1-scans-used",
            title = "You have used both free scans",
            explanation = "The free trial covers 2 full scans of a phone, and both are done. " +
                "Nothing is wrong with this phone or with the app — the trial has simply ended.",
            whatUnlockingGives = "Scan as many phones as you like, keep every report instead of " +
                "the last two, save a report as a PDF, and compare two phones side by side.",
        )
    }

    @Test
    fun a_paid_screen_in_light_mode() {
        // Light is the app's default, so the paywall is more likely to be met in light than in dark.
        render(
            name = "locked-2-advisory-light",
            title = "Claimed against measured",
            explanation = "This compares what the seller told you against what the phone actually " +
                "reports — storage, memory and model. It is part of a paid plan.",
            whatUnlockingGives = "Catch a phone sold as 128 GB that holds 32, or as 8 GB of memory " +
                "when it has 4. Also unlocks the by-hand guide, PDF reports and side-by-side " +
                "comparison.",
            themeMode = ThemeMode.LIGHT,
        )
    }
}
