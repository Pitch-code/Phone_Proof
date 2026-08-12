package com.phoneproof.feature.reports

import androidx.compose.runtime.Immutable
import com.phoneproof.core.reports.SavedReport

@Immutable
data class ReportsUiState(
    val loading: Boolean = true,
    val reports: List<SavedReport> = emptyList(),
    /**
     * How many stored files could not be read.
     *
     * Surfaced rather than hidden. Someone who saved three reports and sees two must be told the
     * third is damaged, or the app looks like it silently loses their work.
     */
    val unreadableCount: Int = 0,
    /** How many reports this install keeps. Shown so pruning never looks like data loss. */
    val retained: Int = 0,
    /** True once a paid tier lifts the history limit. */
    val unlimited: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && reports.isEmpty() && unreadableCount == 0
}
