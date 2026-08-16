package com.phoneproof.feature.vibration

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.vibration.VibrationCheck
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The vibration test, which asks the buyer for nothing except that they hold still.
 *
 * Every other app tests this by asking "did you feel that?". This one watches the accelerometer, so the
 * screen's job is not to collect an opinion — it is to explain that the phone is being *felt* rather than
 * asked, because that is the thing a buyer would never expect and the thing that makes the answer worth more
 * than their own.
 */
@Composable
fun VibrationScreen(
    state: VibrationUiState,
    onStart: () -> Unit,
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
        // The heading is dropped once results are on screen, because a single-check screen and its result
        // card inevitably carry the same name — and "Vibration" above a card titled "Vibration" reads as a
        // rendering mistake. The card has to keep the title, since it appears in saved reports where nothing
        // else supplies the context; so it is the heading that goes.
        if (state.stage != VibrationStage.DONE) {
            Text(
                text = "Vibration",
                style = MaterialTheme.typography.titleLarge,
                color = PhoneProofTheme.colors.textPrimary,
            )
        }

        when (state.stage) {
            VibrationStage.READY -> Ready(state, onStart)
            VibrationStage.RESTING -> Resting(state)
            VibrationStage.BUZZING -> Buzzing(state)
            VibrationStage.DONE -> Done(state, onRestart)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Ready(state: VibrationUiState, onStart: () -> Unit) {
    Text(
        // The sentence that makes this test different from every other app's, said first.
        text = "The app will buzz the motor and watch the accelerometer to see whether the phone " +
            "actually moved. You will not be asked whether you felt it.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Text(
        text = "Asking Android to vibrate always succeeds, even on a phone whose motor is " +
            "disconnected — all it reports is that the request was accepted. So this measures the " +
            "shake instead.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = "Rest the phone on a table if you can. Holding it still works too — a tight grip " +
            "soaks up most of the movement.",
        style = MaterialTheme.typography.titleMedium,
        color = PhoneProofTheme.colors.textPrimary,
    )

    if (!state.hasMotor) {
        Text(
            text = "This phone reports no vibration motor, so there is nothing to measure.",
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.caution,
        )
    } else if (!state.hasAccelerometer) {
        Text(
            text = "There is no accelerometer to feel the phone with, so this cannot be measured. " +
                "Run the sensor test — a dead accelerometer is the more serious finding.",
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.caution,
        )
    }

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneProofTheme.colors.accent,
            contentColor = PhoneProofTheme.colors.textPrimary,
        ),
    ) {
        Text(
            text = if (state.canStart) "Start" else "Run it anyway",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun Resting(state: VibrationUiState) {
    Text(
        text = "Hold still — taking a reading of the phone at rest",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        // Why the buyer is being asked to do nothing for a second and a half. Without this the pause looks
        // like the app has hung.
        text = "This is the baseline the buzz is measured against. If the phone is moving now, the " +
            "app cannot tell a motor from your hand.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    StillnessMeter(state)
}

@Composable
private fun Buzzing(state: VibrationUiState) {
    Text(
        text = "Buzzing now",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.accent,
    )
    Text(
        text = "Keep holding it the same way. The accelerometer is watching.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    StillnessMeter(state)
}

/**
 * Live movement, drawn against the threshold that decides whether the baseline is usable.
 *
 * During the resting phase this is the buyer's cue to keep their hand still; during the buzz it is the motor
 * proving itself in front of them, before any verdict appears. Same number, two jobs — and both of them
 * remove the surprise from whatever the verdict turns out to say.
 */
@Composable
private fun StillnessMeter(state: VibrationUiState) {
    // Scaled so the "too restless" line sits at half width, which leaves room for a buzz to run off the end
    // of the bar rather than pinning silently at the maximum.
    val fraction = (state.liveJerk / (VibrationCheck.TOO_RESTLESS * 2)).coerceIn(0.0, 1.0).toFloat()
    val width by animateFloatAsState(targetValue = fraction, label = "jerk")

    val buzzing = state.stage == VibrationStage.BUZZING
    val colour = when {
        buzzing -> PhoneProofTheme.colors.accent
        state.stillEnough -> PhoneProofTheme.colors.pass
        else -> PhoneProofTheme.colors.caution
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(PhoneProofTheme.colors.gridEmpty, RoundedCornerShape(8.dp))
                .clearAndSetSemantics {},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(16.dp)
                    .background(colour, RoundedCornerShape(8.dp)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (buzzing) {
                    "movement while buzzing"
                } else if (state.stillEnough) {
                    "still enough"
                } else {
                    "too much movement — hold it steadier"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colour,
            )
            Text(
                text = "%.2f m/s²".format(state.liveJerk),
                style = MaterialTheme.typography.labelMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun Done(state: VibrationUiState, onRestart: () -> Unit) {
    state.result?.let { CheckResultCard(it) }

    OutlinedButton(
        onClick = onRestart,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Test again")
    }
}
