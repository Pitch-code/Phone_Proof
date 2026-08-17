package com.phoneproof.feature.touchgrid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.touch.FingersDown
import com.phoneproof.checks.touch.MultiTouchCheck
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The multi-touch screen, in states that need a hand to reach.
 *
 * Four fingers on glass is not something Robolectric can produce, so the pointer positions are supplied
 * directly. That is the point of keeping the screen a pure function of its state: the case worth reviewing
 * is the one where the count sticks below the target, and no amount of tapping in a test could create it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class MultiTouchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    /** Five fingers spread across the pad, roughly where a hand would put them. */
    private fun spread(count: Int): List<Offset> = listOf(
        Offset(150f, 900f),
        Offset(330f, 780f),
        Offset(520f, 740f),
        Offset(700f, 800f),
        Offset(860f, 960f),
    ).take(count)

    @Test
    fun before_anything_is_touched() {
        render("multitouch-1-ready", MultiTouchUiState(claimedPoints = 5))
    }

    @Test
    fun four_fingers_down_and_all_four_registering() {
        render(
            "multitouch-2-counting",
            MultiTouchUiState(
                stage = MultiTouchStage.COUNTING,
                claimedPoints = 5,
                current = 4,
                best = 4,
                positions = spread(4),
            ),
        )
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun the_pad_in_landscape_with_the_prompt_up() {
        // Two things that both want the bottom of the screen: the counter row and the lift-your-fingers
        // prompt, whose clearance is a fixed 96dp measured in portrait.
        render(
            "multitouch-10-landscape",
            MultiTouchUiState(
                stage = MultiTouchStage.COUNTING,
                claimedPoints = 5,
                current = 5,
                best = 5,
                positions = spread(5),
            ),
        )
    }

    @Test
    fun the_target_reached_says_so_before_the_buyer_lets_go() {
        // Green while the fingers are still on the glass. A buyer who has to lift off and read a verdict to
        // find out whether it worked has to do the whole thing again if it did not.
        //
        // This state also carries the "you can lift your fingers now" prompt, since it is the exact
        // condition that shows it: at target, with fingers still down.
        render(
            "multitouch-3-target-reached",
            MultiTouchUiState(
                stage = MultiTouchStage.COUNTING,
                claimedPoints = 5,
                current = 5,
                best = 5,
                positions = spread(5),
            ),
        )
    }

    @Test
    fun the_count_stuck_below_the_claim() {
        // The fault this test exists for: three registering while more are pressed against the screen.
        render(
            "multitouch-4-stuck-at-three",
            MultiTouchUiState(
                stage = MultiTouchStage.COUNTING,
                claimedPoints = 5,
                current = 3,
                best = 3,
                positions = spread(3),
            ),
        )
    }

    @Test
    fun the_question_when_it_fell_short() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                MultiTouchScreen(
                    state = MultiTouchUiState(
                        stage = MultiTouchStage.ASKING,
                        claimedPoints = 5,
                        best = 3,
                    ),
                    onPointers = {},
                    onFinish = {},
                    onAnswerFingersDown = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNode(isDialog())
            .captureRoboImage("$outputDir/multitouch-5-question.png")
    }

    @Test
    fun a_screen_that_followed_everything_it_promised() {
        render(
            "multitouch-6-pass",
            MultiTouchUiState(
                stage = MultiTouchStage.DONE,
                claimedPoints = 5,
                best = 5,
                result = MultiTouchCheck.evaluate(maxObserved = 5, claimedPoints = 5),
            ),
        )
    }

    @Test
    fun all_five_down_and_only_three_followed() {
        render(
            "multitouch-7-caution",
            MultiTouchUiState(
                stage = MultiTouchStage.DONE,
                claimedPoints = 5,
                best = 3,
                result = MultiTouchCheck.evaluate(
                    maxObserved = 3,
                    claimedPoints = 5,
                    fingersDown = FingersDown.ALL_OF_THEM,
                ),
            ),
        )
    }

    @Test
    fun a_phone_that_only_claims_two_points_is_asked_for_two() {
        // The target follows the phone's own claim, so a budget handset is not asked for five and then
        // failed for managing the two it advertises.
        render(
            "multitouch-8-claims-two",
            MultiTouchUiState(
                stage = MultiTouchStage.COUNTING,
                claimedPoints = 2,
                current = 2,
                best = 2,
                positions = spread(2),
            ),
        )
    }

    @Test
    fun in_light_mode() {
        render(
            "multitouch-9-counting-light",
            MultiTouchUiState(
                stage = MultiTouchStage.COUNTING,
                claimedPoints = 5,
                current = 4,
                best = 4,
                positions = spread(4),
            ),
            themeMode = ThemeMode.LIGHT,
        )
    }

    private fun render(
        name: String,
        state: MultiTouchUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                MultiTouchScreen(
                    state = state,
                    onPointers = {},
                    onFinish = {},
                    onAnswerFingersDown = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }
}
