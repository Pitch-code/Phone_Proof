package com.phoneproof.feature.screentest

import androidx.lifecycle.ViewModel
import com.phoneproof.checks.device.ScreenDefectCheck
import com.phoneproof.checks.device.ScreenFinding
import com.phoneproof.core.diagnostics.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenTestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenTestUiState())
    val uiState: StateFlow<ScreenTestUiState> = _uiState.asStateFlow()

    fun onStart() {
        _uiState.value = ScreenTestUiState(phase = ScreenTestPhase.PATTERN, index = 0, viewed = 0)
    }

    /**
     * Advances past the pattern on screen.
     *
     * [ScreenTestUiState.viewed] increments here rather than when a pattern appears, so it counts
     * patterns actually looked at instead of patterns merely reached. The difference matters because
     * the check treats an incomplete run as UNKNOWN rather than a pass.
     */
    fun onPatternSeen() {
        val state = _uiState.value
        if (state.phase != ScreenTestPhase.PATTERN) return

        val seen = state.viewed + 1
        if (seen >= state.total) {
            _uiState.value = state.copy(phase = ScreenTestPhase.QUESTION, viewed = state.total)
        } else {
            _uiState.value = state.copy(index = state.index + 1, viewed = seen)
        }
    }

    /**
     * Ends the run early and still asks the question.
     *
     * Someone who spots a fault on the first pattern should be able to say so immediately, rather
     * than tapping through five more screens to reach the question. The partial [viewed] count
     * carries into the verdict, so a clean answer from a short run is reported as incomplete.
     */
    fun onStopEarly() {
        val state = _uiState.value
        if (state.phase != ScreenTestPhase.PATTERN) return
        _uiState.value = state.copy(phase = ScreenTestPhase.QUESTION)
    }

    fun onAnswer(finding: ScreenFinding) {
        val state = _uiState.value
        val result = ScreenDefectCheck.evaluate(
            finding = finding,
            patternsViewed = state.viewed,
            patternsTotal = state.total,
        )
        Diagnostics.info(
            TAG,
            "screen patterns: finding=$finding viewed=${state.viewed}/${state.total} " +
                "outcome=${result.outcome}",
        )
        _uiState.value = state.copy(phase = ScreenTestPhase.FINISHED, result = result)
    }

    fun onRetest() {
        _uiState.value = ScreenTestUiState()
    }

    private companion object {
        const val TAG = "ScreenTest"
    }
}
