package com.phoneproof.feature.claims

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phoneproof.checks.device.ClaimedSpecs
import com.phoneproof.checks.device.ClaimedSpecsCheck
import com.phoneproof.core.designsystem.ADVISORY_TRIAL_EXCLUSION
import com.phoneproof.core.designsystem.MANUAL_CHECKS_TITLE
import com.phoneproof.core.designsystem.component.LockedFeature
import com.phoneproof.core.device.DeviceFactsReader
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.SettingsRepository
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult

@Composable
fun ClaimsRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val entitlement by remember(context) { SettingsRepository(context).entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)

    if (!entitlement.hasAdvisoryTools) {
        LockedFeature(
            title = "Claimed against measured",
            // Same fix as the guide's lock, and deliberately the same sentence: LockedFeature exists
            // so that every limit sounds alike, and one of the two advisory locks explaining itself
            // better than the other is the drift that component was built to prevent.
            explanation = "This compares what the seller told you against what the phone actually " +
                "reports — storage, memory and model.\n\n" + ADVISORY_TRIAL_EXCLUSION,
            // Was "the by-hand guide" — a name this feature has never carried on screen. Quoted,
            // because the title is a phrase and runs into the sentence around it otherwise.
            whatUnlockingGives = "Catch a phone sold as 128 GB that holds 32, or as 8 GB of memory " +
                "when it has 4. Also unlocks “$MANUAL_CHECKS_TITLE”, PDF reports and side-by-side " +
                "comparison.",
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }

    // rememberSaveable throughout: a buyer typing three fields must not lose them to a rotation, or
    // to the keyboard resizing the window.
    var storage by rememberSaveable { mutableStateOf("") }
    var ram by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<CheckResult?>(null) }

    LaunchedEffect(result) { result?.let { onResults(listOf(it)) } }

    ClaimsScreen(
        storage = storage,
        ram = ram,
        model = model,
        result = result,
        onStorageChanged = { storage = it },
        onRamChanged = { ram = it },
        onModelChanged = { model = it },
        onCompare = {
            // Facts are read at the moment of comparison, not when the screen opens. Free space and
            // the storage total can both move while someone is typing.
            val facts = runCatching { DeviceFactsReader(context, Diagnostics.recorder).read() }
                .onFailure { Diagnostics.error(TAG, "reading device facts failed", it) }
                .getOrNull()

            result = if (facts == null) {
                null
            } else {
                ClaimedSpecsCheck.evaluate(
                    claims = ClaimedSpecs(
                        storageGb = storage.toIntOrNull(),
                        ramGb = ram.toIntOrNull(),
                        modelName = model.ifBlank { null },
                    ),
                    facts = facts,
                ).also { Diagnostics.info(TAG, "claims compared: ${it.outcome}") }
            }
        },
        onReset = { result = null },
        modifier = modifier,
    )
}

private const val TAG = "Claims"
