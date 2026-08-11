package com.phoneproof.feature.emilock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.phoneproof.checks.emilock.EmiLockEvaluator
import com.phoneproof.core.device.DeviceAdminInspector
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.delay

@Composable
fun EmiLockRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<CheckResult?>(null) }

    // The read itself takes a few milliseconds. Left alone the verdict appears in the same frame as
    // the question, which reads as though nothing was examined — and the seller watching over the
    // buyer's shoulder sees no work happen at all. The check is real; only the pacing is deliberate,
    // and it matches the per-step timing used by the full scan.
    LaunchedEffect(revision) {
        result = null
        val startedAt = System.currentTimeMillis()
        val inspector = DeviceAdminInspector(context, Diagnostics.recorder)
        val verdict = runCatching { EmiLockEvaluator.evaluate(inspector.snapshot()) }
            .onFailure { Diagnostics.error(TAG, "lock check failed", it) }
            .getOrNull()
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < MIN_VISIBLE_MILLIS) delay(MIN_VISIBLE_MILLIS - elapsed)
        result = verdict
    }

    EmiLockScreen(
        result = result,
        onRecheck = { revision++ },
        modifier = modifier,
    )
}

private const val TAG = "EmiLockRoute"

/** Matches ScanViewModel.MIN_STEP_MILLIS so the two screens feel like one instrument. */
private const val MIN_VISIBLE_MILLIS = 320L
