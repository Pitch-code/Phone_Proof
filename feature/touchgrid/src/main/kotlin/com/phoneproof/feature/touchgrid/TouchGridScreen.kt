package com.phoneproof.feature.touchgrid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.TouchCoverageEvaluator
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.theme.PhoneProofMotion
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
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
    onLayoutMeasured: (width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.phase == TouchTestPhase.FINISHED) {
        FinishedLayout(state = state, onRetest = onRetest, modifier = modifier)
    } else {
        TestingLayout(
            state = state,
            onTouch = onTouch,
            onFinish = onFinish,
            onLayoutMeasured = onLayoutMeasured,
            modifier = modifier,
        )
    }
}

@Composable
private fun TestingLayout(
    state: TouchGridUiState,
    onTouch: (Float, Float) -> Unit,
    onFinish: () -> Unit,
    onLayoutMeasured: (width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pointerDown by remember { mutableStateOf(false) }

    // Where the finger is, and where the readout sits, so the readout can get out of its own way.
    //
    // The readout is drawn on a scrim over the grid, which made the cells beneath it unreadable —
    // exactly the cells a tester sweeping the top of the screen is trying to check. Dropping the
    // scrim would make the text unreadable over covered cells instead, so neither one can simply
    // win: it fades out while the finger is over it and comes back when the finger leaves.
    var fingerAt by remember { mutableStateOf<Offset?>(null) }
    var readoutBounds by remember { mutableStateOf<Rect?>(null) }

    val fingerOverReadout = run {
        val finger = fingerAt
        val bounds = readoutBounds
        // Inflated, so the readout is already out of the way by the time the finger arrives rather
        // than fading as it crosses the edge.
        finger != null && bounds != null && pointerDown &&
            bounds.inflate(FADE_MARGIN_PX).contains(finger)
    }

    // Faded, not hidden. Going fully transparent would leave a tester mid-sweep with no idea how far
    // they had got, and removing it from layout would make the grid jump.
    val readoutAlpha by animateFloatAsState(
        targetValue = if (fingerOverReadout) 0.12f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "readoutAlpha",
    )
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Measured, not assumed. These insets differ between a gesture-navigation phone and a
    // three-button one, and between portrait and landscape.
    val gestureInsets = WindowInsets.systemGestures
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // Raw insets and the canvas size are reported upward; the cells are worked out by the ViewModel.
    //
    // It used to compute the cell set here and hand that over. The ViewModel needs the raw edge
    // thicknesses instead, because it keeps the *widest* it has seen: this screen hides the system
    // bars while the test runs, and doing so can take these insets to zero, which would otherwise
    // erase the app's knowledge of where the risky strips are at the exact moment it stopped being
    // able to re-measure them.
    LaunchedEffect(canvasSize, gestureInsets, density, layoutDirection) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        onLayoutMeasured(
            canvasSize.width,
            canvasSize.height,
            gestureInsets.getLeft(density, layoutDirection),
            gestureInsets.getTop(density),
            gestureInsets.getRight(density, layoutDirection),
            gestureInsets.getBottom(density),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .onSizeChanged { canvasSize = it },
    ) {
        CoverageCanvas(
            state = state,
            onTouch = onTouch,
            onPointerDownChange = { down ->
                pointerDown = down
                // Cleared on lift so the readout returns even if the finger left over it.
                if (!down) fingerAt = null
            },
            onPointerAt = { fingerAt = it },
            modifier = Modifier
                .fillMaxSize()
                // Asks Android not to take the back swipe over this surface, which is half of what
                // makes the edges testable at all. Until now the app measured the strips the system
                // was stealing and then forgave them, without ever asking it to stop.
                //
                // Only half, and the limits are worth knowing: this affects the back gesture on the
                // left and right edges, Android caps the exclusion at 200dp per edge, and it does
                // nothing for the shade at the top or the home swipe at the bottom. Those are what
                // hiding the system bars is for, in TouchGridRoute.
                .systemGestureExclusion(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onGloballyPositioned { readoutBounds = it.boundsInParent() }
                .alpha(readoutAlpha)
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
            //
            // Over every cell, matching the evaluator. It used to gate on a smaller denominator that
            // excluded the gesture strips, which let the test finish without them ever being swept —
            // the opposite of what is wanted now that the edges are the valuable part.
            val enoughToJudge =
                state.coverageRatio >= TouchCoverageEvaluator.MIN_COVERAGE_TO_JUDGE
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
            ResultActions(retestLabel = "Test again", onRetest = onRetest)
        }
    }
}

@Composable
private fun CoverageCanvas(
    state: TouchGridUiState,
    onTouch: (Float, Float) -> Unit,
    onPointerDownChange: (Boolean) -> Unit,
    /** Where the finger is, in canvas pixels. Used to move the readout out of the way. */
    onPointerAt: (Offset) -> Unit = {},
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
                    // Raw pixels, not the normalised pair above: the caller compares this against
                    // the readout's own bounds, which are in the same pixel space.
                    onPointerAt(position)
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
    // Opaque, not a 34% wash. On the light theme the old value produced the pale blue you could
    // barely see against a white background — and this fill is the entire feedback the test gives,
    // so it has to be unmistakable in a shop under bright light rather than merely present.
    val coveredColour = PhoneProofTheme.colors.accent
    val emptyColour = PhoneProofTheme.colors.gridEmpty

    // No third colour any more. The gesture strips used to be drawn in gridReserved with "no need to
    // touch here" written into them, which is the opposite of the instruction now: they do need
    // touching, and a dimmed band saying otherwise was the single clearest way to stop someone
    // sweeping the exact edges that matter most.
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

        // The "no need to touch here" labels that used to be drawn into the top and bottom bands are
        // gone with the bands. They were solving a problem that no longer exists — explaining why a
        // dimmed strip should be skipped — and if they stayed they would now be instructing the
        // tester to skip the part of the screen the test most wants covered.
    }
}

@Composable
private fun Readout(state: TouchGridUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            // One instruction now. The variant that said "cover everything inside the dimmed border"
            // existed because there *was* a border to stay inside; there is not, and the edges are
            // the part worth insisting on, so the wording says so plainly.
            text = when (state.phase) {
                TouchTestPhase.READY -> "Drag your finger over every part of the screen"
                else -> "Keep going — right into the edges and corners"
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
        // The caption that said "the dimmed edges belong to Android's own swipes ... left out of the
        // result" is gone. Nothing is left out of the result, so it would have been false — and it
        // told the tester not to bother with the edges, which is now the one thing worth insisting on.
        //
        // Replaced with the practical hint, because the edges are genuinely harder to sweep than the
        // middle and a tester who does not know the trick will conclude the screen is dead.
        if (state.phase != TouchTestPhase.READY && state.systemGestureCells.isNotEmpty()) {
            Text(
                text = "For the very top and bottom, start just inside the screen and drag " +
                    "outwards rather than swiping in from the edge.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}


/**
 * How far outside the readout a finger counts as being over it.
 *
 * Generous on purpose. The readout should already be faded by the time the fingertip reaches it, and
 * a fingertip covers far more of the screen than the single point the touch system reports.
 */
private const val FADE_MARGIN_PX = 90f
