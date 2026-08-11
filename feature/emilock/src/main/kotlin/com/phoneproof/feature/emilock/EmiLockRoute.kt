package com.phoneproof.feature.emilock

import androidx.compose.runtime.Composable
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

@Composable
fun EmiLockRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<CheckResult?>(null) }

    remember(revision) {
        // Reading device admins is a handful of synchronous platform calls, so there is no reason to
        // hop threads and add a loading state that flashes for one frame.
        val inspector = DeviceAdminInspector(context, Diagnostics.recorder)
        result = runCatching { EmiLockEvaluator.evaluate(inspector.snapshot()) }
            .onFailure { Diagnostics.error(TAG, "lock check failed", it) }
            .getOrNull()
        revision
    }

    EmiLockScreen(
        result = result,
        onRecheck = { revision++ },
        modifier = modifier,
    )
}

private const val TAG = "EmiLockRoute"
