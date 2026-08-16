package com.phoneproof.feature.vibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.device.VibrationDriver
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.sensors.SensorProbe

/**
 * Stateful entry point.
 *
 * No permissions at all: neither the vibration motor nor the accelerometer needs one. Worth noting because
 * this test does something quite intrusive to a stranger's phone — it makes it buzz — and still asks for
 * nothing.
 */
@Composable
fun VibrationRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val probe = remember(context) { SensorProbe(context) }
    val driver = remember(context) { VibrationDriver(context) }
    val viewModel: VibrationViewModel = viewModel { VibrationViewModel(probe, driver) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.result) { state.result?.let { onResults(listOf(it)) } }

    VibrationScreen(
        state = state,
        onStart = viewModel::start,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}
