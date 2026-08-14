package com.phoneproof.feature.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.MANUAL_CHECKS_TITLE
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The checks the app cannot do.
 *
 * A list of collapsed cards rather than a wizard. A wizard would force the buyer through eight steps
 * in a fixed order while a seller waits, and most of them will already know how to look at a
 * charging port. Collapsed cards let someone jump to the two they have not thought of.
 */
@Composable
fun GuideScreen(
    steps: List<GuideStep>,
    expandedId: String?,
    /**
     * False when the system says animations are off.
     *
     * Respected rather than overridden. Someone who has turned animations off has usually done so
     * for motion sensitivity or on a slow phone, and eight looping diagrams is exactly the content
     * that setting exists to suppress. The diagrams still draw, held at a frame chosen to be
     * legible on its own.
     */
    animate: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = MANUAL_CHECKS_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "No app can test any of these. They need your hands, your eyes and a " +
                    "torch — and they are where the expensive faults hide.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(steps, key = { it.id }) { step ->
                StepCard(
                    step = step,
                    number = steps.indexOf(step) + 1,
                    expanded = step.id == expandedId,
                    animate = animate,
                    onToggle = { onToggle(step.id) },
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    step: GuideStep,
    number: Int,
    expanded: Boolean,
    animate: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (expanded) {
                    PhoneProofTheme.colors.accent.copy(alpha = 0.45f)
                } else {
                    PhoneProofTheme.colors.border
                },
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 10.dp),
            )
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
                    text = step.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
            Text(
                text = if (expanded) "−" else "+",
                style = MaterialTheme.typography.titleLarge,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Diagram(diagram = step.diagram, animate = animate)

                Label("Why it matters")
                Body(step.whyItMatters)

                Label("How to do it")
                step.howTo.forEachIndexed { index, line ->
                    Row {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhoneProofTheme.colors.textTertiary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhoneProofTheme.colors.textSecondary,
                        )
                    }
                }

                Label("Good sign")
                Text(
                    text = step.goodSign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.pass,
                )

                Label("Bad sign")
                Text(
                    text = step.badSign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.caution,
                )
            }
        }
    }
}

@Composable
private fun Diagram(diagram: GuideDiagram, animate: Boolean) {
    // The animation runs only while its card is open, which is what keeps eight looping diagrams
    // from all moving at once behind text nobody is reading. It also means the motion is tied to a
    // deliberate tap rather than starting on its own.
    val progress = if (animate) {
        val transition = rememberInfiniteTransition(label = "diagram")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                // Slow on purpose: this is a demonstration of a physical movement, and a fast loop
                // reads as a flicker rather than an instruction.
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "progress",
        ).value
    } else {
        // A quarter through, not zero. Several diagrams are at rest at 0f — the twist is untwisted,
        // the tray is closed — so a still frame there would show nothing at all.
        0.25f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        GuideDiagramCanvas(
            diagram = diagram,
            progress = progress,
            ink = PhoneProofTheme.colors.textSecondary,
            accent = PhoneProofTheme.colors.accent,
            warn = PhoneProofTheme.colors.caution,
            // The same token as the Box behind this canvas, so an occluding tray matches what it is
            // covering. If these two ever disagree the SIM tray becomes a visible block.
            surface = PhoneProofTheme.colors.surfaceRaised,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )
}
