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
) {
    val results: List<CheckResult> get() = steps.mapNotNull { it.result }
    val doneCount: Int get() = steps.count { it.state == StepState.DONE }
    val runningLabel: String? get() = steps.firstOrNull { it.state == StepState.RUNNING }?.label

    /** 0f..1f, for the progress bar. */
    val progress: Float
        get() = if (steps.isEmpty()) 0f else doneCount.toFloat() / steps.size.toFloat()
}
