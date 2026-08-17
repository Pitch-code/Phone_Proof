package com.phoneproof.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.rememberAnimationsEnabled
import com.phoneproof.core.designsystem.theme.PhoneProofType
import com.phoneproof.core.model.CheckOutcome
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
/**
 * @param emphasise when true, a `FAIL` card breathes slowly so a walk-away finding cannot be
 *   scrolled past. Set false anywhere a measurement is running — a continuously animating surface is
 *   an uncontrolled CPU and screen load, and the battery check measures discharge under a load it
 *   controls.
 */
@Composable
fun CheckResultCard(
    result: CheckResult,
    modifier: Modifier = Modifier,
    emphasise: Boolean = true,
) {
    val accent = result.outcome.accent()
    val category = CheckCategory.forCheckId(result.id)
    val isProblem = result.outcome == CheckOutcome.FAIL

    // A slow breathe, not a flash. W3C WCAG 2.3.1 puts the photosensitive-seizure threshold at three
    // flashes per second and notes people are *more* sensitive to red flashing than any other
    // colour, with a separate stricter test for saturated red. A 1.4 second cycle is 0.7 Hz — an
    // order of magnitude below that line — and it ramps smoothly rather than switching on and off,
    // so it never reads as a flash at all. It still cannot be ignored, which is the point.
    // Someone who has switched animation off system-wide has usually done it for vestibular or
    // photosensitivity reasons, and a FAIL card is exactly the surface where ignoring that would be worst.
    // The border stays at its resting weight instead, which is still heavier than any other outcome.
    val animate = rememberAnimationsEnabled()
    val pulse: Float = if (isProblem && emphasise && animate) {
        val transition = rememberInfiniteTransition(label = "problemPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "problemPulseAlpha",
        ).value
    } else {
        0f
    }

    val borderColour = when (result.outcome) {
        CheckOutcome.FAIL -> accent.copy(alpha = 0.45f + 0.45f * pulse)
        CheckOutcome.PASS -> accent.copy(alpha = 0.38f)
        CheckOutcome.CAUTION -> accent.copy(alpha = 0.34f)
        CheckOutcome.UNKNOWN -> PhoneProofTheme.colors.border
    }
    val borderWidth = if (isProblem) 2.dp else 1.dp
    val fill = if (isProblem) accent.copy(alpha = 0.07f) else PhoneProofTheme.colors.surface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fill, RoundedCornerShape(14.dp))
            .border(borderWidth, borderColour, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A tinted bar keyed to the category, so the eye can find the hardware cards
                // among the software ones without reading every title.
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(category.tint, RoundedCornerShape(2.dp)),
                )
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                )
            }
            OutcomeBadge(result.outcome)
        }

        CategoryChip(category)

        Text(
            text = result.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textPrimary,
        )

        result.consequence?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
        }

        result.action?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                // The outcome colour, except for UNKNOWN.
                //
                // Colouring the action by outcome is right for PASS, CAUTION and FAIL: it ties what to
                // do to how bad it is. UNKNOWN's colour is a deliberately quiet grey — correct for the
                // badge, which must not shout about the absence of a finding — and on a dark card it
                // made the action the least readable line on the screen. That is the one line the
                // buyer has to act on, and "can't tell" results are the ones where they most need
                // telling what to do next.
                color = if (result.outcome == CheckOutcome.UNKNOWN) {
                    PhoneProofTheme.colors.textPrimary
                } else {
                    accent
                },
            )
        }

        if (result.measurements.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                result.measurements.forEach { measurement ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        // Weighted halves with an explicit gap, rather than SpaceBetween. With
                        // SpaceBetween a long value such as a build fingerprint consumed all the
                        // free space and ran straight into its own label with no separation.
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = measurement.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhoneProofTheme.colors.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = measurement.display,
                            style = PhoneProofType.Numeric,
                            color = PhoneProofTheme.colors.textPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
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
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
    }
}
