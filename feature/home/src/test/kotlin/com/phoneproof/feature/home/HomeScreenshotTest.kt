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

    @Test
    fun home() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                HomeScreen(
                    checks = listOf(
                        HomeCheck(
                            title = "Remote lock control",
                            subtitle = "Can a lender brick this phone after you pay?",
                            onClick = {},
                        ),
                        HomeCheck(
                            title = "Touch response",
                            subtitle = "Find dead patches on the screen",
                            onClick = {},
                        ),
                    ),
                    onStartFullTest = {},
                    onOpenSettings = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/home.png")
    }
}
