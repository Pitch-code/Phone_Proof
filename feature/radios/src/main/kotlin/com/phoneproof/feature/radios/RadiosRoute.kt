package com.phoneproof.feature.radios

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.device.RadioProbe
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult

/**
 * Stateful entry point. No runtime permission: everything read here is install-time or permission-free, and
 * the settings panels are launched as plain intents.
 */
@Composable
fun RadiosRoute(
    modifier: Modifier = Modifier,
    /** No-op by default, so this screen never learns whether it is part of a guided run. */
    onResults: (List<CheckResult>) -> Unit = {},
) {
    val context = LocalContext.current
    val probe = remember(context) { RadioProbe(context) }
    val viewModel: RadiosViewModel = viewModel { RadiosViewModel(probe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.stage) {
        if (state.stage == RadiosStage.DONE) onResults(state.results)
    }

    RadiosScreen(
        state = state,
        onOpenSettings = { kind ->
            // Marked before launching rather than after: the buyer leaves the app immediately, and this flag
            // is what allows the follow-up question when they come back to a radio that is still off.
            viewModel.markSettingsVisited(kind)
            runCatching { context.startActivity(probe.settingsIntent(kind)) }
                .onFailure { error ->
                    if (error is ActivityNotFoundException) {
                        Diagnostics.warn(TAG, "no settings screen for $kind on this phone", error)
                    } else {
                        Diagnostics.error(TAG, "could not open settings for $kind", error)
                    }
                }
        },
        onAnswerEnableClaim = viewModel::answerEnableClaim,
        onFinish = viewModel::finish,
        onRestart = viewModel::restart,
        modifier = modifier,
    )
}

private const val TAG = "RadiosRoute"
