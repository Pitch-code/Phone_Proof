package com.phoneproof.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
class GuideScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    @Test
    fun the_list_of_checks() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                GuideScreen(
                    steps = GuideSteps,
                    expandedId = null,
                    animate = false,
                    onToggle = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/guide-1-list.png")
    }

    @Test
    fun a_step_opened() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                GuideScreen(
                    steps = GuideSteps,
                    expandedId = "guide.water",
                    animate = false,
                    onToggle = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/guide-2-step-open.png")
    }

    /**
     * Every diagram, at three points through its cycle.
     *
     * The reason this exists: a Canvas drawing has no compiler to tell you it renders as an
     * unreadable smudge, and there is no emulator here to watch the motion on. Because each diagram
     * is a pure function of progress, a grid of frames is the only way anyone can review whether
     * these read as the thing they are meant to depict.
     */
    @Test
    fun every_diagram_at_three_points_of_its_cycle() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PhoneProofTheme.colors.background)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    GuideDiagram.entries.forEach { diagram ->
                        Text(
                            text = diagram.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = PhoneProofTheme.colors.textTertiary,
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(0.15f, 0.4f, 0.75f).forEach { progress ->
                                Column(modifier = Modifier.weight(1f)) {
                                    GuideDiagramCanvas(
                                        diagram = diagram,
                                        progress = progress,
                                        ink = PhoneProofTheme.colors.textSecondary,
                                        accent = PhoneProofTheme.colors.accent,
                                        warn = PhoneProofTheme.colors.caution,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.5f)
                                            .background(PhoneProofTheme.colors.surfaceRaised),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/guide-3-diagram-frames.png")
    }
}
