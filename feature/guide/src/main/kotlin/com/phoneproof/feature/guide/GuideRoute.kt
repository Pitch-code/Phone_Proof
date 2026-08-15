package com.phoneproof.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phoneproof.core.designsystem.ADVISORY_TRIAL_EXCLUSION
import com.phoneproof.core.designsystem.MANUAL_CHECKS_TITLE
import com.phoneproof.core.designsystem.component.LockedFeature
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.SettingsRepository

@Composable
fun GuideRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entitlement by remember(context) { SettingsRepository(context).entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)

    if (!entitlement.hasAdvisoryTools) {
        LockedFeature(
            title = MANUAL_CHECKS_TITLE,
            // "It is part of a paid plan" was the whole explanation before, which read as though the
            // screen were a Premium extra and said nothing about the trial the reader is actually on.
            //
            // Opens "No app can test" rather than "Eight things no app can test": the render showed
            // the old opening repeating the first two words of the title directly beneath it.
            // "a moving diagram" until the diagrams stopped moving. A paywall describing a feature
            // the buyer has not seen yet is the last place in the app that should oversell it.
            explanation = "No app can test any of these for you — a twisted frame, a re-glued " +
                "screen, the water sticker in the SIM slot — each with a diagram showing " +
                "how to check it.\n\n" + ADVISORY_TRIAL_EXCLUSION,
            whatUnlockingGives = "The full walkthrough, including the account check that stops a " +
                "phone being locked remotely after you have paid. Also unlocks claimed against " +
                "measured, PDF reports and side-by-side comparison.",
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }

    // rememberSaveable, so rotating the phone mid-step does not collapse the card being read. Easy
    // to get wrong and irritating in exactly the situation this screen is used in: one hand on the
    // phone, someone waiting.
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    // No `animate` flag any more, and no read of Settings.Global.ANIMATOR_DURATION_SCALE. Both
    // existed to decide whether the diagrams should loop, and they no longer loop for anyone — so
    // asking the system whether the user tolerates motion is a question with one possible answer.
    GuideScreen(
        steps = GuideSteps,
        expandedId = expandedId,
        onToggle = { id -> expandedId = if (expandedId == id) null else id },
        modifier = modifier,
    )
}
