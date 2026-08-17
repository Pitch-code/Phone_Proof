package com.phoneproof.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.preferences.Entitlement

/**
 * The debug-only tier switcher — the **debug** copy of this file.
 *
 * There is a second file with this same name and signature in `src/release`, and it draws nothing. The
 * release build compiles that one, so a shipped APK contains no code capable of granting a paid tier.
 *
 * ## Why a source set rather than a flag
 *
 * This used to be a `Section` inside `SettingsScreen` wrapped in `if (state.showTestingControls)`, fed
 * from `BuildConfig.DEBUG`. That was correct and it was still the wrong shape:
 *
 *  - the switcher, its strings and the call that writes `PREMIUM` to storage all shipped inside the
 *    release APK, inert but present;
 *  - and it was one edit from being live. Anyone flipping that flag to test something, or inverting the
 *    condition by accident, would publish an app that gives every paid feature away.
 *
 * Splitting it by source set removes the possibility rather than guarding against it. `HardcodedStringsTest`
 * has a companion assertion that no file outside a debug source set may write a paid tier, so the shape
 * cannot quietly be undone either.
 *
 * It exists at all because Play Billing cannot complete a purchase in a sideloaded build, so there is no
 * other way to exercise the paid screens during development.
 */
@Composable
internal fun TierOverride(
    current: Entitlement,
    onSelect: (Entitlement) -> Unit,
) {
    Section("Testing only") {
        Text(
            text = "This build cannot take payments, so the paid tiers are unlocked here to be " +
                "tested. Debug builds only — this section does not exist in a release build.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.caution,
        )
        Entitlement.entries.forEach { tier ->
            ThemeLikeRow(
                title = tier.label,
                selected = current == tier,
                onClick = { onSelect(tier) },
            )
        }
    }
}
