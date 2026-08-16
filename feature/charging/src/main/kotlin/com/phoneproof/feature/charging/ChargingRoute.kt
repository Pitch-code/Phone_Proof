package com.phoneproof.feature.charging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.device.ChargingProbe
import com.phoneproof.core.model.CheckResult

/**
 * Stateful entry point. No permission: the battery broadcast is public.
 *
 * The ViewModel starts watching for a cable in its `init`, so the screen is already listening by the time it
 * is drawn — a buyer who arrives with the charger already plugged in should not have to unplug it to make the
 * test notice.
 */
@Composable
fun ChargingRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val probe = remember(context) { ChargingProbe(context) }
    val viewModel: ChargingViewModel = viewModel { ChargingViewModel(probe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.result) { state.result?.let { onResults(listOf(it)) } }

    ChargingScreen(
        state = state,
        onGiveUp = viewModel::giveUp,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}
