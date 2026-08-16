package com.phoneproof.feature.sensortest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.sensors.SensorProbe

/**
 * Stateful entry point, and nothing else — [SensorTestScreen] owns its own insets, padding and title
 * so that what the screenshot tests render is exactly what a buyer sees.
 *
 * No permission gate, and that is not an omission. Motion and environment sensors below 200 Hz need
 * nothing declared and nothing granted, so this test asks a stranger for no access to their phone at
 * all — which is worth more to the buyer standing there than any dialog copy could be.
 */
@Composable
fun SensorTestRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val probe = remember(context) { SensorProbe(context) }
    val viewModel: SensorTestViewModel = viewModel { SensorTestViewModel(probe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.results) {
        if (state.results.isNotEmpty()) onResults(state.results)
    }

    SensorTestScreen(
        state = state,
        onStart = viewModel::start,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}
