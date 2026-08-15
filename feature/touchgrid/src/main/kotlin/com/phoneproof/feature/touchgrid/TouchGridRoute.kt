package com.phoneproof.feature.touchgrid

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneproof.core.diagnostics.Diagnostics

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

    HideSystemBarsWhileTesting()

    TouchGridScreen(
        state = state,
        onTouch = viewModel::onTouch,
        onFinish = viewModel::onFinish,
        onRetest = viewModel::onRetest,
        onLayoutMeasured = viewModel::onLayoutMeasured,
        modifier = modifier,
    )
}

/**
 * Hides the status and navigation bars for as long as this screen is on top, and puts them back.
 *
 * This is the other half of making the edges testable, and the more important half. The app was
 * already edge-to-edge, so it *drew* under the bars — but drawing there and receiving touches there
 * are different things, and with the bars present the system answers a swipe at the top by opening
 * the shade and a swipe at the bottom by going home. The app never sees those touches. Measuring the
 * strips and forgiving them, which is what it used to do, was treating a fixable problem as a law of
 * nature.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` rather than hiding them outright, because a test screen
 * that traps someone with no way back to their notifications is a worse product than one with a
 * slightly smaller testable area. A deliberate swipe still brings the bars back temporarily.
 *
 * Reversed on dispose, and that matters more than it looks: leaving the bars hidden after navigating
 * away would strand the rest of the app in immersive mode, which nothing else here is laid out for.
 *
 * ## What is not promised
 *
 * This is not a guarantee that every edge touch now arrives. OEM skins differ in how much of the
 * home and shade gesture they keep while immersive, and the ones that keep the most are exactly the
 * heavily-skinned budget phones this app is aimed at. That is why the verdict still carries the
 * unattributable case: when a gap is left inside a gesture strip, the app says it could not tell
 * rather than blaming the screen. **None of this can be verified without hardware** — Robolectric
 * reports no system bars at all, which is also why this lives in the route rather than the screen,
 * so the screenshot tests never execute it.
 */
@Composable
private fun HideSystemBarsWhileTesting() {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }

        if (controller == null) {
            // Not fatal, and deliberately not a crash: the test still works, the edges are just
            // harder to reach. Recorded because a silent failure here would look like a dead screen.
            Diagnostics.warn(TAG, "no window controller; system bars stay visible")
        } else {
            runCatching {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }.onFailure { Diagnostics.error(TAG, "could not hide the system bars", it) }
        }

        onDispose {
            controller?.let {
                runCatching { it.show(WindowInsetsCompat.Type.systemBars()) }
                    .onFailure { error -> Diagnostics.error(TAG, "could not restore the bars", error) }
            }
        }
    }
}

private const val TAG = "TouchGridRoute"
