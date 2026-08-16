package com.phoneproof.feature.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
                    // Static, so the render is deterministic. The diagrams animate on a real
                    // device once a card is tapped.
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
     * Every diagram, at the frame it now holds.
     *
     * The reason this exists: a Canvas drawing has no compiler to tell you it renders as an
     * unreadable smudge. That mattered while these looped and nobody could watch the motion; it
     * matters more now they are still, because a single held frame is the entire illustration. If the
     * pose is wrong there is no next frame to redeem it.
     */
    @Test
    fun every_diagram_at_the_frame_it_holds() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) { DiagramFrames() }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/guide-3-diagram-frames.png")
    }

    /**
     * The same grid in light, which is the theme most people will actually see.
     *
     * This exists because its absence hid a real bug. Every render in this file asked for `DARK`,
     * while light has been the default since #12 — so two diagrams drew an occluder from a hardcoded
     * `Color(0xFF18181B)`, which is `DarkPalette.surfaceRaised` exactly, and on a light card the SIM
     * tray and the account row were near-black blocks. The torch beam had the opposite fault: white
     * at ten percent alpha, invisible on a light surface, in the one diagram about shining a torch.
     *
     * Neither was a subtle rendering flaw. Both were plainly wrong and both survived, because a
     * diagram is only reviewed in the theme somebody photographed it in.
     */
    @Test
    fun every_diagram_in_light_mode() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.LIGHT) { DiagramFrames() }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/guide-5-diagram-frames-light.png")
    }

    /**
     * The hand on its own, large, at the angles the eight diagrams actually ask for.
     *
     * Eight diagrams share one shape, and in the frame grid above it is barely thirty pixels tall —
     * so a hand that has become a blob would look much the same as a hand that has not, and the grid
     * would show eight problems where there is really one. This renders the primitive by itself,
     * big enough to see whether it is a hand at all, and rotated to each orientation in use.
     *
     * Angles here are the ones passed at the call sites: 0 grips the charging lead, 24 holds the
     * phone to the mouth, 152 runs the seam, 187 presses the sensor, 196 taps the account row, 270
     * pulls the SIM tray.
     */
    @Test
    fun the_hand_by_itself() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                val ink = PhoneProofTheme.colors.textSecondary
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PhoneProofTheme.colors.background)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(true, false).forEach { pointing ->
                        Text(
                            text = if (pointing) "POINTING" else "FIST",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhoneProofTheme.colors.textTertiary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(0f, 24f, 152f, 187f, 270f).forEach { angle ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.85f)
                                            .background(PhoneProofTheme.colors.surfaceRaised),
                                    ) {
                                        drawHand(
                                            // Centred, so a hand pointing in any direction stays in
                                            // its cell rather than leaving the frame at one angle.
                                            tip = Offset(size.width / 2f, size.height / 2f),
                                            length = size.height * 0.44f,
                                            angleDegrees = angle,
                                            ink = ink,
                                            pointing = pointing,
                                        )
                                    }
                                    Text(
                                        text = "${angle.toInt()}°",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PhoneProofTheme.colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/guide-4-hand.png")
    }
}


/**
 * All eight diagrams at the one frame each of them holds, in whichever theme wraps it.
 *
 * This used to render three arbitrary points of each diagram's cycle, which was the right thing to
 * review while they looped freely. Now they animate only while a card is open, and the frame that
 * ships when motion is off — `stillFrame` — was not among those three. A review artifact showing
 * three wrong frames instead of the one real one is worse than none.
 *
 * Two columns rather than three, because with a third of the cells there is room to make them bigger,
 * and the whole difficulty with these drawings is that they are judged at thumbnail size.
 *
 * Shared by the dark and light renders rather than written twice: the point of the light grid is that
 * it differs from the dark one *only* by the theme, so any difference between the two images is the
 * palette and nothing else.
 */
@Composable
private fun DiagramFrames() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GuideDiagram.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pair.forEach { diagram ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            // The frame is in the label, so a reviewer can see which pose was chosen
                            // without opening the enum.
                            text = "${diagram.name}  @ ${diagram.stillFrame}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhoneProofTheme.colors.textTertiary,
                        )
                        GuideDiagramCanvas(
                            diagram = diagram,
                            progress = diagram.stillFrame,
                            ink = PhoneProofTheme.colors.textSecondary,
                            accent = PhoneProofTheme.colors.accent,
                            warn = PhoneProofTheme.colors.caution,
                            surface = PhoneProofTheme.colors.surfaceRaised,
                            modifier = Modifier
                                .fillMaxWidth()
                                // 1.6, the production aspect ratio. The old grid used 1.5, so the
                                // cells were never quite the shape the buyer sees.
                                .aspectRatio(1.6f)
                                .background(PhoneProofTheme.colors.surfaceRaised),
                        )
                    }
                }
            }
        }
    }
}
