package com.phoneproof.feature.touchgrid

import androidx.compose.runtime.Immutable
import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.GridSpec
import com.phoneproof.core.model.CheckResult

/** Where the test is in its lifecycle. */
enum class TouchTestPhase {
    /** Waiting for the first touch. */
    READY,

    /** The finger is down and cells are being covered. */
    IN_PROGRESS,

    /** The tester has finished and a verdict exists. */
    FINISHED,
}

/**
 * Everything the touch-grid screen needs to draw itself.
 *
 * Held as an immutable snapshot so the Canvas draw pass reads a consistent picture, and so the
 * screenshot tests can construct any state directly — including states that are tedious to reach
 * by hand, like a dead zone in one corner.
 */
@Immutable
data class TouchGridUiState(
    val spec: GridSpec = GridSpec.Default,
    val touchedCells: Set<Cell> = emptySet(),
    val phase: TouchTestPhase = TouchTestPhase.READY,
    val result: CheckResult? = null,
    /** Cells to emphasise after finishing. Highlighted once, never on a loop. */
    val highlightedCells: Set<Cell> = emptySet(),
    /**
     * Cells under a strip Android uses for its own edge gestures.
     *
     * No longer excused, and no longer drawn differently — they are swept like everywhere else. The
     * set is kept only so the verdict can say "the system may have taken that swipe" instead of
     * accusing the screen, and so a screenshot test can reach that case at all: Robolectric reports
     * no system bars, so it has to be handed in directly.
     */
    val systemGestureCells: Set<Cell> = emptySet(),
) {
    val cellCount: Int get() = spec.cellCount
    val touchedCount: Int get() = touchedCells.size

    /**
     * Progress over every cell, which is both what the readout shows and what gates the finish
     * control.
     *
     * There were two ratios here, one over all cells and one over "reachable" cells, and the finish
     * control gated on the smaller denominator so the edges could be skipped. With the edges part of
     * the test there is one number, and it only reaches 100 when the whole screen has been swept —
     * which is now the point rather than an obstacle.
     */
    val coverageRatio: Float
        get() = touchedCount.toFloat() / cellCount.toFloat()

    val coveragePercent: Int get() = (coverageRatio * 100f).toInt()
}
