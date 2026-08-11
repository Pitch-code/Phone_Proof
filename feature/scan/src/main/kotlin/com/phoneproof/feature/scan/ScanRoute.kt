package com.phoneproof.feature.scan

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.checks.device.BuildIntegrityCheck
import com.phoneproof.checks.device.DeviceFacts
import com.phoneproof.checks.device.DisplayCheck
import com.phoneproof.checks.device.RootCheck
import com.phoneproof.checks.device.SecurityPatchCheck
import com.phoneproof.checks.device.SensorInventoryCheck
import com.phoneproof.checks.device.StorageCheck
import com.phoneproof.checks.emilock.EmiLockEvaluator
import com.phoneproof.core.device.DeviceAdminInspector
import com.phoneproof.core.device.DeviceFactsReader
import com.phoneproof.core.device.RootSignalsReader
import com.phoneproof.core.diagnostics.Diagnostics

@Composable
fun ScanRoute(
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The facts are read once per scan attempt and shared by the checks that need them, so six
    // checks do not each re-read the same platform values.
    val startScan = remember(context) {
        {
            val facts = runCatching { DeviceFactsReader(context, Diagnostics.recorder).read() }
                .onFailure { Diagnostics.error("ScanRoute", "reading device facts failed", it) }
                .getOrNull()
            viewModel.start(tasks(context, facts))
        }
    }

    LaunchedEffect(Unit) { startScan() }

    ScanScreen(
        state = state,
        onRescan = startScan,
        modifier = modifier,
    )
}

/**
 * The scan, in the order a buyer should see it.
 *
 * Ordered by what it costs to get wrong, not by how fast each check runs: remote lock leads because
 * it is the only one here that can cost the entire purchase price. Every task is individually
 * fallible — the ViewModel catches per check — so one uncooperative read cannot empty the report.
 */
private fun tasks(context: Context, facts: DeviceFacts?): List<ScanTask> {
    val diagnostics = Diagnostics.recorder
    val tasks = mutableListOf<ScanTask>()

    tasks += ScanTask(EmiLockEvaluator.CHECK_ID, "Checking for remote lock control") {
        EmiLockEvaluator.evaluate(DeviceAdminInspector(context, diagnostics).snapshot())
    }

    // Second, because a rooted or unlocked phone undermines every measurement that follows it —
    // and because banking apps refusing to run is a problem the buyer discovers far too late.
    tasks += ScanTask(RootCheck.CHECK_ID, "Looking for root and an unlocked bootloader") {
        RootCheck.evaluate(RootSignalsReader(context, diagnostics).read())
    }

    if (facts != null) {
        val todayEpochDay = System.currentTimeMillis() / 86_400_000L
        tasks += ScanTask(BuildIntegrityCheck.CHECK_ID, "Verifying the software is genuine") {
            BuildIntegrityCheck.evaluate(facts)
        }
        tasks += ScanTask(SecurityPatchCheck.CHECK_ID, "Reading the security patch date") {
            SecurityPatchCheck.evaluate(facts, todayEpochDay)
        }
        tasks += ScanTask(StorageCheck.CHECK_ID, "Measuring storage") {
            StorageCheck.evaluate(facts)
        }
        tasks += ScanTask(SensorInventoryCheck.CHECK_ID, "Counting sensors") {
            SensorInventoryCheck.evaluate(facts)
        }
        tasks += ScanTask(DisplayCheck.CHECK_ID, "Testing the display") {
            DisplayCheck.evaluate(facts)
        }
    }

    return tasks
}
