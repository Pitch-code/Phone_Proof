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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.component.decorative
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.reports.Comparison
import com.phoneproof.core.reports.ComparisonRow
import com.phoneproof.core.reports.ComparisonSide
import com.phoneproof.core.reports.SavedReport

/**
 * Two phones, side by side.
 *
 * Built for the actual situation: a buyer has looked at two handsets, probably on different days, and
 * wants to know which to go back for. Differences lead, because the rows where both phones agree are
 * exactly the rows that cannot help them decide.
 */
@Composable
fun CompareScreen(
    comparison: Comparison?,
    candidates: List<SavedReport>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        ScreenTitle(stringResource(R.string.compare_title))

        if (comparison == null) {
            Spacer(Modifier.height(6.dp))
            if (candidates.size < 2) {
                // The honest empty state. Comparison needs two reports, and a buyer who has taken
                // one is not doing anything wrong.
                Text(
                    text = pluralStringResource(
                        R.plurals.compare_need_second,
                        candidates.size,
                        candidates.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            } else {
                Text(
                    text = stringResource(R.string.compare_pick_second),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates, key = { it.id }) { report ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
                                .clickable { onPick(report.id) }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = report.deviceLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = PhoneProofTheme.colors.textPrimary,
                            )
                            Text(
                                text = "›",
                                // A glyph, not information. The row's own text says where it goes.
                                modifier = Modifier.decorative(),
                                style = MaterialTheme.typography.titleLarge,
                                color = PhoneProofTheme.colors.textTertiary,
                            )
                        }
                    }
                }
            }
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell(comparison.left.deviceLabel, Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            HeaderCell(comparison.right.deviceLabel, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        Verdict(comparison)
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Differences first. Rows where both agree cannot help anyone choose.
            val differing = comparison.differingRows
            val same = comparison.rows - differing.toSet()

            if (differing.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.compare_section_differ)) }
                items(differing, key = { "d-${it.checkId}" }) { CompareRow(it) }
            }
            if (same.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.compare_section_same)) }
                items(same, key = { "s-${it.checkId}" }) { CompareRow(it) }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = PhoneProofTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(10.dp))
            .padding(10.dp),
    )
}

@Composable
private fun Verdict(comparison: Comparison) {
    val better = comparison.clearlyBetter
    val text = when {
        comparison.differingRows.isEmpty() ->
            stringResource(R.string.compare_verdict_identical)
        better == ComparisonSide.LEFT ->
            stringResource(R.string.compare_verdict_better, comparison.left.deviceLabel)
        better == ComparisonSide.RIGHT ->
            stringResource(R.string.compare_verdict_better, comparison.right.deviceLabel)
        // Deliberately refuses to pick. Which faults matter is the buyer's call, and the app does
        // not know whether they care more about a battery or a screen.
        else -> stringResource(R.string.compare_verdict_mixed)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (better != null) PhoneProofTheme.colors.pass else PhoneProofTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(10.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun CompareRow(row: ComparisonRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(10.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutcomeCell(row.left, row.better == ComparisonSide.LEFT, Modifier.weight(1f))
            OutcomeCell(row.right, row.better == ComparisonSide.RIGHT, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OutcomeCell(
    outcome: CheckOutcome?,
    isBetter: Boolean,
    modifier: Modifier = Modifier,
) {
    // "Not tested" rather than a blank or a dash. A gap in a comparison table reads as a pass to
    // anyone skimming, and this phone simply was not checked for it.
    val label = outcome?.shortLabel() ?: stringResource(R.string.compare_not_tested)
    val colour = outcome?.accent() ?: PhoneProofTheme.colors.textTertiary

    // Read here rather than inside the semantics lambda, which is not a composable scope.
    val betterDescription = stringResource(R.string.compare_better_description)

    Row(
        modifier = modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
            fontWeight = if (isBetter) FontWeight.Bold else FontWeight.Normal,
        )
        if (isBetter) {
            Text(
                text = stringResource(R.string.compare_better_tick),
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.pass,
                fontWeight = FontWeight.Bold,
                // The tick and the bold weight are both invisible to a screen reader, so the only thing
                // marking the winning side would have been lost. This says it in words instead.
                modifier = Modifier
                    // The gap the string used to carry as a leading space, put where it belongs.
                    .padding(start = 4.dp)
                    .semantics { contentDescription = betterDescription },
            )
        }
    }
}

// A function rather than a property, because reading a resource needs composition.
@Composable
private fun CheckOutcome.shortLabel(): String = when (this) {
    CheckOutcome.PASS -> stringResource(R.string.outcome_short_pass)
    CheckOutcome.CAUTION -> stringResource(R.string.outcome_short_caution)
    CheckOutcome.FAIL -> stringResource(R.string.outcome_short_fail)
    CheckOutcome.UNKNOWN -> stringResource(R.string.outcome_short_unknown)
}
