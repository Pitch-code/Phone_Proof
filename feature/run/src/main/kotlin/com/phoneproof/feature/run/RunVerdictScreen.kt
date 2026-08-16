package com.phoneproof.feature.run

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.plural
import com.phoneproof.core.run.RunGrade
import com.phoneproof.core.run.RunPlan
import com.phoneproof.core.run.RunStep
import com.phoneproof.core.run.RunVerdict

/**
 * The end of the run: one sentence, then the evidence for it.
 *
 * Everything above the fold is the answer. The buyer is reading this with a seller a metre away and
 * money in their hand, so the screen leads with the verdict and the argument to make, and puts the
 * per-check detail underneath for whoever wants it.
 *
 * The passed checks come last on purpose. A list of green ticks is the most reassuring thing on the
 * screen and the least useful, and putting it first is how an inspection app talks someone into a bad
 * phone.
 */
@Composable
fun RunVerdictScreen(
    verdict: RunVerdict,
    deviceLabel: String,
    savedToReports: Boolean,
    onOpenStep: (RunStep) -> Unit,
    onOpenReports: () -> Unit,
    onTestAnotherPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        VerdictBanner(verdict)

        Text(
            text = deviceLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )

        CountsStrip(verdict)

        if (verdict.talkingPoints.isNotEmpty()) {
            SectionLabel("What to say to the seller")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(12.dp))
                    .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                verdict.talkingPoints.forEachIndexed { index, point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            color = PhoneProofTheme.colors.accent,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = point.finding,
                                style = MaterialTheme.typography.labelMedium,
                                color = PhoneProofTheme.colors.textTertiary,
                            )
                            Text(
                                text = point.sayThis,
                                style = MaterialTheme.typography.titleMedium,
                                color = PhoneProofTheme.colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }

        if (verdict.problems.isNotEmpty()) {
            SectionLabel(plural(verdict.problemCount, "problem"))
            verdict.problems.forEach { CheckResultCard(it) }
        }

        // Skipped and never-reached steps land under one heading, because the distinction matters to
        // the app and not to the buyer: either way nothing is known. Rebuilt in plan order rather than
        // concatenating the two lists, so the section reads in the same order as the run.
        val notTestedIds = (verdict.skipped + verdict.unmeasuredEssentials).map { it.id }.toSet()
        val notTested = RunPlan.steps.filter { it.id in notTestedIds }
        if (notTested.isNotEmpty()) {
            SectionLabel("Not tested")
            Text(
                text = "Nothing is known about these, one way or the other. Tap any of them to " +
                    "do it now.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            notTested.forEach { step ->
                NotTestedRow(step = step, onClick = { onOpenStep(step) })
            }
        }

        if (verdict.couldNotTell.isNotEmpty()) {
            SectionLabel("Could not be measured")
            Text(
                // Said out loud because the alternative is a buyer reading four grey cards as four
                // more passes. Android genuinely withholds some of this — battery health, the IMEI —
                // and the honest move is to name it as a gap rather than pad the pass count.
                text = "Android does not let any app read these. They are gaps in the report, not " +
                    "good news.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            verdict.couldNotTell.forEach { CheckResultCard(it, emphasise = false) }
        }

        if (verdict.passed.isNotEmpty()) {
            SectionLabel("${verdict.passCount} passed")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp),
            ) {
                // Compact rows rather than full cards. These are the least interesting lines in the
                // report and they must not out-shout the problems above them.
                verdict.passed.forEach { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelMedium,
                            color = CheckOutcome.PASS.accent(),
                        )
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = PhoneProofTheme.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (savedToReports) {
            Text(
                // Stated rather than offered. The moment a buyer wants a report is after the phone is
                // back in the seller's hand, and a "save this?" prompt gets answered wrongly under
                // pressure — so the app saves it and says so.
                text = "Saved to your reports, so you can show it to the seller or compare it with " +
                    "the next phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
        }

        OutlinedButton(
            onClick = onOpenReports,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Open saved reports")
        }

        Button(
            onClick = onTestAnotherPhone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.surfaceRaised,
                contentColor = PhoneProofTheme.colors.textSecondary,
            ),
        ) {
            Text("Test another phone")
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The verdict itself, sized so it cannot be mistaken for a heading.
 *
 * Colour follows the same four tints as every other outcome in the app, so a buyer who has learned
 * what red means on a check card does not have to learn it again here. Deliberately not animated:
 * this is the one screen where the reader needs to stop and think, and the codebase already bans a
 * looping animation next to anything being read as a measurement.
 */
@Composable
private fun VerdictBanner(verdict: RunVerdict) {
    val accent = when (verdict.grade) {
        RunGrade.WALK_AWAY -> CheckOutcome.FAIL.accent()
        RunGrade.NEGOTIATE -> CheckOutcome.CAUTION.accent()
        RunGrade.INCOMPLETE -> CheckOutcome.UNKNOWN.accent()
        RunGrade.LOOKS_GOOD -> CheckOutcome.PASS.accent()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.fill(accent), RoundedCornerShape(14.dp))
            .border(1.dp, PhoneProofTheme.colors.outline(accent), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
            Text(
                text = "THE VERDICT",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
        Text(
            text = verdict.headline,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = verdict.detail,
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun CountsStrip(verdict: RunVerdict) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CountBox(
            count = verdict.problemCount,
            label = "found",
            tint = CheckOutcome.FAIL.accent(),
            modifier = Modifier.weight(1f),
        )
        CountBox(
            count = verdict.passCount,
            label = "passed",
            tint = CheckOutcome.PASS.accent(),
            modifier = Modifier.weight(1f),
        )
        CountBox(
            count = verdict.unknownCount,
            label = "no answer",
            tint = CheckOutcome.UNKNOWN.accent(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountBox(
    count: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.headlineSmall,
            color = if (count == 0) PhoneProofTheme.colors.textTertiary else tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun NotTestedRow(step: RunStep, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(PhoneProofTheme.colors.background, RoundedCornerShape(14.dp))
                .border(
                    1.dp,
                    PhoneProofTheme.colors.textTertiary.copy(alpha = 0.5f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                // Short, because four of these stack up and the first version repeated "tap to test
                // it — this is one of the ones that matters" down the whole screen, wrapping onto two
                // lines each time. The invitation to tap now lives once, above the list.
                text = if (step.essential) "Decides the verdict" else "Optional",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}
