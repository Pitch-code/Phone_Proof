package com.phoneproof.feature.touchgrid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.touch.GhostTouchCheck
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every state the ghost-touch watch can be in, rendered because the wording carries the whole check.
 *
 * This is the one screen that accuses a phone of a fault it found while nobody was touching it, and the one
 * whose verdict flips between FAIL and CAUTION on a single answer. If the "hands off?" caveat softened, or a
 * quiet watch started reading as a confident pass, the only way to see it is to look — so each state is
 * looked at, and the results are built from the real [GhostTouchCheck] rather than hand-written cards.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class GhostTouchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(name: String, state: GhostTouchUiState, themeMode: ThemeMode = ThemeMode.DARK) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                GhostTouchScreen(
                    state = state,
                    onStart = {},
                    onContact = { _, _ -> },
                    onStop = {},
                    onAnswerHandsOff = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    private fun watch(watchedMillis: Long, events: Int, confirmedHandsOff: Boolean = true) =
        GhostTouchCheck.Watch(
            watchedMillis = watchedMillis,
            // One contact per event, spaced well past the same-event window so the check counts them apart.
            contacts = (0 until events).map {
                GhostTouchCheck.Contact(
                    atMillis = it * 1_000L,
                    xFraction = 0.2f,
                    yFraction = 0.2f,
                )
            },
            confirmedHandsOff = confirmedHandsOff,
        )

    @Test
    fun before_it_starts() {
        // The instructions are the test here: lay it flat, hands off, unplug a charger.
        render("ghost-1-ready", GhostTouchUiState())
    }

    @Test
    fun watching_with_a_clean_screen() {
        render(
            "ghost-2-watching",
            GhostTouchUiState(
                stage = GhostTouchStage.WATCHING,
                watchedMillis = 12_000,
                contactCount = 0,
            ),
        )
    }

    @Test
    fun watching_as_a_phantom_touch_lands() {
        // The moment the buyer sees the fault happen, before any verdict — the surface turns to caution.
        render(
            "ghost-3-caught",
            GhostTouchUiState(
                stage = GhostTouchStage.WATCHING,
                watchedMillis = 18_000,
                contactCount = 2,
            ),
        )
    }

    @Test
    fun a_screen_that_taps_itself() {
        // Hands confirmed off, so this is a FAIL at high confidence — the dealbreaker verdict.
        render(
            "ghost-4-fail",
            GhostTouchUiState(
                stage = GhostTouchStage.DONE,
                watchedMillis = GhostTouchCheck.FULL_WATCH_MILLIS,
                result = GhostTouchCheck.evaluate(watch(GhostTouchCheck.FULL_WATCH_MILLIS, events = 3)),
            ),
        )
    }

    @Test
    fun found_something_but_a_thumb_might_explain_it() {
        // Hands not confirmed off: the check downgrades this to a CAUTION rather than condemning the phone.
        render(
            "ghost-5-caution",
            GhostTouchUiState(
                stage = GhostTouchStage.DONE,
                watchedMillis = GhostTouchCheck.FULL_WATCH_MILLIS,
                result = GhostTouchCheck.evaluate(
                    watch(GhostTouchCheck.FULL_WATCH_MILLIS, events = 2, confirmedHandsOff = false),
                ),
            ),
        )
    }

    @Test
    fun a_quiet_watch_is_a_cautious_pass() {
        // The point of this render: a quiet spell is LOW confidence and says so, never a confident all-clear.
        render(
            "ghost-6-pass",
            GhostTouchUiState(
                stage = GhostTouchStage.DONE,
                watchedMillis = GhostTouchCheck.FULL_WATCH_MILLIS,
                result = GhostTouchCheck.evaluate(watch(GhostTouchCheck.FULL_WATCH_MILLIS, events = 0)),
            ),
        )
    }

    @Test
    fun stopped_too_soon_to_say() {
        // Below the useful minimum the app refuses to draw a conclusion, even with nothing found.
        render(
            "ghost-7-too-short",
            GhostTouchUiState(
                stage = GhostTouchStage.DONE,
                watchedMillis = 4_000,
                result = GhostTouchCheck.evaluate(watch(4_000, events = 0)),
            ),
        )
    }

    @Test
    fun before_it_starts_in_light() {
        render("ghost-8-ready-light", GhostTouchUiState(), ThemeMode.LIGHT)
    }
}
