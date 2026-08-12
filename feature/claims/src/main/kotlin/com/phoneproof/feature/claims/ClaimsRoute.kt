package com.phoneproof.feature.claims

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.phoneproof.checks.device.ClaimedSpecs
import com.phoneproof.checks.device.ClaimedSpecsCheck
import com.phoneproof.core.device.DeviceFactsReader
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult

@Composable
fun ClaimsRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // rememberSaveable throughout: a buyer typing three fields must not lose them to a rotation, or
    // to the keyboard resizing the window.
    var storage by rememberSaveable { mutableStateOf("") }
    var ram by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<CheckResult?>(null) }

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
