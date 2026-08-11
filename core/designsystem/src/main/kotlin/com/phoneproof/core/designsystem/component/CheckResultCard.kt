package com.phoneproof.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.designsystem.theme.PhoneProofType
import com.phoneproof.core.model.CheckResult

/**
 * One row of the inspection report, rendered the same way everywhere it appears.
 *
 * Shared rather than per-feature because the report card has to look identical whether a result is
 * shown right after its own test or alongside eleven others — and because consistency here is what
 * makes the whole thing read as one instrument rather than a pile of separate tools.
 *
 * Note what cannot go wrong: `consequence`, `action` and `falsePositiveCauses` are absent only for
 * PASS and CAN'T TELL, because `CheckResult` refuses to be constructed without them for anything
 * negative. This composable cannot render a bare verdict even if someone tries.
 */
@Composable
fun CheckResultCard(
    result: CheckResult,
    modifier: Modifier = Modifier,
) {
    val accent = result.outcome.accent()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PhoneProofColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, PhoneProofColors.Border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofColors.TextPrimary,
            )
            OutcomeBadge(result.outcome)
        }

        Text(
            text = result.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofColors.TextPrimary,
        )

        result.consequence?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofColors.TextSecondary,
            )
        }

        result.action?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }

        if (result.measurements.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                result.measurements.forEach { measurement ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = measurement.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhoneProofColors.TextTertiary,
                        )
                        Text(
                            text = measurement.display,
                            style = PhoneProofType.Numeric,
                            color = PhoneProofColors.TextPrimary,
                        )
                    }
                }
            }
        }

        if (result.falsePositiveCauses.isNotEmpty()) {
            // Prose, so it uses the prose face. Monospace is reserved for numbers — it exists to
            // stop digits shifting, and using it for a sentence just makes the sentence harder to
            // read while diluting the signal that a monospaced value is a measurement.
            Text(
                text = "Could this be wrong? " + result.falsePositiveCauses.first(),
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofColors.TextTertiary,
            )
        }
    }
}
