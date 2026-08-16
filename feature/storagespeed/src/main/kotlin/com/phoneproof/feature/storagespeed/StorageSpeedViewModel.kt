package com.phoneproof.feature.storagespeed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.device.StorageSpeedCheck
import com.phoneproof.core.device.StorageSpeedProbe
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StorageSpeedStage {
    READY,

    /** Writing, syncing and reading back. */
    RUNNING,

    DONE,
}

@Immutable
data class StorageSpeedUiState(
    val stage: StorageSpeedStage = StorageSpeedStage.READY,
    /** 0f to 1f across the write and then the read. */
    val progress: Float = 0f,
    val freeBytes: Long = 0L,
    val result: CheckResult? = null,
) {
    val enoughSpace: Boolean get() = freeBytes >= StorageSpeedCheck.REQUIRED_FREE_BYTES
    val freeGb: Double get() = freeBytes / 1_073_741_824.0

    /** Halfway marks the switch from writing to reading, which the screen says out loud. */
    val writing: Boolean get() = progress < 0.5f
}

class StorageSpeedViewModel(
    private val probe: StorageSpeedProbe,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageSpeedUiState(freeBytes = probe.freeBytes()))
    val uiState: StateFlow<StorageSpeedUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun start() {
        job?.cancel()
        _uiState.update {
            it.copy(stage = StorageSpeedStage.RUNNING, progress = 0f, result = null)
        }

        job = viewModelScope.launch {
            val trace = probe.measure { fraction ->
                _uiState.update { it.copy(progress = fraction) }
            }
            _uiState.update {
                it.copy(
                    stage = StorageSpeedStage.DONE,
                    progress = 1f,
                    freeBytes = trace.freeBytes,
                    result = StorageSpeedCheck.evaluate(trace),
                )
            }
        }
    }

    fun restart() = start()

    override fun onCleared() {
        // Cancelling stops the writing. The probe deletes its file in a finally, so leaving the screen
        // mid-test cannot strand 64 MB on a stranger's phone.
        job?.cancel()
        super.onCleared()
    }
}
