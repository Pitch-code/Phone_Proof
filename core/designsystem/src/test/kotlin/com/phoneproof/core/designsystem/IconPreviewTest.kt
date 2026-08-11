package com.phoneproof.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.designsystem.theme.PhoneProofType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the launcher-icon mark at the sizes it will actually be seen at.
 *
 * An icon that only works at 192 dp is useless: on the Play Store listing and the home screen it
 * is seen small, and in the notification shade it is a monochrome silhouette. Rendering it here
 * means legibility is a checked fact rather than an assumption.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class IconPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    /**
     * The mark: a coverage grid with one cell dead.
     *
     * It is the app's entire idea in one shape, and nothing else in the category looks like it —
     * every competitor uses a phone outline, a magnifier, or a tick, all of which are invisible in
     * a crowded search result.
     */
    @Composable
    private fun IconMark(
        size: Dp,
        monochrome: Boolean = false,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 22))
                .background(if (monochrome) Color.Black else PhoneProofColors.Background),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(size * 0.56f)) {
                val cells = 3
                val gap = this.size.width * 0.12f / (cells - 1)
                val cell = (this.size.width - gap * (cells - 1)) / cells
                for (row in 0 until cells) {
                    for (column in 0 until cells) {
                        val dead = row == 1 && column == 1
                        // In monochrome the dead cell is omitted rather than tinted. Drawing it
                        // in the tint colour would flatten the mark into a plain 3x3 grid and
                        // lose the only thing that makes it mean something.
                        if (dead && monochrome) continue
                        drawRect(
                            color = when {
                                monochrome -> Color.White
                                dead -> PhoneProofColors.Fail
                                else -> PhoneProofColors.TextPrimary
                            },
                            topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun icon_at_every_size_it_will_be_seen_at() {
        composeRule.setContent {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1C1C20))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    listOf(24.dp, 36.dp, 48.dp, 64.dp, 96.dp).forEach { IconMark(it) }
                }
                Text(
                    text = "24  36  48  64  96 dp  ·  colour",
                    style = PhoneProofType.NumericSmall,
                    color = PhoneProofColors.TextSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    listOf(24.dp, 36.dp, 48.dp, 64.dp, 96.dp).forEach {
                        IconMark(it, monochrome = true)
                    }
                }
                Text(
                    // Android 13+ themed icons strip colour entirely, so the mark has to survive
                    // losing the one red cell that carries its meaning.
                    text = "monochrome / themed-icon fallback",
                    style = PhoneProofType.NumericSmall,
                    color = PhoneProofColors.TextSecondary,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/icon-sizes.png")
    }
}
