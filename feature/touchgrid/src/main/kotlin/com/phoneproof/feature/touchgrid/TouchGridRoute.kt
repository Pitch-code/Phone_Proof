package com.phoneproof.feature.touchgrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful entry point. Kept separate from [TouchGridScreen] so the screen itself stays a pure
 * function of its state and can be rendered to PNG without a ViewModel or a gesture.
 */
@Composable
fun TouchGridRoute(
    modifier: Modifier = Modifier,
    viewModel: TouchGridViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TouchGridScreen(
        state = state,
        onTouch = viewModel::onTouch,
        onFinish = viewModel::onFinish,
        onRetest = viewModel::onRetest,
        modifier = modifier,
    )
}
