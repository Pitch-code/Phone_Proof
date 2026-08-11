package com.phoneproof.feature.touchgrid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.TouchCoverageEvaluator
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.theme.PhoneProofColors
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
    modifier: Modifier = Modifier,
) {
    if (state.phase == TouchTestPhase.FINISHED) {
        FinishedLayout(state = state, onRetest = onRetest, modifier = modifier)
    } else {
        TestingLayout(
            state = state,
            onTouch = onTouch,
            onFinish = onFinish,
            modifier = modifier,
        )
    }
}

@Composable
private fun TestingLayout(
    state: TouchGridUiState,
    onTouch: (Float, Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pointerDown by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofColors.Background),
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Text only, so it never intercepts a touch and never shadows a cell.
            Readout(state)

            // Appears only once there is enough coverage to reach a verdict, and only while the
            // finger is lifted. Until then the whole screen stays sweepable.
            val enoughToJudge = state.coverageRatio >= TouchCoverageEvaluator.MIN_COVERAGE_TO_JUDGE
            if (enoughToJudge && !pointerDown) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhoneProofColors.Accent,
                        contentColor = PhoneProofColors.TextPrimary,
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
            .background(PhoneProofColors.Background),
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

                onPointerDownChange(true)
                try {
                    report(awaitFirstDown(requireUnconsumed = false))
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
    val flagColour = state.result?.outcome?.accent() ?: PhoneProofColors.Fail

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

                val colour: Color = when {
                    // Flagged first: an uncovered cell that the verdict cares about must read as
                    // the loudest thing on screen. The colour comes from the verdict itself so
                    // the map cannot contradict the badge — marking scattered finger-skips in
                    // fail-red next to an amber "CHECK AGAIN" would tell the buyer the screen is
                    // broken when the app is only asking for another pass.
                    flagged -> flagColour.copy(alpha = 0.45f + 0.40f * highlight)
                    // Strong enough to be unmistakable at arm's length in a shop. The earlier
                    // 12% fill was invisible against the background in a rendered check.
                    covered -> PhoneProofColors.Accent.copy(alpha = 0.34f)
                    else -> PhoneProofColors.GridEmpty
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
            text = if (state.phase == TouchTestPhase.READY) {
                "Drag your finger over every part of the screen"
            } else {
                "Keep going — cover the edges and corners"
            },
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${state.touchedCount} / ${state.cellCount}",
                style = PhoneProofType.NumericLarge,
                color = PhoneProofColors.TextPrimary,
            )
            Text(
                text = "${state.coveragePercent}%",
                style = PhoneProofType.NumericLarge,
                color = PhoneProofColors.Accent,
            )
        }
        Text(
            text = "cells covered",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofColors.TextTertiary,
        )
    }
}
