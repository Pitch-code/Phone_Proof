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
     * Cells under a strip Android keeps for its own edge gestures, so touches there may never
     * reach the app.
     *
     * Part of the state rather than something the canvas works out for itself, for two reasons: the
     * verdict needs the same set the drawing uses, and Robolectric reports no system bars at all,
     * so a screenshot test can only show this case if it can hand the set in directly.
     */
    val reservedCells: Set<Cell> = emptySet(),
) {
    val cellCount: Int get() = spec.cellCount
    val touchedCount: Int get() = touchedCells.size
    /** Over every cell, reachable or not. Kept for anything that wants the raw figure. */
    val coverageRatio: Float get() = touchedCount.toFloat() / cellCount.toFloat()

    /** Covered cells among those that can actually be reached. */
    val testableTouchedCount: Int get() = (touchedCells - reservedCells).size

    /**
     * The percentage the readout shows, over reachable cells only.
     *
     * It used to divide by every cell, so a finished test read "466 / 512, 91%" when 457 was the most
     * anyone could ever reach. That told the tester they had missed 46 tiles no app can touch, and
     * made the top and bottom of a perfectly good screen look broken. It reaches 100 when the job is
     * actually done.
     */
    val coveragePercent: Int get() = (testableCoverageRatio * 100f).toInt()

    /** Cells the tester can fairly be asked to reach. Mirrors `TouchCoverage.testableCellCount`. */
    val testableCellCount: Int get() = cellCount - reservedCells.size

    /**
     * Progress against reachable cells, which is what gates the finish control. The readout still
     * shows raw coverage, because that is what the tester sees themselves doing; gating on it
     * would strand them short of a verdict on a phone with wide gesture strips.
     */
    val testableCoverageRatio: Float
        get() {
            if (testableCellCount <= 0) return 0f
            return (touchedCells - reservedCells).size.toFloat() / testableCellCount.toFloat()
        }
}
