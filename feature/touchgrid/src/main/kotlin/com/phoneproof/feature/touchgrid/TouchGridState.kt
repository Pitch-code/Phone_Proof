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
) {
    val cellCount: Int get() = spec.cellCount
    val touchedCount: Int get() = touchedCells.size
    val coverageRatio: Float get() = touchedCount.toFloat() / cellCount.toFloat()
    val coveragePercent: Int get() = (coverageRatio * 100f).toInt()
}
