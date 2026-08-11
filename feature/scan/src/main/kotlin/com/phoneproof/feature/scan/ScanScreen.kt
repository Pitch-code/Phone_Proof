package com.phoneproof.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.designsystem.theme.PhoneProofType
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult

/**
 * The silent scan: everything the phone can be asked directly, on one screen.
 *
 * Deliberately one screen for many checks rather than one screen per check. These all complete in
 * well under a second with no permission prompt and nothing for the buyer to do, so making them
 * separate destinations would add taps and time to a three-minute inspection for no information.
 */
@Composable
fun ScanScreen(
    results: List<CheckResult>,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "What this phone says about itself",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofColors.TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )

        Summary(results)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(results) { result -> CheckResultCard(result) }
        }

        OutlinedButton(
            onClick = onRescan,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 4.dp),
        ) {
            Text("Scan again")
        }
    }
}

/**
 * A one-line tally so the buyer knows where to look before reading anything.
 *
 * Counts are shown per outcome and each carries its word, never a colour alone — this is the row a
 * seller glances at, and it has to survive being screenshotted in greyscale.
 */
@Composable
private fun Summary(results: List<CheckResult>) {
    if (results.isEmpty()) {
        Text(
            text = "Reading…",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofColors.TextTertiary,
        )
        return
    }

    val order = listOf(
        CheckOutcome.FAIL to "problem",
        CheckOutcome.CAUTION to "to check",
        CheckOutcome.UNKNOWN to "can't tell",
        CheckOutcome.PASS to "fine",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        order.forEach { (outcome, label) ->
            val count = results.count { it.outcome == outcome }
            if (count > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "$count",
                        style = PhoneProofType.Numeric,
                        color = outcome.accent(),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = PhoneProofColors.TextTertiary,
                    )
                }
            }
        }
    }
}
