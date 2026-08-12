package com.phoneproof.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.core.reports.ReportStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportsViewModel(
    private val store: ReportStore,
    private val retained: Int,
    private val unlimited: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReportsUiState(retained = retained, unlimited = unlimited),
    )
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads from disk.
     *
     * Called on every visit rather than cached, because a scan finishing on another screen both
     * adds a report and can prune one. A stale list would offer the buyer a report that no longer
     * exists.
     */
    fun refresh() {
        viewModelScope.launch {
            val reports = store.list()
            val damaged = store.unreadableCount()
            _uiState.value = ReportsUiState(
                loading = false,
                reports = reports,
                unreadableCount = damaged,
                retained = retained,
                unlimited = unlimited,
            )
        }
    }
}
