package com.phoneproof.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.reports.ReportStore
import kotlin.random.Random
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One unit of work: a label to show while it runs, and the check itself. */
class ScanTask(
    val id: String,
    val label: String,
    val run: () -> CheckResult,
)

/**
 * Runs the scan one check at a time.
 *
 * The checks themselves take a few milliseconds, so left alone all six results appear in the same
 * frame. That is technically the fastest possible behaviour and it is the wrong behaviour: a buyer
 * cannot tell what was examined, and — more importantly — the seller standing there sees nothing
 * happen. Showing the work is part of what makes the report usable as an argument.
 *
 * So each step is held visible for [MIN_STEP_MILLIS]. The work is entirely real; only the pacing is
 * presentational, and it is capped low enough that the whole run still finishes in about two
 * seconds. This is a deliberate exception to the usual rule against decorative delay, and it is the
 * only one in the app.
 */
class ScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun start(tasks: List<ScanTask>) {
        job?.cancel()
        _uiState.value = ScanUiState(
            steps = tasks.map { ScanStep(id = it.id, label = it.label) },
            finished = false,
            // The suffix keeps two scans started in the same millisecond apart. The ViewModel mints
            // it rather than the screen, so it survives recomposition and cannot change mid-scan.
            scanId = ReportStore.newId(
                System.currentTimeMillis(),
                Random.nextInt(0x1000, 0xFFFF).toString(16),
            ),
        )

        job = viewModelScope.launch {
            tasks.forEachIndexed { index, task ->
                update(index) { it.copy(state = StepState.RUNNING) }

                val startedAt = System.currentTimeMillis()
                val result = runCatching(task.run)
                    .onFailure { Diagnostics.error(TAG, "check '${task.id}' threw", it) }
                    .getOrNull()

                // Hold the running state only for whatever is left of the minimum, so a slow check
                // never adds delay on top of its own work.
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_STEP_MILLIS) delay(MIN_STEP_MILLIS - elapsed)

                update(index) { it.copy(state = StepState.DONE, result = result) }
            }
            _uiState.value = _uiState.value.copy(finished = true)
            Diagnostics.info(TAG, "scan finished: ${_uiState.value.results.size} results")
        }
    }

    private fun update(index: Int, transform: (ScanStep) -> ScanStep) {
        val steps = _uiState.value.steps.toMutableList()
        if (index !in steps.indices) return
        steps[index] = transform(steps[index])
        _uiState.value = _uiState.value.copy(steps = steps)
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    companion object {
        /** Long enough to read a label, short enough that six checks take about two seconds. */
        const val MIN_STEP_MILLIS: Long = 320L
        private const val TAG = "ScanViewModel"
    }
}
