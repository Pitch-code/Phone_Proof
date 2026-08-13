package com.phoneproof.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** Which theme the user has chosen. Persisted; [LIGHT] is the default. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Material You dynamic colour is intentionally not used. The outcome colours have to mean the same
 * thing on every device — a buyer showing a red row to a seller cannot have that red recoloured by
 * someone's wallpaper.
 */
@Composable
fun PhoneProofTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette

    CompositionLocalProvider(LocalPhoneProofPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(palette) else lightScheme(palette),
            typography = PhoneProofType.Material,
            content = content,
        )
    }
}

/**
 * Accessor for the active palette, mirroring how `MaterialTheme.colorScheme` works. Kotlin allows an
 * object and a function to share a name, which is what lets `PhoneProofTheme { }` and
 * `PhoneProofTheme.colors` coexist.
 */
object PhoneProofTheme {
    val colors: PhoneProofPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPhoneProofPalette.current
}

private fun darkScheme(p: PhoneProofPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = p.textPrimary,
    secondary = p.textSecondary,
    onSecondary = p.background,
    error = p.fail,
    onError = p.textPrimary,
    background = p.background,
    onBackground = p.textPrimary,
    surface = p.surface,
    onSurface = p.textPrimary,
    surfaceVariant = p.surfaceRaised,
    onSurfaceVariant = p.textSecondary,
    outline = p.borderStrong,
    outlineVariant = p.border,
)

private fun lightScheme(p: PhoneProofPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = Color.White,
    secondary = p.textSecondary,
    onSecondary = Color.White,
    error = p.fail,
    onError = Color.White,
    background = p.background,
    onBackground = p.textPrimary,
    surface = p.surface,
    onSurface = p.textPrimary,
    surfaceVariant = p.surfaceRaised,
    onSurfaceVariant = p.textSecondary,
    outline = p.borderStrong,
    outlineVariant = p.border,
)
