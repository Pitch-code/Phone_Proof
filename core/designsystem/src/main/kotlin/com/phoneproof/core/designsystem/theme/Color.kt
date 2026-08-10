package com.phoneproof.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The only place colour is defined. Features must never hardcode a hex value.
 *
 * The palette is deliberately neutral and dark-first. This app has to look like an instrument
 * a buyer can hold up to a seller, not like an advertisement — a reviewer of a competing
 * diagnostic tool said its cheap-looking interface was the reason he could not trust it.
 */
@Immutable
object PhoneProofColors {

    // Surfaces. Background is not pure black on purpose: #000 smears visibly on OLED panels,
    // which would be a poor look in an app whose job is to inspect OLED panels.
    val Background: Color = Color(0xFF0A0A0B)
    val Surface: Color = Color(0xFF111113)
    val SurfaceRaised: Color = Color(0xFF18181B)

    /** Hairline borders carry elevation in this design. Shadows are not used. */
    val Border: Color = Color(0x14FFFFFF)
    val BorderStrong: Color = Color(0x29FFFFFF)

    val TextPrimary: Color = Color(0xFFFAFAFA)
    val TextSecondary: Color = Color(0xFFA1A1AA)
    val TextTertiary: Color = Color(0xFF71717A)

    // Outcome colours. Each is paired with an icon and a word at every usage site, because the
    // report card gets photographed and shared, sometimes in greyscale, and because colour
    // alone excludes colour-blind users.
    val Pass: Color = Color(0xFF22C55E)
    val Caution: Color = Color(0xFFF59E0B)
    val Fail: Color = Color(0xFFF43F5E)
    val Unknown: Color = Color(0xFF52525B)

    /** Exactly one accent colour exists in this app. */
    val Accent: Color = Color(0xFF3B82F6)

    /** Untouched cell in the coverage grid: visible as an outline, clearly not yet covered. */
    val GridEmpty: Color = Color(0x0FFFFFFF)

    fun fill(base: Color): Color = base.copy(alpha = 0.12f)

    fun outline(base: Color): Color = base.copy(alpha = 0.40f)
}
