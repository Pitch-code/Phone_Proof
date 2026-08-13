package com.phoneproof.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colour tokens.
 *
 * A palette *instance* rather than a hardcoded object, because there are two of them. The previous
 * version was a single dark object, which meant selecting a light theme changed the Material
 * component colours while every surface, border and outcome colour in the app stayed dark — a light
 * mode that looked broken rather than light.
 *
 * Read it through [PhoneProofTheme.colors] so the active palette follows the theme.
 */
@Immutable
data class PhoneProofPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    /** Hairline borders carry elevation in this design. Shadows are not used. */
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val pass: Color,
    val caution: Color,
    val fail: Color,
    val unknown: Color,
    val accent: Color,
    /** An untouched cell in the coverage grid: visible as an outline, clearly not yet covered. */
    val gridEmpty: Color,
    /**
     * A coverage-grid cell Android reserves for its own edge gestures, so no app can test it.
     *
     * Neutral grey and clearly lighter than [gridEmpty], which is the whole requirement: it has to
     * be distinguishable at a glance from a cell that still needs covering, without borrowing an
     * outcome colour and implying a verdict about a strip that was never measured. A first attempt
     * derived this from `textTertiary` with a low alpha and was invisible — a mid-grey at 16% lands
     * on almost exactly [gridEmpty]'s luminance.
     */
    val gridReserved: Color,
    val isDark: Boolean,
) {
    fun fill(base: Color): Color = base.copy(alpha = if (isDark) 0.12f else 0.10f)

    fun outline(base: Color): Color = base.copy(alpha = if (isDark) 0.40f else 0.45f)
}

/**
 * Dark, which is now a choice rather than the default. The background is not pure black on purpose:
 * #000 smears visibly
 * on OLED panels, which would be a poor look in an app whose job is to inspect OLED panels.
 */
val DarkPalette = PhoneProofPalette(
    background = Color(0xFF0A0A0B),
    surface = Color(0xFF111113),
    surfaceRaised = Color(0xFF18181B),
    border = Color(0x14FFFFFF),
    borderStrong = Color(0x29FFFFFF),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFA1A1AA),
    textTertiary = Color(0xFF71717A),
    pass = Color(0xFF22C55E),
    caution = Color(0xFFF59E0B),
    fail = Color(0xFFF43F5E),
    unknown = Color(0xFF52525B),
    accent = Color(0xFF3B82F6),
    gridEmpty = Color(0x0FFFFFFF),
    gridReserved = Color(0x38FFFFFF),
    isDark = true,
)

/**
 * Light.
 *
 * The outcome colours are deliberately *not* the dark ones reused. Green at #22C55E and amber at
 * #F59E0B are legible on near-black and far too pale on near-white, so each is darkened until it
 * carries on a light surface. A verdict a buyer cannot read in sunlight is not a verdict.
 */
val LightPalette = PhoneProofPalette(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF4F4F5),
    border = Color(0x1A000000),
    borderStrong = Color(0x33000000),
    textPrimary = Color(0xFF18181B),
    textSecondary = Color(0xFF52525B),
    textTertiary = Color(0xFF71717A),
    pass = Color(0xFF15803D),
    caution = Color(0xFFB45309),
    fail = Color(0xFFDC2626),
    unknown = Color(0xFF71717A),
    accent = Color(0xFF2563EB),
    gridEmpty = Color(0x0D000000),
    gridReserved = Color(0x30000000),
    isDark = false,
)

/**
 * Static rather than dynamic: the palette changes only when the user picks a different theme, so
 * there is no reason to make every reader recompose on a value that is effectively constant.
 */
// Falls back to light, matching the app's default, so a palette read without a theme around it
// cannot disagree with what the rest of the app is doing.
//
// This is a fallback and not a default anyone should rely on: changing it moved the launcher-icon
// preview, which turned out to be reading the palette with no theme wrapper at all. That render now
// pins its own theme. If another one moves when this line changes, that render is the bug.
val LocalPhoneProofPalette = staticCompositionLocalOf { LightPalette }
