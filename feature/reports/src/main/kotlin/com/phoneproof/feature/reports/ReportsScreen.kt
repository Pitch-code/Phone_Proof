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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.OutcomeBadge
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.component.decorative
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
        ScreenTitle(stringResource(R.string.reports_title))
        // Suppressed while there is nothing to retain. Announcing "this version keeps your last 2"
        // to someone with zero reports explains a limit they have not met and reads as an upsell on
        // an empty screen.
        if (state.reports.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = retentionLine(state),
                // bodyMedium, up from labelSmall. This line explains why an old report disappeared,
                // which is the one thing on this screen someone comes back confused about.
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
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
                    text = stringResource(R.string.reports_empty_headline),
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.reports_empty_detail),
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
                                text = stringResource(R.string.reports_compare_row),
                                style = MaterialTheme.typography.titleMedium,
                                color = PhoneProofTheme.colors.accent,
                            )
                            Text(
                                text = "›",
                                // A glyph, not information. The row's own text says where it goes.
                                modifier = Modifier.decorative(),
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

// @Composable so both can reach a resource. They are only ever called from composition, so this
// costs nothing and keeps the wording out of Kotlin.
@Composable
private fun retentionLine(state: ReportsUiState): String = when {
    state.unlimited -> stringResource(R.string.reports_retention_unlimited)
    // States the limit up front rather than letting the oldest report disappear unexplained.
    else -> stringResource(R.string.reports_retention_limited, state.retained)
}

// pluralStringResource rather than an if: this was the last `if (n == 1)` left in the codebase, and
// English putting its plural boundary at one is not a rule the target languages share.
@Composable
private fun damagedLine(count: Int): String =
    pluralStringResource(R.plurals.reports_damaged, count, count)

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
                // The date separates two reports of the same phone, so it has to be readable rather
                // than decorative.
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
        OutcomeBadge(outcome = report.worstOutcome)
    }
}
