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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.SettingsRepository
import com.phoneproof.core.preferences.ShopBrandingPreference
import com.phoneproof.core.reports.ReportStore
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.ShopBranding
import com.phoneproof.core.reports.asPlainText
import com.phoneproof.core.reports.reportStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportsRoute(
    onOpenReport: (String) -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { reportStore(context.filesDir) }
    // Note: reading only. Pruning happens on save, in ScanRoute, where the entitlement is applied.

    val settings = remember(context) { SettingsRepository(context) }
    val entitlement by remember(settings) { settings.entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)
    val unlimited = entitlement.hasPremiumExtras

    val viewModel: ReportsViewModel = viewModel(
        key = "reports-$unlimited",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReportsViewModel(
                store,
                retained = if (unlimited) ReportStore.PREMIUM_RETAIN else ReportStore.FREE_TIER_RETAIN,
                unlimited = unlimited,
            ) as T
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ReportsScreen(
        state = state,
        formatDate = ::formatReportDate,
        onOpenReport = onOpenReport,
        onCompare = onCompare,
        modifier = modifier,
    )
}

@Composable
fun ReportDetailRoute(
    reportId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { reportStore(context.filesDir) }
    val settings = remember(context) { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val entitlement by remember(settings) { settings.entitlement }
        .collectAsStateWithLifecycle(initialValue = Entitlement.FREE)
    val branding by remember(settings) { settings.shopBranding }
        .collectAsStateWithLifecycle(initialValue = ShopBrandingPreference())

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
        canExportPdf = entitlement.hasPremiumExtras,
        onExportPdf = {
            if (current != null) {
                scope.launch {
                    exportAndSharePdf(
                        context = context,
                        report = current,
                        // Branding is applied only on the Shop tier. A Premium buyer gets the PDF
                        // without a shop header, which is what they paid for.
                        branding = if (entitlement.hasShopBranding) {
                            ShopBranding(
                                name = branding.name,
                                contact = branding.contact,
                                logoPath = branding.logoPath,
                            )
                        } else {
                            ShopBranding.None
                        },
                    )
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Writes the PDF and hands it to another app.
 *
 * Runs off the main thread: drawing several pages and encoding a bitmap is not instant, and a buyer
 * tapping Export should not see the app freeze. Failures are logged and swallowed rather than
 * crashing the screen showing the report they are reading.
 */
private suspend fun exportAndSharePdf(
    context: android.content.Context,
    report: SavedReport,
    branding: ShopBranding,
) {
    val file = withContext(Dispatchers.IO) {
        runCatching {
            ReportPdfWriter(context).write(
                report = report,
                dateLabel = formatReportDate(report.createdAtEpochMs),
                branding = branding,
            )
        }.onFailure { Diagnostics.error(TAG, "writing the PDF failed", it) }.getOrNull()
    } ?: return

    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    }.onFailure {
        // Almost always a misconfigured provider authority, which is invisible until it is used.
        Diagnostics.error(TAG, "could not make a shareable link for the PDF", it)
    }.getOrNull() ?: return

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "PhoneProof report — ${report.deviceLabel}")
        // Without this the receiving app gets a URI it is not allowed to open.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Send or print this report"))
    }.onFailure { Diagnostics.error(TAG, "no app could take the PDF", it) }
    Diagnostics.info(TAG, "exported report ${report.id} as ${file.name}")
}

private const val TAG = "Reports"
