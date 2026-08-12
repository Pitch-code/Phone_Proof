package com.phoneproof.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.OutcomeBadge
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.summaryLine

/**
 * Saved reports.
 *
 * Every finished scan is kept without being asked for, because the moment a buyer wants a report is
 * after they have put the phone down — a "save this?" prompt would be answered wrongly under
 * pressure, in front of the seller.
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    formatDate: (Long) -> String,
    onOpenReport: (String) -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Saved reports",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )
        // Suppressed while there is nothing to retain. Announcing "this version keeps your last 2"
        // to someone with zero reports explains a limit they have not met and reads as an upsell on
        // an empty screen.
        if (state.reports.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = retentionLine(state),
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            state.loading -> Unit

            state.isEmpty -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = "No reports yet.",
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Test a phone and the report is kept here automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Offered only once there are two things to compare. A button that explains it needs
                // a second report is noise on a screen that already has one.
                if (state.reports.size >= 2) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    PhoneProofTheme.colors.surfaceRaised,
                                    RoundedCornerShape(12.dp),
                                )
                                .border(
                                    1.dp,
                                    PhoneProofTheme.colors.accent.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable(onClick = onCompare)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Compare two phones",
                                style = MaterialTheme.typography.titleMedium,
                                color = PhoneProofTheme.colors.accent,
                            )
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.titleLarge,
                                color = PhoneProofTheme.colors.accent,
                            )
                        }
                    }
                }

                items(state.reports, key = { it.id }) { report ->
                    ReportRow(
                        report = report,
                        dateLabel = formatDate(report.createdAtEpochMs),
                        onClick = { onOpenReport(report.id) },
                    )
                }

                if (state.unreadableCount > 0) {
                    item {
                        Text(
                            text = damagedLine(state.unreadableCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = PhoneProofTheme.colors.caution,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun retentionLine(state: ReportsUiState): String = when {
    state.unlimited -> "Every report is kept on this device."
    // States the limit up front rather than letting the oldest report disappear unexplained.
    else -> "This version keeps your last ${state.retained}. Premium keeps every one."
}

private fun damagedLine(count: Int): String = if (count == 1) {
    "1 saved report is damaged and could not be opened."
} else {
    "$count saved reports are damaged and could not be opened."
}

@Composable
private fun ReportRow(
    report: SavedReport,
    dateLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // The phone leads, not the date. Someone comparing two handsets is looking for which
            // phone this was, and a list of dates all reading "today" tells them nothing.
            Text(
                text = report.deviceLabel,
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = report.summaryLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
        OutcomeBadge(outcome = report.worstOutcome)
    }
}
