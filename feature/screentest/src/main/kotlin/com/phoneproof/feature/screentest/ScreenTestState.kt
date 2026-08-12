package com.phoneproof.feature.screentest

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.phoneproof.core.model.CheckResult

/**
 * One full-screen test pattern.
 *
 * The set is chosen so that each fault has at least one pattern on which it is unmissable:
 *
 *  - **White** shows dead (permanently black) pixels, and is the best pattern for OLED burn-in,
 *    because a ghost of a status bar or keyboard appears as a faint grey band.
 *  - **Black** shows stuck-on and hot pixels as bright specks, which are invisible on white.
 *  - **Red, green and blue** each isolate one subpixel. A pixel with only its green element dead
 *    looks slightly off-colour on white and obviously black on a green field.
 *  - **Mid grey** is the most revealing pattern for uneven panel wear and blotchiness, which pure
 *    white can hide by saturating the eye.
 */
@Immutable
data class TestPattern(
    val name: String,
    val colour: Color,
    /** What to look for. Shown before the pattern, never on top of it. */
    val lookFor: String,
    /** True when the pattern is light, so hint text drawn over it must be dark. */
    val isLight: Boolean,
)

val DefaultPatterns: List<TestPattern> = listOf(
    TestPattern(
        name = "White",
        colour = Color.White,
        lookFor = "Dark dots, and faint grey shapes where a keyboard or status bar used to sit.",
        isLight = true,
    ),
    TestPattern(
        name = "Black",
        colour = Color.Black,
        lookFor = "Bright specks. These are invisible on a white screen.",
        isLight = false,
    ),
    TestPattern(
        name = "Red",
        colour = Color(0xFFFF0000),
        lookFor = "Dots of any other colour, or patches that look darker than the rest.",
        isLight = false,
    ),
    TestPattern(
        name = "Green",
        colour = Color(0xFF00FF00),
        lookFor = "Dots of any other colour. Green is the subpixel your eye notices most.",
        isLight = true,
    ),
    TestPattern(
        name = "Blue",
        colour = Color(0xFF0000FF),
        lookFor = "Dots of any other colour, and uneven brightness across the panel.",
        isLight = false,
    ),
    TestPattern(
        name = "Grey",
        colour = Color(0xFF808080),
        lookFor = "Blotches and uneven shading. This is the best pattern for a worn panel.",
        isLight = false,
    ),
)

enum class ScreenTestPhase {
    /** Explains what is about to happen, and that brightness will go to full. */
    INTRO,

    /** A pattern fills the screen. Tapping advances. */
    PATTERN,

    /** Asks what they saw. */
    QUESTION,

    FINISHED,
}

@Immutable
data class ScreenTestUiState(
    val phase: ScreenTestPhase = ScreenTestPhase.INTRO,
    val patterns: List<TestPattern> = DefaultPatterns,
    /** Index into [patterns] while in [ScreenTestPhase.PATTERN]. */
    val index: Int = 0,
    /**
     * How many patterns have been shown in full.
     *
     * Tracked separately from [index] because it is evidence: a buyer who backs out after two
     * patterns has not tested the screen, and the check refuses to call that a pass.
     */
    val viewed: Int = 0,
    val result: CheckResult? = null,
) {
    val current: TestPattern? get() = patterns.getOrNull(index)
    val total: Int get() = patterns.size

    /** 1-based, for "3 of 6". */
    val position: Int get() = (index + 1).coerceAtMost(total)
}
