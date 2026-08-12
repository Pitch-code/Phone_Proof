package com.phoneproof.feature.reports

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.reports.ReportStore
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.asPlainText

@Composable
fun ReportsRoute(
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { reportStore(context) }

    val viewModel: ReportsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReportsViewModel(store, retained = ReportStore.FREE_TIER_RETAIN) as T
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ReportsScreen(
        state = state,
        formatDate = ::formatReportDate,
        onOpenReport = onOpenReport,
        modifier = modifier,
    )
}

@Composable
fun ReportDetailRoute(
    reportId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { reportStore(context) }

    // produceState, not a ViewModel: this screen holds one immutable record loaded by id and has no
    // state to survive a rotation beyond what the disk already holds.
    val report by produceState<SavedReport?>(initialValue = null, reportId) {
        value = runCatching { store.find(reportId) }
            .onFailure { Diagnostics.error(TAG, "could not read report $reportId", it) }
            .getOrNull()
    }

    val current = report
    ReportDetailScreen(
        report = current,
        dateLabel = current?.let { formatReportDate(it.createdAtEpochMs) } ?: "",
        onShare = {
            if (current != null) {
                val text = current.asPlainText(formatReportDate(current.createdAtEpochMs))
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "PhoneProof report — ${current.deviceLabel}")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                // A chooser rather than a direct launch, so the buyer picks WhatsApp or SMS on the
                // spot instead of the OS silently reusing whatever they shared with last.
                runCatching {
                    context.startActivity(Intent.createChooser(send, "Share this report"))
                }.onFailure { Diagnostics.error(TAG, "no app could share the report", it) }
                Diagnostics.info(TAG, "shared report ${current.id}")
            }
        },
        modifier = modifier,
    )
}

private const val TAG = "Reports"
