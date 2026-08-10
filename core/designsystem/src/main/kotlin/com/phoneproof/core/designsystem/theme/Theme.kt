package com.phoneproof.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app is dark-first. A light scheme exists only so the app does not look broken if a user
 * has forced light mode; the tokens stay neutral either way.
 *
 * Material You dynamic colour is intentionally not used. The outcome colours have to mean the
 * same thing on every device — a buyer showing a red row to a seller cannot have that red
 * recoloured by someone's wallpaper.
 */
private val DarkScheme = darkColorScheme(
    primary = PhoneProofColors.Accent,
    onPrimary = PhoneProofColors.TextPrimary,
    secondary = PhoneProofColors.TextSecondary,
    onSecondary = PhoneProofColors.Background,
    error = PhoneProofColors.Fail,
    onError = PhoneProofColors.TextPrimary,
    background = PhoneProofColors.Background,
    onBackground = PhoneProofColors.TextPrimary,
    surface = PhoneProofColors.Surface,
    onSurface = PhoneProofColors.TextPrimary,
    surfaceVariant = PhoneProofColors.SurfaceRaised,
    onSurfaceVariant = PhoneProofColors.TextSecondary,
    outline = PhoneProofColors.BorderStrong,
    outlineVariant = PhoneProofColors.Border,
)

private val LightScheme = lightColorScheme(
    primary = PhoneProofColors.Accent,
    error = PhoneProofColors.Fail,
)

@Composable
fun PhoneProofTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = PhoneProofType.Material,
        content = content,
    )
}
