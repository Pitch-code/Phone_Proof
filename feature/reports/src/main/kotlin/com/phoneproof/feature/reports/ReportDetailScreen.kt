package com.phoneproof.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.summaryLine

/**
 * One saved report, read back.
 *
 * Deliberately the same [CheckResultCard] the live scan uses. A report that looked different from
 * the screen it was taken on would invite the question of whether it had been altered, which is the
 * opposite of what a report is for.
 */
@Composable
fun ReportDetailScreen(
    report: SavedReport?,
    dateLabel: String,
    onShare: () -> Unit,
    onExportPdf: () -> Unit,
    /**
     * False on the free tier.
     *
     * The button stays visible and says why rather than disappearing. A feature that silently is not
     * there cannot be discovered, and a paid tier nobody knows about sells nothing — but a disabled
     * button that explains itself is also not a dark pattern, because it never pretends to work.
     */
    canExportPdf: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        if (report == null) {
            // Reachable in practice: a report can be pruned by a later scan while this screen is on
            // the back stack, so it has to say something rather than render an empty page.
            Spacer(Modifier.height(48.dp))
            Text(
                text = "This report is no longer saved.",
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            return@Column
        }

        Spacer(Modifier.height(14.dp))
        ScreenTitle(report.deviceLabel)
        Text(
            text = "${report.androidLabel} · $dateLabel",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = report.summaryLine(),
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(report.results, key = { it.id }) { result ->
                // emphasise = false: a saved report is read, not watched. A card breathing in a
                // history screen would imply something is happening right now.
                CheckResultCard(result, emphasise = false)
            }
        }

        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Share as text")
            }
            OutlinedButton(
                onClick = onExportPdf,
                enabled = canExportPdf,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (canExportPdf) "Save or print as PDF" else "PDF export is a Premium extra")
            }
        }
    }
}
