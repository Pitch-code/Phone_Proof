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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.OutcomeBadge
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.component.accent
import com.phoneproof.core.designsystem.component.glyph
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.nounFor
import com.phoneproof.core.model.plural
import com.phoneproof.core.run.RunCondition
import com.phoneproof.core.run.RunPlan
import com.phoneproof.core.run.RunState
import com.phoneproof.core.run.RunStep
import com.phoneproof.core.run.RunStepStatus
import com.phoneproof.core.run.StepEffort

/**
 * The guided run: one screen that knows what has been tested and what has not.
 *
 * Home offered eight checks in a list and no opinion about which to do first, which left the buyer
 * designing their own inspection while a seller watched them. This screen holds the order and the
 * progress so they only have to keep tapping.
 *
 * Stateless, so the screenshot tests can render a half-finished run — which is the state that matters
 * and the one that is hardest to reach by hand.
 */
@Composable
fun RunChecklistScreen(
    state: RunState,
    onStart: () -> Unit,
    onOpenStep: (RunStep) -> Unit,
    onSkip: (RunStep) -> Unit,
    onSeeVerdict: () -> Unit,
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

        if (!state.active) {
            BeforeYouStart(onStart = onStart)
        } else {
            RunInProgress(
                state = state,
                onOpenStep = onOpenStep,
                onSkip = onSkip,
                onSeeVerdict = onSeeVerdict,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The intro, which exists to be read once.
 *
 * Every condition listed here is derived from the plan rather than written out by hand, so a test
 * added later that needs quiet cannot end up with its requirement announced nowhere. It is also the
 * only honest place to mention them: a screen that discovers halfway through a measurement that the
 * room was too loud has already produced a confident wrong answer.
 */
@Composable
private fun BeforeYouStart(onStart: () -> Unit) {
    Text(
        text = "Test this phone",
        style = MaterialTheme.typography.displaySmall,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        text = "${RunPlan.steps.size} tests, in the order that makes sense, about " +
            "${RunPlan.typicalMinutes} minutes. Skip anything you like — the verdict at the end " +
            "says what you skipped.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )

    Spacer(Modifier.height(4.dp))

    val conditions = RunCondition.entries.filter { condition ->
        RunPlan.steps.any { condition in it.needs }
    }
    if (conditions.isNotEmpty()) {
        SectionLabel("Before you start")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            conditions.forEach { condition -> AdviceLine(adviceFor(condition)) }
            if (RunPlan.steps.any { it.effort == StepEffort.ASK_THE_SELLER }) {
                AdviceLine(
                    "The last few steps need the seller: what the advert claimed, and the IMEI " +
                        "read off the phone.",
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    Button(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneProofTheme.colors.accent,
            contentColor = PhoneProofTheme.colors.onAccent,
        ),
    ) {
        Text(text = "Start", style = MaterialTheme.typography.titleLarge)
    }

    val essentials = RunPlan.steps.count { it.essential }
    Text(
        // The buyer with a seller getting impatient needs to know there is a shorter version, and
        // needs to know it before they start rather than discovering it by giving up halfway.
        text = "In a hurry? The $essentials tests that decide the verdict take about " +
            "${RunPlan.essentialMinutes} minutes.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RunInProgress(
    state: RunState,
    onOpenStep: (RunStep) -> Unit,
    onSkip: (RunStep) -> Unit,
    onSeeVerdict: () -> Unit,
) {
    val next = state.nextStep

    Text(
        text = "Testing this phone",
        style = MaterialTheme.typography.headlineSmall,
        color = PhoneProofTheme.colors.textPrimary,
    )

    ProgressHeader(state)

    if (next != null) {
        CurrentStepCard(
            step = next,
            position = state.steps.indexOf(next) + 1,
            total = state.steps.size,
            onOpen = { onOpenStep(next) },
            onSkip = { onSkip(next) },
        )
    }

    // Offered from the first completed step onwards rather than only at the end. An inspection gets
    // cut short by the seller, not by the buyer finishing the list, and a verdict on four tests is
    // worth more than being trapped on a checklist.
    if (state.settledCount > 0) {
        Button(
            onClick = onSeeVerdict,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (next == null) 64.dp else 52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = if (next == null) {
                ButtonDefaults.buttonColors(
                    containerColor = PhoneProofTheme.colors.accent,
                    contentColor = PhoneProofTheme.colors.onAccent,
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = PhoneProofTheme.colors.surfaceRaised,
                    contentColor = PhoneProofTheme.colors.textSecondary,
                )
            },
        ) {
            Text(
                text = if (next == null) "See the verdict" else "Stop here and see the verdict",
                style = if (next == null) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleSmall
                },
            )
        }
    }

    SectionLabel("The whole run")
    state.steps.forEachIndexed { index, step ->
        StepRow(
            step = step,
            position = index + 1,
            status = state.statusOf(step.id),
            worstOutcome = state.worstOutcomeOf(step.id),
            isNext = step.id == next?.id,
            onClick = { onOpenStep(step) },
        )
    }
}

@Composable
private fun ProgressHeader(state: RunState) {
    val minutesLeft = (state.remainingSeconds + 59) / 60
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(PhoneProofTheme.colors.gridEmpty, RoundedCornerShape(4.dp))
                // One description for the pair, so a screen reader says "5 of 8 tests done" instead
                // of reading out a bar with no value and then a line of text.
                .clearAndSetSemantics {},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progress)
                    .height(8.dp)
                    .background(PhoneProofTheme.colors.accent, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = buildString {
                append("${state.settledCount} of ${state.steps.size} done")
                if (state.skippedCount > 0) {
                    append(" · ${state.skippedCount} skipped")
                }
                if (minutesLeft > 0) {
                    append(" · about $minutesLeft ${nounFor(minutesLeft, "minute")} left")
                }
            },
            style = MaterialTheme.typography.titleSmall,
            color = PhoneProofTheme.colors.textSecondary,
            modifier = Modifier.semantics {
                contentDescription = "${state.settledCount} of ${state.steps.size} tests done"
            },
        )
    }
}

/**
 * The one thing to do next, given its own card.
 *
 * The whole point of the run is that the buyer never has to decide what to do next, so exactly one
 * thing on this screen is allowed to look like a button worth pressing.
 */
@Composable
private fun CurrentStepCard(
    step: RunStep,
    position: Int,
    total: Int,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(14.dp))
            .border(1.dp, PhoneProofTheme.colors.accent, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "STEP $position OF $total",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.accent,
        )
        ScreenTitle(step.title)
        Text(
            text = step.why,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(labelFor(step.effort))
            Tag("about ${secondsLabel(step.typicalSeconds)}")
        }

        step.needs.forEach { condition -> AdviceLine(adviceFor(condition)) }

        Button(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.accent,
                contentColor = PhoneProofTheme.colors.onAccent,
            ),
        ) {
            Text(
                text = when (step.effort) {
                    StepEffort.AUTOMATIC -> "Run it"
                    StepEffort.HANDS_ON -> "Start this test"
                    StepEffort.ASK_THE_SELLER -> "Open it"
                    StepEffort.LOOK_YOURSELF -> "Show me what to look for"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(
                // Named rather than a bare "Skip", so the buyer knows the cost before they tap. The
                // verdict does hold it against the phone's score — it withholds a clean verdict.
                text = "Skip this — the verdict will say I did",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun StepRow(
    step: RunStep,
    position: Int,
    status: RunStepStatus,
    worstOutcome: CheckOutcome?,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (isNext) {
                    PhoneProofTheme.colors.accent.copy(alpha = 0.5f)
                } else {
                    PhoneProofTheme.colors.border
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusMarker(position = position, status = status, worstOutcome = worstOutcome)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (status == RunStepStatus.PENDING) {
                    PhoneProofTheme.colors.textPrimary
                } else {
                    PhoneProofTheme.colors.textSecondary
                },
            )
            Text(
                text = when (status) {
                    RunStepStatus.SKIPPED -> "Skipped — tap to do it after all"
                    RunStepStatus.DONE -> doneSubtitle(worstOutcome)
                    RunStepStatus.PENDING -> step.why
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }

        if (worstOutcome != null) {
            OutcomeBadge(worstOutcome)
        }
    }
}

/** What a finished step says about itself in one line, so the badge is never the only signal. */
private fun doneSubtitle(worstOutcome: CheckOutcome?): String = when (worstOutcome) {
    CheckOutcome.FAIL -> "Found a fault — see the verdict"
    CheckOutcome.CAUTION -> "Found something worth asking about"
    CheckOutcome.UNKNOWN -> "Could not be measured on this phone"
    CheckOutcome.PASS -> "Nothing wrong found"
    // The walkthrough, which measures nothing. Deliberately not "Done" and certainly not "Passed":
    // the app showed the buyer where to look and has no idea what they saw.
    null -> "Walkthrough shown — only your own eyes can judge those"
}

@Composable
private fun StatusMarker(position: Int, status: RunStepStatus, worstOutcome: CheckOutcome?) {
    // The glyph follows the finding, not merely the fact that the step ran. The first render of this
    // screen put a tick against every completed step, which produced a red tick beside a badge reading
    // PROBLEM and an amber tick beside CHECK AGAIN — the marker congratulating the buyer for having
    // discovered a fault.
    val (label, tint) = when {
        status == RunStepStatus.SKIPPED -> "–" to PhoneProofTheme.colors.textTertiary
        status == RunStepStatus.DONE && worstOutcome == null ->
            "✓" to PhoneProofTheme.colors.pass
        status == RunStepStatus.DONE -> worstOutcome!!.glyph() to worstOutcome.accent()
        else -> "$position" to PhoneProofTheme.colors.textTertiary
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(PhoneProofTheme.colors.background, RoundedCornerShape(14.dp))
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun AdviceLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .background(PhoneProofTheme.colors.textTertiary, RoundedCornerShape(2.dp)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
        modifier = Modifier
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(6.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

private fun labelFor(effort: StepEffort): String = when (effort) {
    StepEffort.AUTOMATIC -> "Automatic"
    StepEffort.HANDS_ON -> "In your hands"
    StepEffort.ASK_THE_SELLER -> "Ask the seller"
    StepEffort.LOOK_YOURSELF -> "Look yourself"
}

private fun secondsLabel(seconds: Int): String = if (seconds < 60) {
    plural(seconds, "second")
} else {
    plural((seconds + 30) / 60, "minute")
}

internal fun adviceFor(condition: RunCondition): String = when (condition) {
    RunCondition.QUIET ->
        "Find somewhere quiet. The speaker test measures a tone through the microphone, and a " +
            "noisy shop drowns it."
    RunCondition.DIM_LIGHT ->
        "Shade the screen with your hand for the colour pages. Dead pixels hide under a showroom " +
            "light."
    // Deliberately does not name the touch test, though that is where it was first seen. This line is
    // shown against whichever step declares the condition, and it appeared under "Dead pixels and
    // burn-in" explaining a problem with a different test.
    RunCondition.NO_INTERRUPTIONS ->
        "Turn on Do Not Disturb. A notification banner covers part of the screen and takes the taps " +
            "underneath it, which looks exactly like a fault in the phone."
}
