package com.phoneproof.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.designsystem.theme.PhoneProofType
import com.phoneproof.core.diagnostics.DiagEntry
import com.phoneproof.core.diagnostics.DiagLevel

/**
 * The in-app log.
 *
 * This exists because during testing the only channel for "something broke" was a person describing
 * a symptom from memory. That is slow and lossy, and it is how a bug survives three rounds of
 * back-and-forth. A log the tester can copy in one tap turns a vague report into an exact one.
 *
 * Copy is offered before Share on purpose. Sharing depends on a share target existing and behaving,
 * and on the exact device that is already misbehaving that is one more thing that can fail. The
 * clipboard always works.
 */
@Composable
fun DiagnosticsScreen(
    entries: List<DiagEntry>,
    droppedCount: Int,
    header: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    formatTimestamp: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofColors.TextPrimary,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhoneProofColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, PhoneProofColors.Border, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = header,
                style = PhoneProofType.NumericSmall,
                color = PhoneProofColors.TextSecondary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${entries.size} events",
                style = PhoneProofType.NumericSmall,
                color = PhoneProofColors.TextTertiary,
            )
            if (droppedCount > 0) {
                // Surfaced rather than hidden: a truncated log that looks complete sends whoever
                // reads it hunting for events that were never included.
                Text(
                    text = "$droppedCount dropped",
                    style = PhoneProofType.NumericSmall,
                    color = PhoneProofColors.Caution,
                )
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = "Nothing recorded yet. Errors and crashes will appear here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofColors.TextTertiary,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Newest first: when something has just gone wrong, the relevant entry is the last one.
            reverseLayout = true,
        ) {
            items(entries) { entry -> EntryRow(entry, formatTimestamp) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onCopy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhoneProofColors.Accent,
                    contentColor = PhoneProofColors.TextPrimary,
                ),
            ) {
                Text("Copy log")
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text("Share")
            }
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: DiagEntry,
    formatTimestamp: (Long) -> String,
) {
    val accent = entry.level.accent()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, PhoneProofColors.Border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = entry.level.label,
                style = PhoneProofType.NumericSmall,
                color = accent,
            )
            Text(
                text = formatTimestamp(entry.timestampMillis),
                style = PhoneProofType.NumericSmall,
                color = PhoneProofColors.TextTertiary,
            )
            Text(
                text = entry.tag,
                style = PhoneProofType.NumericSmall,
                color = PhoneProofColors.TextSecondary,
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofColors.TextPrimary,
        )
        entry.stackTrace?.let {
            Text(
                text = it,
                style = PhoneProofType.NumericSmall,
                color = PhoneProofColors.TextTertiary,
            )
        }
    }
}

private fun DiagLevel.accent(): Color = when (this) {
    DiagLevel.INFO -> PhoneProofColors.TextSecondary
    DiagLevel.WARN -> PhoneProofColors.Caution
    DiagLevel.ERROR -> PhoneProofColors.Fail
    DiagLevel.CRASH -> PhoneProofColors.Fail
}
