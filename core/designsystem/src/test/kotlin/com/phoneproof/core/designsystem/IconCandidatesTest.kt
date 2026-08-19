package com.phoneproof.core.designsystem

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Candidate launcher marks, side by side, at the sizes they will actually be judged at.
 *
 * The product owner's objection to the current icon is that it needs explaining, and that is a fair
 * objection: a 3×3 grid with one dead cell states the app's idea precisely and states it to nobody. The
 * argument for it was that nothing else in the category looks like it — true, and the wrong trade for an
 * app no one has heard of, which has about one second in a search result to say what it is.
 *
 * So this renders each candidate the way it will be met, not the way a designer would like it met:
 *
 *  - **24 dp**, which is the size in a search result and a notification.
 *  - **Monochrome**, because Android 13+ themed icons throw every colour away. A mark that depends on a
 *    green tick against a white phone becomes one flat silhouette, and several otherwise good ideas die
 *    here — which is why each candidate uses holes rather than colour to carry its detail.
 *  - Against the **real launcher background**, since a home-screen icon does not follow the app's own
 *    light or dark setting.
 *
 * Loading the shipped drawables rather than redrawing them in Compose, for the same reason
 * [IconPreviewTest] does: a lookalike would let the reviewed image and the real asset drift apart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w520dp-h1600dp-xhdpi")
class IconCandidatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private val sizes = listOf(24.dp, 36.dp, 48.dp, 64.dp, 96.dp)

    private data class Candidate(val label: String, val drawable: Int, val pitch: String)

    private val candidates = listOf(
        Candidate("A", R.drawable.ic_candidate_a_foreground, "phone + verified badge"),
        Candidate("B", R.drawable.ic_candidate_b_foreground, "magnifier over phone"),
        Candidate("C", R.drawable.ic_candidate_c_foreground, "phone + pulse"),
        Candidate("D", R.drawable.ic_candidate_d_foreground, "the tick alone"),
        Candidate("now", R.drawable.ic_launcher_foreground, "current: grid with a dead cell"),
    )

    @Composable
    private fun Tile(size: Dp, drawable: Int, background: Color, tint: Color? = null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 22))
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
                colorFilter = tint?.let { ColorFilter.tint(it) },
            )
        }
    }

    /**
     * Label above, tiles below.
     *
     * The first version of this put the description in a third column beside the tiles, and Compose did what
     * it was asked: with no width left it wrapped the text to one character per line, turning each label into
     * a vertical ribbon and pushing the monochrome half of the sheet off the bottom. A review sheet that is
     * itself hard to read is worse than no sheet, because it wastes the reviewer's attention on the wrong
     * problem.
     */
    @Composable
    private fun CandidateRow(candidate: Candidate, tint: Color?) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${candidate.label}  —  ${candidate.pitch}",
                style = PhoneProofType.NumericSmall,
                color = Color(0xFFFAFAFA),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                sizes.forEach { size ->
                    Tile(
                        size = size,
                        drawable = candidate.drawable,
                        background = if (tint == null) LAUNCHER_BACKGROUND else Color.Black,
                        tint = tint,
                    )
                }
            }
        }
    }

    @Test
    fun every_candidate_in_colour_and_stripped_of_it() {
        composeRule.setContent {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1C1C20))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "IN COLOUR  ·  24  36  48  64  96 dp",
                    style = PhoneProofType.NumericSmall,
                    color = Color(0xFF9A9AA2),
                )
                candidates.forEach { CandidateRow(it, tint = null) }

                Text(
                    text = "THEMED ICON  ·  every colour thrown away",
                    style = PhoneProofType.NumericSmall,
                    color = Color(0xFF9A9AA2),
                    modifier = Modifier.padding(top = 10.dp),
                )
                candidates.forEach { CandidateRow(it, tint = Color.White) }
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/icon-candidates.png")
    }

    private companion object {
        /** Mirrors `R.color.ic_launcher_background` (#FF0A0A0B). */
        val LAUNCHER_BACKGROUND = Color(0xFF0A0A0B)
    }
}
