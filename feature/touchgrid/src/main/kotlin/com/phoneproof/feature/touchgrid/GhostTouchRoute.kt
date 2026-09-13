package com.phoneproof.feature.touchgrid

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope

/**
 * Stateful entry point for the ghost-touch watch.
 *
 * The clock lives here, not in the ViewModel: the ViewModel takes timestamps so it stays free of Android
 * and a test can run a full watch instantly, while the real ticking — a coroutine that nudges the countdown
 * a few times a second — belongs with the composition that is only alive while the screen is on show.
 *
 * `SystemClock.elapsedRealtime` rather than the wall clock, because a watch measured against the wall clock
 * would lurch if the time changed under it mid-run; elapsed real time only ever moves forward.
 */
@Composable
fun GhostTouchRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
    viewModel: GhostTouchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Runs only while a watch is in progress. Keyed on the stage so it starts when the watch begins and is
    // torn down the instant the watch ends, rather than spinning for the life of the screen.
    LaunchedEffect(state.stage) {
        if (state.stage == GhostTouchStage.WATCHING) {
            coroutineScope {
                while (isActive) {
                    viewModel.tick(SystemClock.elapsedRealtime())
                    delay(TICK_MILLIS)
                }
            }
        }
    }

    LaunchedEffect(state.result) { state.result?.let { onResults(listOf(it)) } }

    GhostTouchScreen(
        state = state,
        onStart = { viewModel.start(SystemClock.elapsedRealtime()) },
        onContact = { x, y -> viewModel.onContact(SystemClock.elapsedRealtime(), x, y) },
        onStop = { viewModel.stopEarly(SystemClock.elapsedRealtime()) },
        onAnswerHandsOff = viewModel::answerHandsOff,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}

/** A few times a second: often enough for a smooth countdown, rarely enough to cost nothing. */
private const val TICK_MILLIS: Long = 200
