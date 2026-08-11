package com.phoneproof.feature.touchgrid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.TouchCoverageEvaluator
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.PhoneProofMotion
import com.phoneproof.core.designsystem.theme.PhoneProofType

/**
 * The touch coverage test.
 *
 * Two distinct layouts, and the split is deliberate:
 *
 *  - **While testing**, the grid owns the entire screen and there are *no tappable controls over
 *    it*. Any button sitting on the test surface would swallow touches for the area underneath,
 *    and the bottom edge — the most common place for a dead strip — is exactly where a button bar
 *    would sit. That would have made the test silently unable to check its most important region.
 *  - **When finished**, the grid shrinks to a map above the verdict, so the highlighted defect is
 *    never hidden by the panel describing it.
 *
 * Stateless by design, which is what lets the screenshot tests render a corner dead zone without
 * performing a gesture.
 */
@Composable
fun TouchGridScreen(
    state: TouchGridUiState,
    onTouch: (Float, Float) -> Unit,
    onFinish: () -> Unit,
    onRetest: () -> Unit,
    onReservedCellsChanged: (Set<Cell>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.phase == TouchTestPhase.FINISHED) {
        FinishedLayout(state = state, onRetest = onRetest, modifier = modifier)
    } else {
        TestingLayout(
            state = state,
            onTouch = onTouch,
            onFinish = onFinish,
            onReservedCellsChanged = onReservedCellsChanged,
            modifier = modifier,
        )
    }
}

@Composable
private fun TestingLayout(
    state: TouchGridUiState,
    onTouch: (Float, Float) -> Unit,
    onFinish: () -> Unit,
    onReservedCellsChanged: (Set<Cell>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pointerDown by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Measured, not assumed. These insets differ between a gesture-navigation phone and a
    // three-button one, and between portrait and landscape.
    val gestureInsets = WindowInsets.systemGestures
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val reserved = remember(state.spec, canvasSize, gestureInsets, density, layoutDirection) {
        reservedCells(
            spec = state.spec,
            width = canvasSize.width,
            height = canvasSize.height,
            left = gestureInsets.getLeft(density, layoutDirection),
            top = gestureInsets.getTop(density),
            right = gestureInsets.getRight(density, layoutDirection),
            bottom = gestureInsets.getBottom(density),
        )
    }

    // Reported rather than used locally, because the verdict is produced by the ViewModel and has
    // to be built from the same set that is drawn.
    LaunchedEffect(reserved) { onReservedCellsChanged(reserved) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .onSizeChanged { canvasSize = it },
    ) {
        CoverageCanvas(
            state = state,
            onTouch = onTouch,
            onPointerDownChange = { pointerDown = it },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Only the overlay is inset. The canvas above deliberately keeps no inset at all,
                // because the test has to reach the true physical edges of the screen — insetting
                // it would leave the strips under the status and navigation bars untestable, and
                // those edges are where dead touch zones usually are.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                // A scrim, because this text sits directly on the grid and the grid changes colour
                // underneath it as cells fill in. Without it the smaller lines were unreadable over
                // covered cells. It costs a few rows of visible grid state, which is the lesser
                // problem: the counter above reports progress, whereas unreadable instructions
                // leave the tester with no idea what to do.
                .background(
                    PhoneProofTheme.colors.background.copy(alpha = 0.82f),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Text only, so it never intercepts a touch and never shadows a cell.
            Readout(state)

            // Appears only once there is enough coverage to reach a verdict, and only while the
            // finger is lifted. Until then the whole screen stays sweepable.
            // Measured against reachable cells, matching the evaluator. Gating on raw coverage would
            // hide this button on exactly the phones whose gesture strips make 90% unreachable.
            val enoughToJudge =
                state.testableCoverageRatio >= TouchCoverageEvaluator.MIN_COVERAGE_TO_JUDGE
            if (enoughToJudge && !pointerDown) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhoneProofTheme.colors.accent,
                        contentColor = PhoneProofTheme.colors.textPrimary,
                    ),
                ) {
                    Text("See the result")
                }
            }
        }
    }
}

@Composable
private fun FinishedLayout(
    state: TouchGridUiState,
    onRetest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            // Safe to inset the whole thing here: once the test is over the grid is a map of what
            // was found, not a surface anyone is still touching.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
        ) {
            CoverageCanvas(
                state = state,
                onTouch = { _, _ -> },
                onPointerDownChange = {},
                interactive = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.result?.let { CheckResultCard(it) }
            OutlinedButton(
                onClick = onRetest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Test again")
            }
        }
    }
}

@Composable
private fun CoverageCanvas(
    state: TouchGridUiState,
    onTouch: (Float, Float) -> Unit,
    onPointerDownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    // One animated value for the end-of-test emphasis, not one per cell. It settles and stops;
    // a looping animation would be an uncontrolled load, which this codebase treats as a bug.
    val highlight by animateFloatAsState(
        targetValue = if (state.phase == TouchTestPhase.FINISHED) 1f else 0f,
        animationSpec = PhoneProofMotion.singlePulse(),
        label = "uncoveredHighlight",
    )

    val gestureModifier = if (!interactive) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                if (width <= 0f || height <= 0f) return@awaitEachGesture

                fun report(position: Offset) {
                    onTouch(position.x / width, position.y / height)
                }

                fun report(change: PointerInputChange) {
                    // Historical samples matter: a fast drag reports far fewer events than the
                    // cells it physically crossed, and skipping them would invent dead zones.
                    change.historical.forEach { report(it.position) }
                    report(change.position)
                }

                // After awaitFirstDown, not before it. awaitEachGesture starts a fresh iteration of
                // this block the instant the previous gesture ends and then suspends here waiting
                // for the next finger, so flagging "down" above the suspend left the flag true from
                // first composition onwards and true again immediately after every lift. The one
                // control gated on the finger being up — "See the result" — could therefore never
                // appear, and the test had no way to finish.
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                onPointerDownChange(true)
                try {
                    report(firstDown)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) report(change)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    onPointerDownChange(false)
                }
            }
        }
    }

    // Falls back to Fail only when there is no verdict yet, which is a state where nothing is
    // flagged anyway.
    val flagColour = state.result?.outcome?.accent() ?: PhoneProofTheme.colors.fail

    // Hoisted out of the Canvas: the draw lambda is not a composable scope, so it cannot read the
    // palette. Reading them once per composition is also cheaper than once per cell — this loop
    // runs 512 times.
    val coveredColour = PhoneProofTheme.colors.accent.copy(alpha = 0.34f)
    val emptyColour = PhoneProofTheme.colors.gridEmpty

    val reservedColour = PhoneProofTheme.colors.gridReserved

    Canvas(modifier = modifier.then(gestureModifier)) {
        val cellWidth = size.width / state.spec.columns
        val cellHeight = size.height / state.spec.rows
        val gap = 1.dp.toPx()
        val cellSize = Size(
            width = (cellWidth - gap).coerceAtLeast(1f),
            height = (cellHeight - gap).coerceAtLeast(1f),
        )

        for (row in 0 until state.spec.rows) {
            for (column in 0 until state.spec.columns) {
                val cell = Cell(column, row)
                val covered = cell in state.touchedCells
                val flagged = highlight > 0f && cell in state.highlightedCells

                val reserved = cell in state.reservedCells

                val colour: Color = when {
                    // Before covered, so a reserved cell the finger did reach still reads as
                    // untestable. Its coverage was luck — the system could have taken that swipe —
                    // and showing it as confirmed would overstate what was measured.
                    reserved && !flagged -> reservedColour
                    // Flagged first: an uncovered cell that the verdict cares about must read as
                    // the loudest thing on screen. The colour comes from the verdict itself so
                    // the map cannot contradict the badge — marking scattered finger-skips in
                    // fail-red next to an amber "CHECK AGAIN" would tell the buyer the screen is
                    // broken when the app is only asking for another pass.
                    flagged -> flagColour.copy(alpha = 0.45f + 0.40f * highlight)
                    // Strong enough to be unmistakable at arm's length in a shop. The earlier
                    // 12% fill was invisible against the background in a rendered check.
                    covered -> coveredColour
                    else -> emptyColour
                }

                drawRect(
                    color = colour,
                    topLeft = Offset(column * cellWidth + gap / 2f, row * cellHeight + gap / 2f),
                    size = cellSize,
                )
            }
        }
    }
}

@Composable
private fun Readout(state: TouchGridUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = when {
                state.phase == TouchTestPhase.READY ->
                    "Drag your finger over every part of the screen"
                // Telling someone to cover the edges while the app is also telling them the edges
                // cannot be tested is a straight contradiction, and it was on screen together.
                state.reservedCells.isNotEmpty() ->
                    "Keep going — cover everything inside the dimmed border"
                else -> "Keep going — cover the edges and corners"
            },
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${state.touchedCount} / ${state.cellCount}",
                style = PhoneProofType.NumericLarge,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = "${state.coveragePercent}%",
                style = PhoneProofType.NumericLarge,
                color = PhoneProofTheme.colors.accent,
            )
        }
        Text(
            text = "cells covered",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        // Shown only when the phone actually reserves something, and said before the verdict rather
        // than after it. A tester who cannot cover the top edge needs to know why at the moment
        // they are struggling with it, not in a footnote once the result is already on screen.
        if (state.reservedCells.isNotEmpty()) {
            Text(
                text = "The dimmed edges belong to Android's own swipes. No app can test them, " +
                    "and they are left out of the result.",
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
