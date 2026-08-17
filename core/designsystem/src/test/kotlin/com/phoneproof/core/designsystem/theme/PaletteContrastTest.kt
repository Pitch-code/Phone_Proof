package com.phoneproof.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Test

/**
 * Contrast, computed rather than eyeballed.
 *
 * A screenshot proves a colour was drawn; it does not prove anybody can read it. The `onAccent` token
 * exists because the obvious choice — `textPrimary` on `accent` — fails WCAG AA in *both* themes, and
 * that was only noticed by measuring a rendered light-mode button. These tests make the property
 * machine-checked so it cannot be quietly undone by someone tidying two tokens into one.
 */
class PaletteContrastTest {

    /** WCAG 2.1 relative luminance. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    /** WCAG 2.1 contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val palettes = mapOf("dark" to DarkPalette, "light" to LightPalette)

    @Test
    fun the_label_on_an_accent_button_meets_AA_in_both_themes() {
        // 4.5:1 is the AA floor for text below 18.66sp bold / 24sp regular, and the retest button's
        // label is titleSmall, so the large-text allowance of 3:1 does not apply.
        palettes.forEach { (name, palette) ->
            val ratio = contrast(palette.onAccent, palette.accent)

            assertThat(ratio).isGreaterThan(4.5)
            // Guards against "fixing" a future failure by making the label the same colour as the button.
            assertThat(ratio).isLessThan(21.0)
            assertThat(name).isNotEmpty()
        }
    }

    @Test
    fun textPrimary_on_accent_would_fail_which_is_why_onAccent_exists() {
        // The bug this token was introduced to fix, kept as a test so the reasoning survives. If a
        // future palette change makes textPrimary legible on the accent, this test failing is the
        // signal to reconsider the token — not to delete the assertion.
        palettes.forEach { (_, palette) ->
            assertThat(contrast(palette.textPrimary, palette.accent)).isLessThan(4.5)
        }
    }

    @Test
    fun the_two_themes_solve_it_in_opposite_directions() {
        // Light mode's accent is deep enough to carry white; dark mode's is light enough to need a dark
        // label, which is also what Material 3 does with onPrimary in a dark theme. Stated as a test
        // because it looks like a mistake to anyone who has not measured it.
        assertThat(luminance(LightPalette.onAccent)).isGreaterThan(luminance(LightPalette.accent))
        assertThat(luminance(DarkPalette.onAccent)).isLessThan(luminance(DarkPalette.accent))
    }

    @Test
    fun body_text_is_readable_on_its_own_background_in_both_themes() {
        // Nothing to do with the button, and cheap to assert while the arithmetic is here.
        palettes.forEach { (_, palette) ->
            assertThat(contrast(palette.textPrimary, palette.background)).isGreaterThan(4.5)
            assertThat(contrast(palette.textPrimary, palette.surface)).isGreaterThan(4.5)
            assertThat(contrast(palette.textSecondary, palette.surface)).isGreaterThan(4.5)
        }
    }

    @Test
    fun the_arithmetic_agrees_with_the_two_reference_points_everyone_knows() {
        // A contrast function that is subtly wrong would make every assertion above meaningless.
        assertThat(contrast(Color.Black, Color.White)).isWithin(0.01).of(21.0)
        assertThat(contrast(Color.White, Color.White)).isWithin(0.01).of(1.0)
    }
}
