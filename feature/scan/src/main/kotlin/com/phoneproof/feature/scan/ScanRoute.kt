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
import com.phoneproof.checks.device.BatteryCheck
import com.phoneproof.checks.device.BuildIntegrityCheck
import com.phoneproof.checks.device.DeviceFacts
import com.phoneproof.checks.device.DisplayCheck
import com.phoneproof.checks.device.RootCheck
import com.phoneproof.checks.device.SecurityPatchCheck
import com.phoneproof.checks.device.SensorInventoryCheck
import com.phoneproof.checks.device.StorageCheck
import com.phoneproof.checks.emilock.EmiLockEvaluator
import com.phoneproof.core.device.BatteryFactsReader
import com.phoneproof.core.device.DeviceAdminInspector
import com.phoneproof.core.device.DeviceFactsReader
import com.phoneproof.core.device.RootSignalsReader
import com.phoneproof.core.device.androidLabel
import com.phoneproof.core.device.deviceLabel
import com.phoneproof.core.designsystem.SCAN_ALLOWANCE_UNLOCK
import com.phoneproof.core.designsystem.component.LockedFeature
import com.phoneproof.core.designsystem.scanAllowanceUsedUpExplanation
import com.phoneproof.core.designsystem.scanAllowanceUsedUpTitle
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.SettingsRepository
import com.phoneproof.core.reports.ReportStore
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.reportStore

private const val TAG = "ScanRoute"

@Composable
fun ScanRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
    /**
     * Whether to write its own saved report.
     *
     * False during a guided run, where the run writes one report containing the scan's findings *and*
     * everything else. Without this a single run would produce two reports — and on the free tier,
     * which keeps two, one run would fill the entire history and evict the previous phone.
     */
    saveOwnReport: Boolean = true,
    viewModel: ScanViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val settings = remember(context) { SettingsRepository(context) }
    val entitlement by remember(settings) { settings.entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)
    val scansUsed by remember(settings) { settings.scansUsed }
        .collectAsStateWithLifecycle(initialValue = 0)

    val retain = if (entitlement.hasPremiumExtras) {
        ReportStore.PREMIUM_RETAIN
    } else {
        ReportStore.FREE_TIER_RETAIN
    }

    // Declared before it is read, which the first attempt at this got wrong: the block below runs
    // before the scan is started, so the allowance has to be known by then.
    val outOfScans = !entitlement.hasUnlimitedScans && scansUsed >= Entitlement.FREE_SCAN_LIMIT

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

    if (outOfScans) {
        LockedFeature(
            title = scanAllowanceUsedUpTitle(Entitlement.FREE_SCAN_LIMIT),
            explanation = scanAllowanceUsedUpExplanation(Entitlement.FREE_SCAN_LIMIT),
            whatUnlockingGives = SCAN_ALLOWANCE_UNLOCK,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }

    // Keyed so the scan starts once, and only while there is an allowance left. Without outOfScans in
    // the key, a buyer arriving with no scans left would still burn one before the block appeared.
    LaunchedEffect(outOfScans) { if (!outOfScans) startScan() }

    LaunchedEffect(state.results, state.finished) {
        if (state.finished) onResults(state.results)
    }

    val scanId = state.scanId

    // The allowance is spent here, on its own, and deliberately not inside the save below.
    //
    // It used to sit at the end of the save block, which was fine while every scan wrote its own
    // report. Once a guided run took over the writing, `saveOwnReport = false` would have returned
    // early and skipped this too — so a buyer who only ever used the guided run would have had an
    // unlimited free trial, and the paywall would have quietly stopped existing.
    //
    // Counted at the point results appeared, rather than when the scan started: a scan that read
    // nothing must not cost a buyer one of their two.
    LaunchedEffect(scanId, state.finished) {
        if (scanId == null || !state.finished || state.results.isEmpty()) return@LaunchedEffect
        if (!entitlement.hasUnlimitedScans) {
            settings.recordScanUsed()
            Diagnostics.info(
                TAG,
                "free scan recorded (${scansUsed + 1}/${Entitlement.FREE_SCAN_LIMIT})",
            )
        }
    }

    // Saved without being asked. The moment a buyer wants a report is after they have handed the
    // phone back, and a "save this?" prompt is answered wrongly under pressure in front of a seller.
    //
    // Keyed on scanId, so this runs once per scan: the id is minted when the scan starts, and saving
    // the same id twice rewrites one file rather than duplicating the report.
    LaunchedEffect(scanId, state.finished, retain, saveOwnReport) {
        if (!saveOwnReport) return@LaunchedEffect
        if (scanId == null || !state.finished) return@LaunchedEffect
        val results = state.results
        if (results.isEmpty()) return@LaunchedEffect

        runCatching {
            // Retention follows what the buyer has paid for. Without this the Premium promise to
            // "keep every report instead of only the last two" would be false: the save path is
            // where pruning happens, so a hardcoded free limit here silently deletes a paying
            // customer's history no matter what the Settings screen says they bought.
            val pruned = reportStore(context.filesDir, retain).save(
                SavedReport(
                    id = scanId,
                    createdAtEpochMs = System.currentTimeMillis(),
                    deviceLabel = deviceLabel(),
                    androidLabel = androidLabel(),
                    results = results,
                ),
            )
            Diagnostics.info(TAG, "saved report $scanId (${results.size} results, $pruned pruned)")
        }.onFailure {
            // A failed save must never take down the screen showing results the buyer is reading.
            Diagnostics.error(TAG, "could not save report $scanId", it)
        }
    }

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

    // Read per scan rather than with the other facts, because charge, temperature and the charge
    // counter all move: a stale reading would make a rescan look like it re-measured when it had
    // not. Outside the facts != null block, since the battery has its own source and does not
    // depend on DeviceFactsReader having succeeded.
    tasks += ScanTask(BatteryCheck.CHECK_ID, "Measuring the battery") {
        BatteryCheck.evaluate(BatteryFactsReader(context, diagnostics).read())
    }

    return tasks
}
