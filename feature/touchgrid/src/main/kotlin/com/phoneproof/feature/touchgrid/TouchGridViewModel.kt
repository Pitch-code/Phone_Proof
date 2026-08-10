package com.phoneproof.feature.touchgrid

import androidx.lifecycle.ViewModel
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
        val coverage = tracker.snapshot()
        val result = TouchCoverageEvaluator.evaluate(coverage)
        _uiState.value = _uiState.value.copy(
            touchedCells = coverage.touchedCells,
            phase = TouchTestPhase.FINISHED,
            result = result,
            highlightedCells = coverage.untouchedCells,
        )
    }

    fun onRetest() {
        tracker.reset()
        _uiState.value = TouchGridUiState(spec = tracker.spec)
    }
}
