package com.phoneproof.feature.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CategoryChip
import com.phoneproof.core.designsystem.component.CheckCategory
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.component.glyph
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.designsystem.theme.PhoneProofType
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.nounFor

/**
 * The scan.
 *
 * While it runs, the checks are shown as a list working top to bottom. Once finished, the same list
 * becomes the report. Keeping it one continuous surface rather than a spinner that swaps for results
 * means the buyer watches the thing they are about to read being produced — which is the point of
 * showing progress at all.
 */
@Composable
fun ScanScreen(
    state: ScanUiState,
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
        Header(state)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.steps, key = { it.id }) { step ->
                when {
                    step.result != null -> AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220)) +
                            slideInVertically(tween(220)) { it / 6 },
                    ) {
                        CheckResultCard(step.result)
                    }
                    else -> PendingRow(step)
                }
            }
        }

        if (state.finished) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(bottom = 4.dp),
            ) {
                Text("Scan again")
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(state: ScanUiState) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (state.finished) {
                "What this phone says about itself"
            } else {
                "Checking this phone…"
            },
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofColors.TextPrimary,
        )

        if (state.finished) {
            Tally(state)
        } else {
            ProgressLine(state)
        }
    }
}

@Composable
private fun ProgressLine(state: ScanUiState) {
    val target by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(280),
        label = "scanProgress",
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.runningLabel ?: "Starting…",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofColors.TextSecondary,
            )
            Text(
                text = "${state.doneCount}/${state.steps.size}",
                style = PhoneProofType.Numeric,
                color = PhoneProofColors.TextTertiary,
            )
        }
        // A determinate bar, not a spinner. The buyer can see how much is left, and it finishes
        // rather than looping — nothing in this app animates forever.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PhoneProofColors.BorderStrong),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(target)
                    .height(3.dp)
                    .background(PhoneProofColors.Accent),
            )
        }
    }
}

@Composable
private fun Tally(state: ScanUiState) {
    val results = state.results
    if (results.isEmpty()) return

    // "problem" is the only countable noun here, so it is the only one that needs a plural.
    // "to check", "can't tell" and "fine" read correctly at any count.
    val order = listOf(
        Triple(CheckOutcome.FAIL, "problem", true),
        Triple(CheckOutcome.CAUTION, "to check", false),
        Triple(CheckOutcome.UNKNOWN, "can't tell", false),
        Triple(CheckOutcome.PASS, "fine", false),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        order.forEach { (outcome, label, countable) ->
            val count = results.count { it.outcome == outcome }
            if (count > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "$count",
                        style = PhoneProofType.Numeric,
                        color = outcome.accent(),
                    )
                    Text(
                        text = if (countable) nounFor(count, label) else label,
                        style = MaterialTheme.typography.labelSmall,
                        color = PhoneProofColors.TextTertiary,
                    )
                }
            }
        }
    }
}

/** A check that has not produced a result yet: either waiting its turn, or running now. */
@Composable
private fun PendingRow(step: ScanStep) {
    val running = step.state == StepState.RUNNING
    val category = CheckCategory.forCheckId(step.id)

    val pulse by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(240),
        label = "stepPulse",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (running) PhoneProofColors.Surface else PhoneProofColors.Background,
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (running) category.tint.copy(alpha = 0.45f) else PhoneProofColors.Border,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(
                    category.tint.copy(alpha = 0.25f + 0.75f * pulse),
                    RoundedCornerShape(2.dp),
                ),
        )
        Text(
            text = step.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (running) PhoneProofColors.TextPrimary else PhoneProofColors.TextTertiary,
            fontWeight = if (running) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (running) {
            CategoryChip(category)
        } else {
            Text(
                text = CheckOutcome.UNKNOWN.glyph(),
                style = PhoneProofType.NumericSmall,
                color = PhoneProofColors.TextTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
