package com.phoneproof.feature.touchgrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.device.claimedTouchPoints
import com.phoneproof.core.model.CheckResult

/**
 * Stateful entry point for the multi-touch test.
 *
 * The phone's claimed capability is read here and pushed into the ViewModel, rather than read inside it,
 * so the ViewModel stays free of a `Context` and the screen can be rendered at any claimed value the
 * screenshot tests care about — including a phone that claims nothing.
 */
@Composable
fun MultiTouchRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
    viewModel: MultiTouchViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(context) { viewModel.setClaimedPoints(claimedTouchPoints(context)) }

    LaunchedEffect(state.result) { state.result?.let { onResults(listOf(it)) } }

    MultiTouchScreen(
        state = state,
        onPointers = viewModel::onPointers,
        onFinish = viewModel::finish,
        onAnswerFingersDown = viewModel::answerFingersDown,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}
