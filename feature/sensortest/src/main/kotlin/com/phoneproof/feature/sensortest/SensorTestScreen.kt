package com.phoneproof.feature.sensortest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.sensors.SensorKind
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.plural

/**
 * The live sensor test: two gestures, then five verdicts.
 *
 * The meters are the point of the screen, not decoration. Before them, the commonest outcome of a
 * liveness test is "could not tell" — the buyer waggles the phone politely, never crosses the
 * threshold, and gets nothing for their trouble. A bar that visibly fills turns the gesture into
 * something with a finish line, and because the bar and the verdict read the same threshold from
 * [com.phoneproof.checks.sensors.SensorGesture], a full bar can never be followed by a shrug.
 */
@Composable
fun SensorTestScreen(
    state: SensorTestUiState,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While a gesture is running the screen holds one instruction and two meters and nothing else, so
    // it is centred and does not scroll: the buyer is tilting the phone and watching the middle of it,
    // and the first render had all of that huddled at the top above two thirds of empty black.
    val duringGesture = state.phase == SensorPhase.MOTION || state.phase == SensorPhase.COVER

    // Remembered unconditionally. Creating it inside the branch would mean calling a composable in
    // some compositions and not others, which is not allowed and would crash on the phase change.
    val scrollState = rememberScrollState()

    // Insets, padding and the title all live here rather than in the route, so that what the screenshot
    // tests render is what the buyer sees. The first version left padding to the route and the rendered
    // meters ran off the right-hand edge with their labels clipped — a layout bug that existed only in
    // the review, which is the worst place for one to hide.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Centred when it fits, scrolling when it does not, rather than one or the other.
        //
        // Simply centring the gesture screens would have been enough at the default font size and would
        // have clipped the meters off the bottom at a 200% accessibility scale, with no scroll to
        // recover them. A minimum height of one viewport gets the centring for free while leaving the
        // column scrollable, so a buyer with large text still reaches everything.
        val viewport = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (duringGesture) viewport else 0.dp),
                verticalArrangement = if (duringGesture) {
                    Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                } else {
                    Arrangement.spacedBy(12.dp)
                },
            ) {
                if (!duringGesture) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Sensors",
                        style = MaterialTheme.typography.titleLarge,
                        color = PhoneProofTheme.colors.textPrimary,
                    )
                }

                when (state.phase) {
                    SensorPhase.READY -> Ready(state, onStart)
                    SensorPhase.MOTION -> Motion(state)
                    SensorPhase.COVER -> Cover(state)
                    SensorPhase.DONE -> Done(state, onRestart)
                }

                if (!duringGesture) Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun Ready(state: SensorTestUiState, onStart: () -> Unit) {
    Text(
        text = "This one watches the sensors while you move the phone, rather than asking the " +
            "phone whether it has them. Every sensor on a water-damaged handset is still on the " +
            "parts list.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )

    Card {
        Text(
            text = "TWO THINGS TO DO",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Step("1", "Tilt the phone right over and turn it back, like turning a doorknob.")
        Step("2", "Hold your palm flat over the top of the screen, then take it away.")
        Text(
            // Said before the test rather than after: this is the one honest way to avoid a screen full
            // of "could not tell" that reads as the app not working.
            text = "If you skip a gesture the app says so and reports nothing about those sensors. " +
                "It will not call a working sensor broken because nobody moved the phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }

    if (state.available.isEmpty()) {
        Text(
            text = "This phone reports no sensors at all, so there is nothing here to measure.",
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.caution,
        )
    } else {
        Text(
            text = "${plural(state.available.size, "sensor")} to test: " +
                state.available.sortedBy { it.ordinal }.joinToString { plainName(it) } + ".",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )
        PrimaryButton(text = "Start", onClick = onStart)
    }
}

@Composable
private fun Motion(state: SensorTestUiState) {
    Prompt(
        instruction = "Tilt the phone right over, then turn it back",
        detail = "Keep hold of it. Big, slow movements — the meters below show what is registering.",
        secondsLeft = state.secondsLeft,
        complete = state.gestureComplete,
    )

    Meter(label = "Tilting", progress = state.tiltProgress)
    if (state.hasGyroscope) {
        Meter(label = "Turning", progress = state.turnProgress)
    } else {
        // Not silently omitted: a buyer staring at one meter where the screen described two would
        // reasonably think something had gone wrong.
        Text(
            text = "No turning meter — this phone has no gyroscope, which the instant scan already " +
                "reported.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun Cover(state: SensorTestUiState) {
    Prompt(
        instruction = "Now cover the top of the screen with your palm",
        detail = "Lay your hand flat over the earpiece, hold it there, then take it away.",
        secondsLeft = state.secondsLeft,
        complete = state.gestureComplete,
    )

    if (state.hasProximity) {
        Indicator(label = "Proximity sensor felt your hand", satisfied = state.proximityFelt)
    }
    if (state.hasLight) {
        Indicator(label = "Light sensor reacted to your hand", satisfied = state.lightReacted)
    }
}

@Composable
private fun Done(state: SensorTestUiState, onRestart: () -> Unit) {
    val unknowns = state.results.count { it.outcome == CheckOutcome.UNKNOWN }
    if (unknowns > 0) {
        Card(accent = PhoneProofTheme.colors.unknown) {
            Text(
                text = "${plural(unknowns, "sensor")} could not be judged",
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                // Offered before the buyer reads five cards and concludes the app is broken. The retry
                // is the fix, and it takes fifteen seconds.
                text = "The gesture those need was not completed. Running it again and making the " +
                    "movement bigger is usually all it takes.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
        }
    }

    state.results.forEach { CheckResultCard(it) }

    ResultActions(retestLabel = "Test again", onRetest = onRestart)
}

// ---------------------------------------------------------------------- pieces

@Composable
private fun Prompt(
    instruction: String,
    detail: String,
    secondsLeft: Int,
    complete: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = instruction,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.textSecondary,
        )
        Text(
            text = if (complete) {
                "Got it — that is enough"
            } else {
                "$secondsLeft ${if (secondsLeft == 1) "second" else "seconds"} left"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (complete) {
                PhoneProofTheme.colors.pass
            } else {
                PhoneProofTheme.colors.accent
            },
        )
    }
}

@Composable
private fun Meter(label: String, progress: Float) {
    // Animated so a 50 Hz stream of samples does not make the bar jitter. Not a looping animation —
    // it tracks a value the buyer is producing, which is the opposite of decoration.
    val width by animateFloatAsState(targetValue = progress, label = "meter-$label")
    val full = progress >= 1f

    Column(
        modifier = Modifier.semantics {
            contentDescription = "$label ${(progress * 100).toInt()} percent"
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = PhoneProofTheme.colors.textSecondary,
            )
            Text(
                text = if (full) "enough" else "keep going",
                style = MaterialTheme.typography.labelMedium,
                color = if (full) {
                    PhoneProofTheme.colors.pass
                } else {
                    PhoneProofTheme.colors.textTertiary
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(PhoneProofTheme.colors.gridEmpty, RoundedCornerShape(7.dp))
                .clearAndSetSemantics {},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(width.coerceIn(0f, 1f))
                    .height(14.dp)
                    .background(
                        if (full) PhoneProofTheme.colors.pass else PhoneProofTheme.colors.accent,
                        RoundedCornerShape(7.dp),
                    ),
            )
        }
    }
}

@Composable
private fun Indicator(label: String, satisfied: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (satisfied) PhoneProofTheme.colors.pass else PhoneProofTheme.colors.border,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(PhoneProofTheme.colors.background, RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (satisfied) {
                        PhoneProofTheme.colors.pass
                    } else {
                        PhoneProofTheme.colors.textTertiary.copy(alpha = 0.5f)
                    },
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (satisfied) "✓" else "·",
                style = MaterialTheme.typography.labelMedium,
                color = if (satisfied) {
                    PhoneProofTheme.colors.pass
                } else {
                    PhoneProofTheme.colors.textTertiary
                },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (satisfied) {
                PhoneProofTheme.colors.textPrimary
            } else {
                PhoneProofTheme.colors.textTertiary
            },
        )
    }
}

@Composable
private fun Card(
    accent: Color = PhoneProofTheme.colors.border,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, accent, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.accent,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneProofTheme.colors.accent,
            contentColor = PhoneProofTheme.colors.onAccent,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

private fun plainName(kind: SensorKind): String = when (kind) {
    SensorKind.ACCELEROMETER -> "accelerometer"
    SensorKind.GYROSCOPE -> "gyroscope"
    SensorKind.MAGNETOMETER -> "compass"
    SensorKind.PROXIMITY -> "proximity"
    SensorKind.LIGHT -> "light"
}
