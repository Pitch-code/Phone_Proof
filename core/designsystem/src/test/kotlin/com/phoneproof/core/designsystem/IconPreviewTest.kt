package com.phoneproof.core.designsystem

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
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.designsystem.theme.PhoneProofType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the launcher icon at the sizes it will actually be seen at.
 *
 * Critically, this loads the **real vector drawables that ship in the APK** rather than redrawing
 * the mark in Compose. An earlier version of this test drew a lookalike, which meant the reviewed
 * PNG and the shipped asset could drift apart without anyone noticing — the screenshot would have
 * kept looking correct while the icon on the home screen was wrong.
 *
 * An icon that only works at 192 dp is useless: on a Play listing and a home screen it is seen
 * small, and on Android 13+ themed icons it is a single-tint silhouette with no colour at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class IconPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private val previewSizes = listOf(24.dp, 36.dp, 48.dp, 64.dp, 96.dp)

    /**
     * Approximates how the launcher composites an adaptive icon: the background colour, the
     * foreground vector on top, and a rounded mask.
     */
    @Composable
    private fun AdaptiveIconPreview(
        size: Dp,
        drawableRes: Int,
        background: Color,
        tint: Color? = null,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 22))
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(drawableRes),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
                colorFilter = tint?.let { ColorFilter.tint(it) },
            )
        }
    }

    @Test
    fun launcher_icon_at_every_size_it_will_be_seen_at() {
        composeRule.setContent {
            // Pinned to dark, like every other render in this project. This preview previously read
            // the palette with no theme around it, so it silently used the CompositionLocal fallback
            // — and the moment that fallback changed with the app's default, the icon tiles turned
            // white while the shipped icon stayed near-black. A render that depends on a fallback is
            // a render that moves for reasons unrelated to what it is testing.
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
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
                    previewSizes.forEach {
                        AdaptiveIconPreview(
                            size = it,
                            drawableRes = R.drawable.ic_launcher_foreground,
                            // The launcher's real background from res/values/colors.xml, not the
                            // theme's. The icon on a phone's home screen does not follow the app's
                            // light or dark setting, so a preview that did would be showing
                            // something the user never sees.
                            background = LAUNCHER_BACKGROUND,
                        )
                    }
                }
                Text(
                    text = "24  36  48  64  96 dp  ·  ic_launcher_foreground",
                    style = PhoneProofType.NumericSmall,
                    color = PhoneProofTheme.colors.textSecondary,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    previewSizes.forEach {
                        AdaptiveIconPreview(
                            size = it,
                            drawableRes = R.drawable.ic_launcher_monochrome,
                            background = Color.Black,
                            // Themed icons force a single tint, which is precisely why the dead
                            // cell is omitted in this drawable instead of being coloured.
                            tint = Color.White,
                        )
                    }
                }
                Text(
                    text = "ic_launcher_monochrome  ·  themed-icon, colour stripped",
                    style = PhoneProofType.NumericSmall,
                    color = PhoneProofTheme.colors.textSecondary,
                )
            }
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/icon-sizes.png")
    }

    /**
     * The 512×512 PNG the Play Console asks for, generated rather than drawn by hand.
     *
     * Play needs a square bitmap for the store listing, and it is the one icon asset that cannot be a
     * vector. Producing it here means it is built from **the same drawable that ships in the APK**, so the
     * listing and the installed app cannot end up showing different marks — which is otherwise an easy
     * mistake to make, and an embarrassing one, since the two are seen side by side the moment someone
     * installs.
     *
     * `mdpi` is deliberate: at density 1 a 512 dp box is exactly 512 px, which is what the Console wants.
     * No rounded corners and no transparency — Play applies its own mask, and supplying a pre-rounded icon
     * gets it rounded twice.
     */
    @Test
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun the_512_pixel_icon_for_the_play_listing() {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(512.dp)
                    .background(LAUNCHER_BACKGROUND),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(512.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/play-store-icon-512.png")
    }

    private companion object {
        /** Mirrors `R.color.ic_launcher_background` (#FF0A0A0B). */
        val LAUNCHER_BACKGROUND = Color(0xFF0A0A0B)
    }
}
