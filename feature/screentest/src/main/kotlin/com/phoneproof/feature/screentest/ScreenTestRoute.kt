package com.phoneproof.feature.screentest

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.diagnostics.Diagnostics

@Composable
fun ScreenTestRoute(
    modifier: Modifier = Modifier,
    viewModel: ScreenTestViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Full brightness and no screen timeout, for as long as this screen is on top.
    //
    // Both are necessary rather than nice: a dim panel hides exactly the faint blotches this test
    // exists to reveal, and a screen dimming mid-inspection would look like a fault in itself.
    //
    // DisposableEffect, so both are handed back on the way out. Leaving the app pinned at full
    // brightness after the buyer navigates away would drain the seller's phone and be indefensible
    // in an app asking to be trusted. BRIGHTNESS_OVERRIDE_NONE restores the user's own setting
    // rather than a value guessed here.
    DisposableEffect(activity) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness

        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
        } else {
            // Worth a log rather than a silent shrug: the test still works, but a dim screen makes
            // a clean result much less meaningful.
            Diagnostics.info(TAG, "no activity window; brightness left as the user had it")
        }

        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply {
                    screenBrightness = previousBrightness
                        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    ScreenTestScreen(
        state = state,
        onStart = viewModel::onStart,
        onPatternSeen = viewModel::onPatternSeen,
        onStopEarly = viewModel::onStopEarly,
        onAnswer = viewModel::onAnswer,
        onRetest = viewModel::onRetest,
        modifier = modifier,
    )
}

/**
 * Walks the context chain to the Activity.
 *
 * `LocalContext.current` is not guaranteed to be an Activity — under Robolectric and in previews it
 * is not — so this returns null rather than casting and crashing the screenshot tests.
 */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private const val TAG = "ScreenTest"
