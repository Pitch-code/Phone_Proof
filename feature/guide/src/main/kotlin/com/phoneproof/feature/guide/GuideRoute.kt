package com.phoneproof.feature.guide

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.phoneproof.core.designsystem.theme.rememberAnimationsEnabled
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.SettingsRepository

@Composable
fun GuideRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entitlement by remember(context) { SettingsRepository(context).effectiveEntitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)

    if (!entitlement.hasAdvisoryTools) {
        LockedFeature(
            title = MANUAL_CHECKS_TITLE,
            // "It is part of a paid plan" was the whole explanation before, which read as though the
            // screen were a Premium extra and said nothing about the trial the reader is actually on.
            //
            // Opens "No app can test" rather than "Eight things no app can test": the render showed
            // the old opening repeating the first two words of the title directly beneath it.
            //
            // "Moving" is accurate again — the diagrams animate while a step is open. It was removed
            // while they held still, because a paywall describing a feature the buyer has not seen yet
            // is the last place in the app that should oversell it, and it has to track the truth in
            // both directions.
            explanation = "No app can test any of these for you — a twisted frame, a re-glued " +
                "screen, the water sticker in the SIM slot — each with a moving diagram showing " +
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

    val animate = rememberAnimationsEnabled()

    val photoStore = remember(context) { WalkthroughPhotos(context) }
    var photos by remember { mutableStateOf(photoStore.all()) }
    // Which step the camera was launched for. The result callback reports success without saying what it
    // was for, so this has to be remembered across the trip out to the camera app — and rememberSaveable,
    // because that trip can take this process down on a phone short of memory, which is most of them.
    var capturingStepId by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val stepId = capturingStepId
        capturingStepId = null
        if (stepId == null) return@rememberLauncherForActivityResult

        // A cancelled capture leaves the zero-byte file that had to exist before the URI could be handed
        // over. Cleared here, or it would show up as a photograph and render as a broken thumbnail — which
        // reads as a bug rather than as the cancellation it was.
        if (!saved) photoStore.discardIfEmpty(stepId)
        photos = photoStore.all()
    }

    GuideScreen(
        steps = GuideSteps,
        expandedId = expandedId,
        animate = animate,
        photos = photos,
        onToggle = { id -> expandedId = if (expandedId == id) null else id },
        onTakePhoto = { id ->
            val target = photoStore.captureTarget(id)
            if (target == null) {
                Diagnostics.error(TAG, "no capture target for $id")
            } else {
                capturingStepId = id
                // ActivityNotFoundException on a device with no camera app is a real possibility, and it
                // must not take the screen down with it.
                runCatching { camera.launch(target) }
                    .onFailure {
                        capturingStepId = null
                        Diagnostics.error(TAG, "no camera app to launch", it)
                    }
            }
        },
        onSharePhoto = { id ->
            photoStore.shareIntent(id)?.let { intent ->
                runCatching { context.startActivity(intent) }
                    .onFailure { Diagnostics.error(TAG, "sharing failed", it) }
            }
        },
        onDeletePhoto = { id ->
            photoStore.delete(id)
            photos = photoStore.all()
        },
        modifier = modifier,
    )
}

private const val TAG = "GuideRoute"

