package com.phoneproof.feature.touchgrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.nounFor

/**
 * Counts fingers on the glass, live.
 *
 * The live count is the whole design. A buyer told "put five fingers down" and shown nothing has no way to
 * know whether the fourth one registered — and this test's entire finding is whether it did. Watching the
 * number stick at three while a fourth finger is pressed against the screen is the buyer discovering the
 * fault themselves, before the app has said a word about it. The verdict afterwards only confirms what they
 * already saw.
 */
@Composable
fun MultiTouchScreen(
    state: MultiTouchUiState,
    onPointers: (List<Offset>) -> Unit,
    onFinish: () -> Unit,
    onAnswerFingersDown: (Boolean) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Fingers at once",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )

        if (state.stage == MultiTouchStage.DONE) {
            Finished(state = state, onRestart = onRestart)
        } else {
            Counting(state = state, onPointers = onPointers, onFinish = onFinish)
        }

        Spacer(Modifier.height(20.dp))
    }

    if (state.stage == MultiTouchStage.ASKING) {
        FingersQuestion(
            target = state.target,
            best = state.best,
            onAnswer = onAnswerFingersDown,
        )
    }
}

// A ColumnScope receiver, so the pad can take Modifier.weight(1f) and fill whatever is left after the
// heading and the counter. Without it the pad would need a fixed height and would be wrong on every screen
// size but the one it was measured on.
@Composable
private fun ColumnScope.Counting(
    state: MultiTouchUiState,
    onPointers: (List<Offset>) -> Unit,
    onFinish: () -> Unit,
) {
    Text(
        text = "Put ${state.target} fingers on the pad below, one at a time, and watch the count.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Text(
        // The reason the count matters, said before the test rather than after. A buyer who understands
        // what they are watching for does not need the verdict explained to them.
        text = "If the number stops going up while you are still adding fingers, that is the fault " +
            "this test is looking for.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )

    TouchPad(
        state = state,
        onPointers = onPointers,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "${state.best} of ${state.target}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (state.reachedTarget) {
                    PhoneProofTheme.colors.pass
                } else {
                    PhoneProofTheme.colors.textPrimary
                },
            )
            Text(
                text = if (state.reachedTarget) {
                    "That is everything this phone claims"
                } else {
                    "best so far · ${state.current} down now"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
        Button(
            onClick = onFinish,
            enabled = state.stage == MultiTouchStage.COUNTING,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.accent,
                contentColor = PhoneProofTheme.colors.textPrimary,
            ),
        ) {
            Text("See the result", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * The pad, and the numbered rings under each finger.
 *
 * Rings rather than filled dots, so a finger does not hide the thing that proves it registered. The number
 * inside each one is what turns "the screen felt something" into "the screen is following this finger
 * separately from the others" — which is the actual question.
 */
@Composable
private fun TouchPad(
    state: MultiTouchUiState,
    onPointers: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColour = if (state.reachedTarget) {
        PhoneProofTheme.colors.pass
    } else {
        PhoneProofTheme.colors.accent
    }

    Box(
        modifier = modifier
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = "Touch pad. ${state.best} " +
                    "${nounFor(state.best, "finger")} tracked at once so far."
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Every pressed pointer, reported on every event. Compose delivers a change per
                        // pointer including the ones that did not move, so filtering on `pressed` is what
                        // separates fingers on the glass from fingers that have just lifted.
                        onPointers(
                            event.changes.filter { it.pressed }.map { it.position },
                        )
                    }
                }
            }
            .drawBehind {
                state.positions.forEachIndexed { index, position ->
                    drawCircle(
                        color = ringColour,
                        radius = RING_RADIUS_PX + index * RING_STEP_PX,
                        center = position,
                        style = Stroke(width = 6f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (state.positions.isEmpty()) {
            Text(
                text = if (state.best == 0) {
                    "Touch here"
                } else {
                    "Lift off and try again, or see the result"
                },
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "${state.current}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = ringColour.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun Finished(state: MultiTouchUiState, onRestart: () -> Unit) {
    state.result?.let { CheckResultCard(it) }

    OutlinedButton(
        onClick = onRestart,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Test again")
    }
}

/**
 * The question, when the count fell short.
 *
 * Unavoidable, and unlike the sensor test there is no second instrument to ask instead. Three points
 * measured is indistinguishable from three fingers used, so the only witness available is the person whose
 * hand it was. Both answers carry the same weight for the same reason as everywhere else in this app: the
 * reassuring answer is the one that costs the buyer money.
 */
@Composable
private fun FingersQuestion(
    target: Int,
    best: Int,
    onAnswer: (Boolean) -> Unit,
) {
    Dialog(onDismissRequest = { onAnswer(false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(16.dp))
                .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Did you get all $target fingers on the glass?",
                style = MaterialTheme.typography.headlineSmall,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = "The screen followed $best at once. If all $target were really down, that is " +
                    "worth knowing about. If you only managed $best, this proves nothing and the app " +
                    "will say so.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onAnswer(false) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("I used fewer", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("All $target", style = MaterialTheme.typography.titleMedium)
                }
            }
            TextButton(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "I am not sure",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
        }
    }
}

/** Rings grow with each extra finger, so overlapping ones stay tellable apart. */
private const val RING_RADIUS_PX = 46f
private const val RING_STEP_PX = 9f
