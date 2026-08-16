package com.phoneproof.feature.storagespeed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.device.StorageSpeedProbe
import com.phoneproof.core.model.CheckResult

/**
 * Stateful entry point.
 *
 * No permission needed, because the test never leaves this app's own cache directory. That is also the reason
 * it is safe to run on a phone the buyer does not own: it cannot see or touch anything belonging to the
 * person selling it.
 */
@Composable
fun StorageSpeedRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val probe = remember(context) { StorageSpeedProbe(context) }
    val viewModel: StorageSpeedViewModel = viewModel { StorageSpeedViewModel(probe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.result) { state.result?.let { onResults(listOf(it)) } }

    StorageSpeedScreen(
        state = state,
        onStart = viewModel::start,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}
