package com.phoneproof.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.phoneproof.checks.device.BuildIntegrityCheck
import com.phoneproof.checks.device.DisplayCheck
import com.phoneproof.checks.device.SecurityPatchCheck
import com.phoneproof.checks.device.SensorInventoryCheck
import com.phoneproof.checks.device.StorageCheck
import com.phoneproof.checks.emilock.EmiLockEvaluator
import com.phoneproof.core.device.DeviceAdminInspector
import com.phoneproof.core.device.DeviceFactsReader
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult

@Composable
fun ScanRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<CheckResult>>(emptyList()) }

    remember(revision) {
        results = runCatching { runScan(context) }
            .onFailure { Diagnostics.error(TAG, "scan failed", it) }
            .getOrDefault(emptyList())
        revision
    }

    ScanScreen(
        results = results,
        onRescan = { revision++ },
        modifier = modifier,
    )
}

/**
 * Runs every check that needs nothing from the user.
 *
 * Ordered worst-first is tempting, but the list is ordered by *importance to a buyer* instead: the
 * remote-lock check leads because it is the one that can cost the entire purchase price. The tally
 * at the top of the screen is what directs attention to failures.
 *
 * Each check is wrapped individually so one misbehaving read cannot empty the whole report — the
 * least cooperative phone would otherwise produce the least information, which is backwards.
 */
private fun runScan(context: android.content.Context): List<CheckResult> {
    val diagnostics = Diagnostics.recorder
    val facts = DeviceFactsReader(context, diagnostics).read()
    val todayEpochDay = System.currentTimeMillis() / 86_400_000L

    val checks: List<Pair<String, () -> CheckResult>> = listOf(
        "emilock" to {
            EmiLockEvaluator.evaluate(DeviceAdminInspector(context, diagnostics).snapshot())
        },
        "integrity" to { BuildIntegrityCheck.evaluate(facts) },
        "patch" to { SecurityPatchCheck.evaluate(facts, todayEpochDay) },
        "storage" to { StorageCheck.evaluate(facts) },
        "sensors" to { SensorInventoryCheck.evaluate(facts) },
        "display" to { DisplayCheck.evaluate(facts) },
    )

    return checks.mapNotNull { (name, run) ->
        runCatching(run)
            .onFailure { Diagnostics.error(TAG, "check '$name' threw", it) }
            .getOrNull()
    }
}

private const val TAG = "ScanRoute"
