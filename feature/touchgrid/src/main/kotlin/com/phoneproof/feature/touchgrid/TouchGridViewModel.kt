package com.phoneproof.feature.touchgrid

import androidx.lifecycle.ViewModel
import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.GridSpec
import com.phoneproof.checks.touch.TouchCoverageEvaluator
import com.phoneproof.checks.touch.TouchCoverageTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the coverage test.
 *
 * Note what is *not* here: no measurement logic and no verdict rules. Those live in
 * `checks:touch`, which is pure Kotlin and exhaustively unit tested. This class only marshals
 * pointer events into the tracker and publishes state, which keeps the part that decides whether
 * someone walks away from a purchase out of a class that needs Android to test.
 */
class TouchGridViewModel(
    spec: GridSpec = GridSpec.Default,
) : ViewModel() {

    private val tracker = TouchCoverageTracker(spec)

    private val _uiState = MutableStateFlow(TouchGridUiState(spec = spec))
    val uiState: StateFlow<TouchGridUiState> = _uiState.asStateFlow()

    /**
     * Records which cells sit under Android's own gesture strips, as measured from the live window
     * insets by the screen.
     *
     * Survives [onRetest] on purpose: the strips belong to the phone, not to the attempt, and
     * clearing them would make the first moments of a retest briefly judge cells it already knows
     * are unreadable.
     */
    private var reservedCells: Set<Cell> = emptySet()

    fun onReservedCellsChanged(cells: Set<Cell>) {
        if (cells == reservedCells) return
        reservedCells = cells
        _uiState.value = _uiState.value.copy(reservedCells = cells)
    }

    /**
     * Records a touch at a normalised position.
     *
     * Out-of-range values are rejected by the tracker rather than clamped, so a finger sliding
     * off the edge never credits coverage to a cell it did not actually reach.
     */
    fun onTouch(normalisedX: Float, normalisedY: Float) {
        // Returns null when the point was out of range or the cell was already covered, so a
        // fast drag reporting the same cell dozens of times rebuilds state exactly once.
        tracker.markNormalised(normalisedX, normalisedY) ?: return

        _uiState.value = _uiState.value.copy(
            touchedCells = tracker.snapshot().touchedCells,
            phase = TouchTestPhase.IN_PROGRESS,
            // A verdict from a previous attempt is cleared the moment testing resumes; showing a
            // stale result next to a changing grid would be misleading.
            result = null,
            highlightedCells = emptySet(),
        )
    }

    fun onFinish() {
        // The reserved set is attached here, at the point of judgement, so the evaluator can tell a
        // gap the platform caused from a gap the digitiser caused.
        val coverage = tracker.snapshot().copy(reservedCells = reservedCells)
        val result = TouchCoverageEvaluator.evaluate(coverage)
        _uiState.value = _uiState.value.copy(
            touchedCells = coverage.touchedCells,
            phase = TouchTestPhase.FINISHED,
            result = result,
            // Reserved cells are excluded from the highlight as well as from the verdict. They are
            // drawn in their own right, and flagging them in the verdict's colour would contradict
            // a PASS badge by painting the top of the screen as though it had failed.
            highlightedCells = coverage.untouchedCells - reservedCells,
        )
    }

    fun onRetest() {
        tracker.reset()
        // Carries the reserved set across, since it describes the phone rather than the attempt.
        _uiState.value = TouchGridUiState(spec = tracker.spec, reservedCells = reservedCells)
    }
}
