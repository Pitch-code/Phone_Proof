package com.phoneproof.feature.sensortest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.sensors.SensorProbe

/**
 * Stateful entry point.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Sensors",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )
        SensorTestScreen(
            state = state,
            onStart = viewModel::start,
            onRestart = viewModel::restart,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
