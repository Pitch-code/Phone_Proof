package com.phoneproof.feature.settings

import androidx.compose.runtime.Composable
import com.phoneproof.core.preferences.Entitlement

/**
 * The release copy of the tier switcher, which deliberately does nothing.
 *
 * A debug build compiles the version of this file in `src/debug`, which draws a switcher for every tier
 * so the paid screens can be exercised without Play Billing — impossible in a sideloaded build.
 *
 * **A release build compiles this one.** There is no flag to get wrong and no condition to invert: the
 * shipped APK simply contains no code that can grant a paid tier, because the only such code lives in a
 * source set the release variant never sees.
 *
 * Do not "simplify" the two files into one guarded by `BuildConfig.DEBUG`. That is what this replaced, and
 * the problem with it was never that the flag was unreliable — it was that the switcher, its strings and
 * the write to storage all shipped, one careless edit away from giving every paid feature away.
 *
 * [current] and [onSelect] are unused here on purpose; the signature has to match the debug copy.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun TierOverride(
    current: Entitlement,
    onSelect: (Entitlement) -> Unit,
) {
    // Nothing. See above.
}
