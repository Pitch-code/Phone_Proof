package com.phoneproof.feature.touchgrid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.phoneproof.checks.touch.FingersDown
import com.phoneproof.checks.touch.MultiTouchCheck
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MultiTouchStage {
    /** Explaining, before anything has been touched. */
    READY,

    /** Fingers on the glass, count rising. */
    COUNTING,

    /** The count fell short of the claim, so the buyer is being asked how many they managed. */
    ASKING,

    DONE,
}

@Immutable
data class MultiTouchUiState(
    val stage: MultiTouchStage = MultiTouchStage.READY,
    /** What the phone says it can do, for the target and for the report. */
    val claimedPoints: Int? = null,
    /** Fingers on the glass right now. */
    val current: Int = 0,
    /** The most seen at once during this attempt, which is the measurement. */
    val best: Int = 0,
    /** Where each finger is, so the screen can draw a numbered ring under it. */
    val positions: List<Offset> = emptyList(),
    val result: CheckResult? = null,
) {
    /** How many the buyer is being asked for: the phone's own claim, or a hand's worth. */
    val target: Int get() = claimedPoints ?: MultiTouchCheck.TARGET_FINGERS

    /** True once the measurement can only be a pass, so the screen can say so before they let go. */
    val reachedTarget: Boolean get() = best >= target
}

/**
 * Counts fingers, and keeps the best count.
 *
 * The measurement is the *maximum* simultaneous points, not the current count, because fingers never land
 * or lift together. A buyer placing five fingers passes through one, two, three and four on the way, and
 * lifting them passes back down — so anything reading the instantaneous count would report whatever
 * happened to be true at the moment the test ended.
 */
class MultiTouchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MultiTouchUiState())
    val uiState: StateFlow<MultiTouchUiState> = _uiState.asStateFlow()

    fun setClaimedPoints(points: Int?) {
        _uiState.update { if (it.claimedPoints == points) it else it.copy(claimedPoints = points) }
    }

    fun onPointers(positions: List<Offset>) {
        _uiState.update { state ->
            state.copy(
                stage = if (state.stage == MultiTouchStage.READY && positions.isNotEmpty()) {
                    MultiTouchStage.COUNTING
                } else {
                    state.stage
                },
                current = positions.size,
                best = maxOf(state.best, positions.size),
                positions = positions,
            )
        }
    }

    /**
     * Ends the attempt and judges it.
     *
     * Goes to the question only when the count fell short *and* the check itself says the matter is
     * unsettled. Asking after a pass would be asking a buyer to second-guess a measurement.
     */
    fun finish() {
        val state = _uiState.value
        val result = MultiTouchCheck.evaluate(
            maxObserved = state.best,
            claimedPoints = state.claimedPoints,
        )

        val worthAsking = state.best in 1 until state.target
        _uiState.update {
            it.copy(
                stage = if (worthAsking) MultiTouchStage.ASKING else MultiTouchStage.DONE,
                positions = emptyList(),
                current = 0,
                result = if (worthAsking) null else result,
            )
        }
    }

    fun answerFingersDown(allOfThem: Boolean) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                stage = MultiTouchStage.DONE,
                result = MultiTouchCheck.evaluate(
                    maxObserved = state.best,
                    claimedPoints = state.claimedPoints,
                    fingersDown = if (allOfThem) {
                        FingersDown.ALL_OF_THEM
                    } else {
                        FingersDown.FEWER
                    },
                ),
            )
        }
    }

    fun restart() {
        _uiState.value = MultiTouchUiState(claimedPoints = _uiState.value.claimedPoints)
    }
}
