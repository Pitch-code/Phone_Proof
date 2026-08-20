package com.phoneproof.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phoneproof.core.designsystem.component.LockedFeature
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.PaidChecks
import com.phoneproof.core.preferences.SettingsRepository

/**
 * Wraps a check that the free trial does not include.
 *
 * Applied in the navigation graph rather than inside each feature module, for two reasons. The feature
 * modules do not know their own route — they are handed callbacks and nothing else, which is what lets the
 * same screen be opened from Home, from the run and from the verdict. And a paywall repeated in three
 * feature modules is three places for the wording and the rule to drift apart, which is the whole reason
 * `LockedFeature` exists.
 *
 * Which checks are locked, what each paywall says, and the reasoning for both, all live in [PaidChecks] —
 * deliberately not here, so the decision, the wording and the entitlement sit together instead of being
 * spread across the navigation graph.
 */
@Composable
fun PaidCheckGate(
    route: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val entitlement by remember(context) { SettingsRepository(context).effectiveEntitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)

    // Not `isUnlocked`, because the copy lookup is what decides here: a route with no wording written for
    // it is not a locked route, and falling through to the check is the right way to fail. The alternative
    // — a paywall with an empty body over a screen that works — asks for money and says nothing.
    val locked = PaidChecks.copyFor(route)?.takeIf { !entitlement.hasPremiumExtras }

    if (locked == null) {
        content()
        return
    }

    LockedFeature(
        title = locked.title,
        explanation = locked.explanation,
        whatUnlockingGives = locked.whatUnlockingGives,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}
