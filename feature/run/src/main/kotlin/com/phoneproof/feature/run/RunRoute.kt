package com.phoneproof.feature.run

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phoneproof.core.device.androidLabel
import com.phoneproof.core.device.deviceLabel
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.PaidChecks
import com.phoneproof.core.preferences.SettingsRepository
import com.phoneproof.core.reports.ReportStore
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.reportStore
import com.phoneproof.core.run.RunPlan
import com.phoneproof.core.run.RunSession
import com.phoneproof.core.run.RunState
import com.phoneproof.core.run.RunStep
import com.phoneproof.core.run.RunVerdict
import com.phoneproof.core.run.StepEffort

private const val TAG = "RunRoute"

/**
 * The checklist, wired to the session.
 *
 * **Still carries no gate of its own**, and that part was always right: every step gates itself — the scan
 * against the free scan allowance, claims and the walkthrough against the advisory tier, and now
 * multi-touch, vibration and the radios against Premium. A second gate here would be a second place to
 * keep the same rules right.
 *
 * What it does now is *say so in advance*. The old comment here claimed a free-trial buyer was "entitled to
 * six steps", which was wrong twice over — it is ten of fifteen — and the screen said nothing at all about
 * the other five. So the run walked a buyer into a paywall five separate times, mid-inspection, in front of
 * a seller. Marking them costs nothing and removes every one of those surprises.
 */
@Composable
fun RunRoute(
    session: RunSession,
    onOpenStep: (String) -> Unit,
    onSeeVerdict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by session.state.collectAsStateWithLifecycle()
    val retain = rememberRetention()

    val settings = remember(context) { SettingsRepository(context) }
    val entitlement by remember(settings) { settings.entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)

    // Asked of PaidChecks rather than assembled here, so the marker on a run step, the marker on the same
    // check in the checks list, and the paywall itself all come from one place. Step ids are route names,
    // which is what makes this a direct lookup — RunPlanRoutesTest is what keeps that true.
    val lockedStepIds = remember(entitlement) {
        RunPlan.steps.map { it.id }.filter { PaidChecks.isLocked(it, entitlement) }.toSet()
    }

    // Written as the run goes rather than only at the end, keyed on the findings so far. An inspection
    // gets interrupted — the seller wants the phone back, the buyer's own phone rings — and a report
    // that only exists once the last step is finished is a report the interrupted buyer never gets.
    // Saving the same id repeatedly rewrites one file, so this costs one small write per step.
    LaunchedEffect(state.results, retain) { saveRunReport(context, state, retain) }

    RunChecklistScreen(
        state = state,
        onStart = session::start,
        onOpenStep = { step ->
            // A step the app cannot measure is marked as shown the moment the buyer opens it, because
            // "shown" is the most the app can honestly claim about the walkthrough. It produces no
            // results, so this changes no count in the verdict — it only stops the step being listed
            // as untested when the buyer has in fact been through it.
            if (step.effort == StepEffort.LOOK_YOURSELF) {
                session.markDone(step.id)
            }
            onOpenStep(step.id)
        },
        onSkip = { step -> session.skip(step.id) },
        onSeeVerdict = onSeeVerdict,
        modifier = modifier,
        lockedStepIds = lockedStepIds,
    )
}

/**
 * The verdict.
 *
 * Takes the session rather than a finished [RunVerdict] so that a buyer who goes back and tests
 * something from the "not tested" list sees the verdict change when they return. A verdict computed
 * once and passed in would keep showing the old one.
 */
@Composable
fun RunVerdictRoute(
    session: RunSession,
    onOpenStep: (String) -> Unit,
    onOpenReports: () -> Unit,
    onTestAnotherPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by session.state.collectAsStateWithLifecycle()
    val retain = rememberRetention()

    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(state.results, retain) {
        saved = saveRunReport(context, state, retain)
    }

    RunVerdictScreen(
        verdict = RunVerdict.of(state),
        deviceLabel = remember { "${deviceLabel()} · ${androidLabel()}" },
        savedToReports = saved,
        onOpenStep = { step: RunStep -> onOpenStep(step.id) },
        onOpenReports = onOpenReports,
        onTestAnotherPhone = {
            // Cleared here rather than on arriving at the checklist, so the buyer who leaves the
            // verdict open and comes back to it still sees what they measured.
            session.reset()
            onTestAnotherPhone()
        },
        modifier = modifier,
    )
}

/** History depth follows what the buyer has paid for, exactly as the standalone scan does. */
@Composable
private fun rememberRetention(): Int {
    val context = LocalContext.current
    val settings = remember(context) { SettingsRepository(context) }
    val entitlement by remember(settings) { settings.entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)
    return if (entitlement.hasPremiumExtras) {
        ReportStore.PREMIUM_RETAIN
    } else {
        ReportStore.FREE_TIER_RETAIN
    }
}

/**
 * Writes the run as a single saved report, and returns whether there is now one to read.
 *
 * One report per run, not one per step. The whole reason the run collects into
 * [com.phoneproof.core.reports.SavedReport] rather than a bespoke store is that a run then arrives in
 * the history screen, the PDF and the side-by-side comparison without any of them being taught about
 * runs at all.
 *
 * Failure is logged and swallowed. A report that cannot be written must never take down the screen a
 * buyer is reading in front of a seller.
 */
private suspend fun saveRunReport(context: Context, state: RunState, retain: Int): Boolean {
    val results = state.allResults
    if (!state.active || results.isEmpty()) return false

    return runCatching {
        val pruned = reportStore(context.filesDir, retain).save(
            SavedReport(
                // Minted from the moment the run started, so every save during one run rewrites one
                // file instead of leaving a trail of partial reports behind it.
                id = ReportStore.newId(state.startedAtEpochMs, "run"),
                createdAtEpochMs = state.startedAtEpochMs,
                deviceLabel = deviceLabel(),
                androidLabel = androidLabel(),
                results = results,
            ),
        )
        Diagnostics.info(TAG, "saved run report (${results.size} results, $pruned pruned)")
        true
    }.onFailure {
        Diagnostics.error(TAG, "could not save the run report", it)
    }.getOrDefault(false)
}
