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
     * The widest gesture insets seen this session, and the canvas they were measured against.
     *
     * **The widest, not the latest, and that is the whole reason this is stateful.** The screen hides
     * the system bars for the duration of the test so the edges can actually be touched — and hiding
     * them can take the gesture insets to zero with them. Tracking the maximum means the app does not
     * forget where the strips are the moment it succeeds in claiming them. Whether a given phone
     * reports zero once immersive is exactly the sort of platform behaviour that cannot be checked
     * without hardware, so this is written to be correct either way.
     *
     * Insets are kept as edge thicknesses in pixels rather than as a cell set, so a rotation changing
     * the canvas size recomputes the cells instead of unioning two incompatible grids together.
     *
     * Survives [onRetest] on purpose: the strips belong to the phone, not to the attempt.
     */
    private var gestureLeft = 0
    private var gestureTop = 0
    private var gestureRight = 0
    private var gestureBottom = 0
    private var canvasWidth = 0
    private var canvasHeight = 0

    private var gestureCells: Set<Cell> = emptySet()

    fun onLayoutMeasured(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) {
        gestureLeft = maxOf(gestureLeft, left)
        gestureTop = maxOf(gestureTop, top)
        gestureRight = maxOf(gestureRight, right)
        gestureBottom = maxOf(gestureBottom, bottom)
        canvasWidth = width
        canvasHeight = height

        val cells = systemGestureCells(
            spec = tracker.spec,
            width = canvasWidth,
            height = canvasHeight,
            left = gestureLeft,
            top = gestureTop,
            right = gestureRight,
            bottom = gestureBottom,
        )
        if (cells == gestureCells) return
        gestureCells = cells
        _uiState.value = _uiState.value.copy(systemGestureCells = cells)
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
        // The gesture set is attached at the point of judgement, so the evaluator can say a gap is
        // unattributable rather than pretending to know whose fault it is.
        val coverage = tracker.snapshot().copy(systemGestureCells = gestureCells)
        val result = TouchCoverageEvaluator.evaluate(coverage)
        _uiState.value = _uiState.value.copy(
            touchedCells = coverage.touchedCells,
            phase = TouchTestPhase.FINISHED,
            result = result,
            // Every uncovered cell is highlighted now, including the ones in the gesture strips. They
            // used to be excluded because the verdict forgave them and painting them would have
            // contradicted a PASS badge. The verdict no longer forgives them: it asks for another
            // sweep, and the tester needs to see *where*.
            highlightedCells = coverage.untouchedCells,
        )
    }

    fun onRetest() {
        tracker.reset()
        // Carries the gesture set across, since it describes the phone rather than the attempt.
        _uiState.value = TouchGridUiState(spec = tracker.spec, systemGestureCells = gestureCells)
    }
}
