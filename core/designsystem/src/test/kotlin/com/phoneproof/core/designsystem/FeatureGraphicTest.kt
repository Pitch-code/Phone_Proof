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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The 1024×500 banner the Play listing requires.
 *
 * Generated rather than made in a design tool, for the same reason the 512 icon is: it uses **the launcher
 * drawable that ships in the APK**, so the banner, the store icon and the installed app cannot drift into
 * showing three different marks.
 *
 * `w1024dp-h500dp-mdpi` is exactly 1024×500 px at density 1, which is the size Play asks for.
 *
 * ## Why it is this plain
 *
 * Play crops and overlays this image differently across the surfaces it appears on, and it can appear with
 * the app title printed over it. So the mark sits left of centre with clear space around it, nothing
 * important goes near an edge, and there is no small print — anything subtle would be lost or covered. The
 * one line of text is the sentence that explains the app to someone who has never heard of it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h500dp-mdpi")
class FeatureGraphicTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    @Test
    fun the_feature_graphic_for_the_play_listing() {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(1024.dp, 500.dp)
                    .background(LAUNCHER_BACKGROUND),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    modifier = Modifier.padding(horizontal = 72.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(percent = 22))
                            .background(Color(0xFF15151A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(200.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "PhoneProof",
                            fontSize = 76.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFAFAFA),
                        )
                        Text(
                            // The whole pitch in one line. Not a feature list: this is read in passing, at
                            // a glance, often with the app's title printed over part of it.
                            text = "Test any phone before you buy it",
                            fontSize = 34.sp,
                            color = Color(0xFF22C55E),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/store-feature-graphic.png")
    }

    private companion object {
        /** Mirrors `R.color.ic_launcher_background` (#FF0A0A0B). */
        val LAUNCHER_BACKGROUND = Color(0xFF0A0A0B)
    }
}
