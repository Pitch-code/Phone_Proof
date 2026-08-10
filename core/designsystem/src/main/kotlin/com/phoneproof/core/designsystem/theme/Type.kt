package com.phoneproof.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography.
 *
 * The load-bearing decision here is [Numeric]: every measurement in the app renders in a
 * monospaced face with tabular figures, so digits never shift horizontally as live values
 * update. It does more for perceived credibility than any animation, and it is the difference
 * between reading like a measuring device and reading like a web page.
 *
 * Bundling Inter and JetBrains Mono is deferred polish; the platform families give correct
 * metrics today without adding font binaries to the repo.
 */
object PhoneProofType {

    /**
     * OpenType tabular figures. Every digit occupies the same advance width, so a live readout
     * does not jitter horizontally while it counts. Passed as a raw feature tag string, which is
     * what [TextStyle.fontFeatureSettings] expects.
     */
    private const val TABULAR_FIGURES = "tnum"

    private val evenLineHeight = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    /** Use for every number, unit and measurement in the app. Never for prose. */
    val Numeric: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = TABULAR_FIGURES,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = evenLineHeight,
    )

    /** Large readout, e.g. the live cell counter during the coverage test. */
    val NumericLarge: TextStyle = Numeric.copy(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
    )

    val NumericSmall: TextStyle = Numeric.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val Material: Typography = Typography(
        displaySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.5).sp,
            lineHeightStyle = evenLineHeight,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.3).sp,
            lineHeightStyle = evenLineHeight,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
            lineHeightStyle = evenLineHeight,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            lineHeightStyle = evenLineHeight,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            lineHeightStyle = evenLineHeight,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            lineHeightStyle = evenLineHeight,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            lineHeightStyle = evenLineHeight,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            lineHeightStyle = evenLineHeight,
        ),
    )
}
