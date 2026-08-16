package com.phoneproof.feature.cameratest

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * Cameras first, then the torch.
 *
 * No viewfinder, and that is deliberate rather than unfinished. A preview would be reassuring to look at
 * and would make the measurement depend on a composable being laid out, visible and correctly sized —
 * three more ways for it to quietly measure nothing. It also invites the buyer to judge the picture, which
 * is not what this screen can honestly help with: it establishes that the sensor is imaging, and says so.
 */
@Composable
fun CameraTestScreen(
    state: CameraTestUiState,
    onTestCameras: () -> Unit,
    onLightTorch: () -> Unit,
    onAnswerLit: (Boolean) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Each camera is opened and a few frames are read from it. That shows whether the " +
                "sensor is alive and producing a live picture — not whether the photos are any good, " +
                "which needs your eyes and the phone's own camera app.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        if (state.cameras.isEmpty()) {
            Text(
                text = "This phone reports no cameras at all, which is unusual enough to be worth " +
                    "checking in the phone's own camera app before drawing any conclusion.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.caution,
            )
        } else if (state.stage == CameraStage.READY) {
            Text(
                text = "Found ${state.cameras.size}: " +
                    state.cameras.joinToString { it.facing.label.lowercase() } + ".",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
            Text(
                text = "Keep your fingers clear of both lenses and point the phone at something with " +
                    "detail in it — not a blank wall.",
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textPrimary,
            )
        }

        state.testing?.let {
            Text(
                text = "Testing the ${it.lowercase()}…",
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        state.results.forEach { CheckResultCard(it) }
        state.torch?.let { CheckResultCard(it) }

        when (state.stage) {
            CameraStage.READY -> if (state.cameras.isNotEmpty()) {
                Primary(text = "Test the cameras", onClick = onTestCameras)
            }

            CameraStage.CAMERAS_DONE -> if (state.hasFlash) {
                Primary(text = "Now test the flashlight", onClick = onLightTorch, pulse = true)
            } else {
                Primary(text = "Check the flashlight", onClick = onLightTorch)
            }

            CameraStage.TORCH_LIT -> {
                Text(
                    text = "Look at the back of the phone. Did the flash light up?",
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                // Both answers identical, as on the speaker question. The app has no stake in which is
                // true, and drawing "Yes" as the primary action would nudge toward the reassuring answer —
                // the one that costs the buyer money.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { onAnswerLit(false) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("No", style = MaterialTheme.typography.titleMedium) }
                    OutlinedButton(
                        onClick = { onAnswerLit(true) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Yes", style = MaterialTheme.typography.titleMedium) }
                }
            }

            CameraStage.FINISHED -> OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Test again") }

            CameraStage.TESTING -> Unit
        }
    }
}

@Composable
private fun Primary(text: String, onClick: () -> Unit, pulse: Boolean = false) {
    // A pulse, and this is a deliberate departure from what was asked for.
    //
    // The request was for the button to flash in different colours so it catches the eye. WCAG 2.3.1 puts
    // the photosensitive-seizure threshold at three flashes per second, Play's pre-launch report flags
    // flashing content, and this button sits on a screen a stranger may be handed — so a flash here is the
    // wrong tool at any speed. A slow fade between two colours draws the eye just as well and never goes
    // dark, at the same 0.7 Hz already used for the trial counter on Home, which is the only other looping
    // animation in this codebase.
    val phase by rememberInfiniteTransition(label = "torchPulse").animateFloat(
        initialValue = 0f,
        targetValue = if (pulse) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "torchPulseValue",
    )

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            // Towards the caution amber rather than towards white: the torch is the one step on this
            // screen the buyer has to look away from the phone for, and amber reads as "attention" without
            // implying something has gone wrong.
            containerColor = lerp(
                PhoneProofTheme.colors.accent,
                PhoneProofTheme.colors.caution,
                phase * 0.55f,
            ),
            contentColor = Color.White,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
