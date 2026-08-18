package com.phoneproof.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
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
 * Which checks are locked, and the reasoning for each, is in [PaidChecks] — deliberately not here, so the
 * decision sits next to the entitlement it belongs to rather than inside the navigation graph.
 */
@Composable
fun PaidCheckGate(
    route: String,
    title: String,
    /** What this check finds, in the buyer's terms. Concrete: they are deciding whether to pay for it. */
    whatItFinds: String,
    /**
     * How to get the same answer without paying.
     *
     * Every locked check has one — that is the rule they were chosen by. Saying it out loud costs a sale
     * occasionally and is the difference between a limit and a hostage: none of these is withheld because
     * the buyer would be stuck without it.
     */
    doItYourself: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val entitlement by remember(context) { SettingsRepository(context).entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)

    if (PaidChecks.isUnlocked(route, entitlement)) {
        content()
        return
    }

    LockedFeature(
        title = title,
        explanation = "$whatItFinds\n\nThe free trial leaves this one out. $doItYourself",
        whatUnlockingGives = "Premium unlocks this and the other two measured checks the trial leaves " +
            "out, keeps every report instead of the last two, and adds PDF export and side-by-side " +
            "comparison.",
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}
