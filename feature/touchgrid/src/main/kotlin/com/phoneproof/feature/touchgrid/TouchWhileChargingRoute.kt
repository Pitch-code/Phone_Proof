package com.phoneproof.feature.touchgrid

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.device.ChargingProbe

/**
 * Stateful entry point for the touch-while-charging watch. No permission: the battery broadcast is public.
 *
 * The charge probe is created here and handed to the ViewModel, so the ViewModel depends only on the
 * [com.phoneproof.core.device.ChargeSource] interface and can be tested with a fake. The countdown ticks
 * from here with real timestamps, for the same reason the ghost-touch route does: the ViewModel takes time
 * as an argument and stays free of a clock.
 */
@Composable
fun TouchWhileChargingRoute(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val probe = remember(context) { ChargingProbe(context) }
    val viewModel: TouchWhileChargingViewModel = viewModel { TouchWhileChargingViewModel(probe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.stage) {
        if (state.stage == ChargeTouchStage.WATCHING) {
            while (true) {
                viewModel.tick(SystemClock.elapsedRealtime())
                kotlinx.coroutines.delay(TICK_MILLIS)
            }
        }
    }

    TouchWhileChargingScreen(
        state = state,
        onStart = { viewModel.start(SystemClock.elapsedRealtime()) },
        onContact = { x, y -> viewModel.onContact(SystemClock.elapsedRealtime(), x, y) },
        onStop = viewModel::stopEarly,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}

/** A few times a second: smooth countdown, negligible cost. */
private const val TICK_MILLIS: Long = 200
