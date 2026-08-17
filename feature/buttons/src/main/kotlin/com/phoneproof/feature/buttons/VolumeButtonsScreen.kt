package com.phoneproof.feature.buttons

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.phoneproof.checks.buttons.ButtonObservation
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The volume-button test.
 *
 * Almost all of the design work here is in one sentence on screen: **the volume will not change while this
 * is open.** The app swallows the key presses so a stranger's phone does not get its volume run to maximum
 * in a shop — but a buyer who presses a button and sees no volume slider appear will reasonably conclude the
 * button is broken. Saying so up front is what stops this screen manufacturing the fault it looks for.
 */
@Composable
fun VolumeButtonsScreen(
    state: VolumeButtonsUiState,
    onAnswerPressedBoth: (Boolean) -> Unit,
    onFinish: () -> Unit,
    onRestart: () -> Unit,
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
        Spacer(Modifier.height(10.dp))
        // Dropped once the result card is on screen: a single-check screen and its card carry the same name,
        // and the heading repeated directly above it reads as a rendering mistake. The card keeps the title
        // because saved reports have nothing else to supply the context.
        if (state.stage != VolumeStage.DONE) {
            ScreenTitle("Volume buttons")
        }

        if (state.stage == VolumeStage.DONE) {
            state.result?.let { CheckResultCard(it) }
            ResultActions(retestLabel = "Test again", onRetest = onRestart)
        } else {
            Waiting(state = state, onFinish = onFinish)
        }

        Spacer(Modifier.height(24.dp))
    }

    if (state.stage == VolumeStage.ASKING) {
        PressedQuestion(missing = state.missing, onAnswer = onAnswerPressedBoth)
    }
}

@Composable
private fun Waiting(state: VolumeButtonsUiState, onFinish: () -> Unit) {
    Text(
        text = "Press the volume up button, then the volume down button.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Text(
        // The most important line on the screen. Without it, an app that deliberately swallows the key
        // presses looks exactly like a phone with two dead buttons.
        text = "The volume will not change while this screen is open — the app takes the presses " +
            "instead, so a stranger's phone does not end up at full volume. Watch the ticks below.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )

    Spacer(Modifier.height(4.dp))

    KeyRow(label = "Volume up", observation = state.up)
    KeyRow(label = "Volume down", observation = state.down)

    if (state.anyKeyHeldNow) {
        Text(
            // Live, while it is happening. A buyer whose finger is not on the phone reads this as the app
            // telling them something is jammed, which is exactly what it is telling them.
            text = "A key is being held down. If your finger is off the phone, that key is stuck.",
            style = MaterialTheme.typography.titleSmall,
            color = PhoneProofTheme.colors.caution,
        )
    }

    Spacer(Modifier.height(4.dp))

    OutlinedButton(
        onClick = onFinish,
        enabled = state.anythingHeard,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(if (state.anythingHeard) "See the result" else "Waiting for a button press")
    }

    if (!state.anythingHeard) {
        Text(
            text = "Nothing has reached the app yet. If neither button registers, the app will say " +
                "so rather than blame the phone — it may be this app that is not receiving them.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

/**
 * One button's state, as a tick and a count.
 *
 * The count matters as much as the tick. A worn button that registers one press in three is a real fault and
 * would tick green on the first success, so the number is what lets a buyer see it misbehaving.
 */
@Composable
private fun KeyRow(label: String, observation: ButtonObservation) {
    val satisfied = observation.everPressed
    val accent = when {
        observation.stillDown -> PhoneProofTheme.colors.caution
        satisfied -> PhoneProofTheme.colors.pass
        else -> PhoneProofTheme.colors.border
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, accent, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(PhoneProofTheme.colors.background, RoundedCornerShape(13.dp))
                .border(1.dp, accent, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    observation.stillDown -> "!"
                    satisfied -> "✓"
                    else -> "·"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (satisfied || observation.stillDown) {
                    accent
                } else {
                    PhoneProofTheme.colors.textTertiary
                },
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (satisfied) {
                    PhoneProofTheme.colors.textPrimary
                } else {
                    PhoneProofTheme.colors.textTertiary
                },
            )
            Text(
                text = when {
                    observation.stillDown -> "held down now"
                    observation.presses == 0 -> "not pressed yet"
                    observation.presses == 1 -> "1 press, released"
                    else -> "${observation.presses} presses, released"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
    }
}

/**
 * The question, asked only when one button worked and the other did not.
 *
 * That asymmetry is the whole reason it is worth asking: the working button proves the app receives volume
 * keys, so the buyer's answer is the last thing needed to turn a silence into a finding. Both answers weigh
 * the same, as everywhere else — the reassuring one is the one that costs money.
 */
@Composable
private fun PressedQuestion(missing: VolumeKey?, onAnswer: (Boolean) -> Unit) {
    val name = when (missing) {
        VolumeKey.UP -> "volume up"
        VolumeKey.DOWN -> "volume down"
        null -> "other"
    }

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
                text = "Did you press the $name button?",
                style = MaterialTheme.typography.headlineSmall,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = "The other button reached the app, so the app is definitely receiving volume " +
                    "keys. That makes this one worth asking about — but it is easy to press the same " +
                    "button twice by mistake.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onAnswer(false) },
                    modifier = Modifier.fillMaxWidth(0.5f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Not yet", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { onAnswer(true) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("I pressed it", style = MaterialTheme.typography.titleMedium)
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
