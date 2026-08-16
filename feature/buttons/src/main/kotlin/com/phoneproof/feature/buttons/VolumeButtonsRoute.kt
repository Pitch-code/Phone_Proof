package com.phoneproof.feature.buttons

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult

/**
 * Stateful entry point, and the place the volume keys are actually caught.
 *
 * ## Consuming the presses
 *
 * `onKeyEvent` returns true for both volume keys, which stops the event reaching the system. That is
 * deliberate twice over. It keeps a stranger's phone from having its volume run to the top in a shop, and it
 * stops the volume panel sliding over the screen the buyer is being asked to watch.
 *
 * The cost is that a pressed button produces no visible effect anywhere except this screen — so the screen
 * says so in as many words. Without that sentence, an app that swallows the presses is indistinguishable
 * from a phone with two dead buttons, and this test would manufacture the fault it exists to find.
 *
 * ## Focus
 *
 * Key events only arrive at a focused node, so an invisible focusable box takes focus on entry. If the
 * request fails, the keys go nowhere — and the check is written so that hearing nothing is reported as "this
 * may be the app's fault" rather than as two broken buttons, which is exactly the case this protects.
 */
@Composable
fun VolumeButtonsRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
    viewModel: VolumeButtonsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester) {
        // runCatching, because requesting focus on a node that is not attached yet throws — and under
        // Robolectric it never attaches at all. A failure here must not take down the screen.
        runCatching { focusRequester.requestFocus() }
            .onFailure { Diagnostics.warn(TAG, "could not take focus; volume keys may not arrive", it) }
    }

    LaunchedEffect(state.result) { state.result?.let { onResults(listOf(it)) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val key = when (event.key) {
                    Key.VolumeUp -> VolumeKey.UP
                    Key.VolumeDown -> VolumeKey.DOWN
                    else -> return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> viewModel.onKeyDown(key)
                    KeyEventType.KeyUp -> viewModel.onKeyUp(key)
                    else -> Unit
                }
                // Consumed, so the system volume does not move and no volume panel appears over the test.
                true
            },
    ) {
        VolumeButtonsScreen(
            state = state,
            onAnswerPressedBoth = viewModel::answerPressedBoth,
            onFinish = viewModel::finish,
            onRestart = viewModel::restart,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val TAG = "VolumeButtons"
