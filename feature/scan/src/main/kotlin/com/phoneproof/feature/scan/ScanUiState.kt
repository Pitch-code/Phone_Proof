package com.phoneproof.feature.scan

import androidx.compose.runtime.Immutable
import com.phoneproof.core.model.CheckResult

enum class StepState { PENDING, RUNNING, DONE }

@Immutable
data class ScanStep(
    val id: String,
    val label: String,
    val state: StepState = StepState.PENDING,
    val result: CheckResult? = null,
)

@Immutable
data class ScanUiState(
    val steps: List<ScanStep> = emptyList(),
    val finished: Boolean = false,
    /**
     * Identifies this scan attempt, and becomes the saved report's id.
     *
     * Generated once when the scan starts so that saving is idempotent: recomposition, or returning
     * to this screen, rewrites the same file instead of filling the buyer's history with duplicates
     * of one scan. A fresh scan gets a fresh id and is therefore a separate report.
     */
    val scanId: String? = null,
) {
    val results: List<CheckResult> get() = steps.mapNotNull { it.result }
    val doneCount: Int get() = steps.count { it.state == StepState.DONE }
    val runningLabel: String? get() = steps.firstOrNull { it.state == StepState.RUNNING }?.label

    /** 0f..1f, for the progress bar. */
    val progress: Float
        get() = if (steps.isEmpty()) 0f else doneCount.toFloat() / steps.size.toFloat()
}
