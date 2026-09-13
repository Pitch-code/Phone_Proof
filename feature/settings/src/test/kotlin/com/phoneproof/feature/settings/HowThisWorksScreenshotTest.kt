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

/**
 * The whole explainer, in one image, both themes.
 *
 * Rendered because this screen is nothing but words, and the words are the product: if the code example
 * reads wrong, or a rupee figure sneaks back in, or the "nothing is left behind" promise gets softened,
 * the only way to catch it is to look. A tall viewport captures the full scroll in one shot so nothing
 * below the fold ships unreviewed.
 *
 * The content composable [HowThisWorksScreen] is rendered directly rather than through the [HowThisWorks]
 * dialog: a Dialog draws in its own window, which `onRoot()` does not capture, and the content is what is
 * worth looking at anyway.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2600dp-xhdpi")
class HowThisWorksScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                HowThisWorksScreen(onClose = {}, modifier = Modifier.fillMaxSize())
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun how_this_works_dark() {
        render("how-this-works-1-dark", ThemeMode.DARK)
    }

    @Test
    fun how_this_works_light() {
        render("how-this-works-2-light", ThemeMode.LIGHT)
    }
}
