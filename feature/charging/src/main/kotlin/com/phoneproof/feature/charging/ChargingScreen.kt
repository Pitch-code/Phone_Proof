package com.phoneproof.feature.charging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.device.ChargingCheck
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ConditionPrompt
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The charging test, which the app cannot start on its own.
 *
 * Everything else in this app runs when the buyer taps a button. This one needs a charger plugged into someone
 * else's phone, which may not be in the room — so waiting is a real state with a real screen, and giving up is
 * a button rather than a silent timeout. A buyer with no cable to hand deserves an honest "not tested" rather
 * than a countdown they cannot satisfy.
 */
@Composable
fun ChargingScreen(
    state: ChargingUiState,
    onGiveUp: () -> Unit,
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
        if (state.stage != ChargingStage.DONE) {
            Text(
                text = "Charging",
                style = MaterialTheme.typography.titleLarge,
                color = PhoneProofTheme.colors.textPrimary,
            )
        }

        when (state.stage) {
            ChargingStage.WAITING -> Waiting(state, onGiveUp)
            ChargingStage.MEASURING -> Measuring(state)
            ChargingStage.DONE -> Done(state, onRestart)
        }

        Spacer(Modifier.height(24.dp))
    }

    // Centred, unusually, and it carries the escape route with it.
    //
    // This is the one test the app cannot start, so the screen can sit in WAITING indefinitely — and on a
    // real phone the instruction was easy to miss. Centring it hides the paragraph behind it, so the reason
    // to bother is repeated here in one line, and "there is no charger here" is inside the card: a prompt
    // that might never clear on its own must never be the only thing on screen without a way past it.
    ConditionPrompt(
        visible = state.stage == ChargingStage.WAITING && !state.plugged,
        headline = "Please connect the charger",
        // Short, because the paragraph behind explains why the test matters and this only has to say what
        // happens next. Skipping is mentioned here rather than left as a separate line, so the buyer can
        // see the consequence of the button before pressing it.
        detail = "The test starts on its own the moment you do, and this closes by itself. If you have " +
            "no cable, skipping is fine — the report will say charging was not tested rather than " +
            "pretending otherwise.",
        alignment = Alignment.Center,
        action = "There is no charger here",
        onAction = onGiveUp,
    )
}

@Composable
private fun Waiting(state: ChargingUiState, onGiveUp: () -> Unit) {
    Text(
        text = "Plug a charger in. The test starts on its own the moment you do.",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        // The reason to bother, and it is not the speed. Said here because a buyer who thinks this is a
        // benchmark will skip it, and this is the one check they cannot repeat after paying.
        text = "The point of this is not really the speed. A loose charging socket is one of the " +
            "commonest faults on a used phone: it charges fine while you watch it and gives up " +
            "overnight. The app watches for twenty seconds and counts the times it lets go.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    LiveState(state)

    // The way out used to live here, and it now lives in the prompt instead.
    //
    // While this screen is waiting, the prompt is always showing — WAITING means nothing is plugged in by
    // definition — so a button here would be a second, identical "there is no charger here" sitting
    // underneath the card. Touches pass through the prompt, so it would have been a live button the buyer
    // could not see. One of the two had to go, and the visible one is the one worth keeping.
}

@Composable
private fun Measuring(state: ChargingUiState) {
    Text(
        text = "Charging — watching for ${state.secondsLeft} more seconds",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.accent,
    )
    Text(
        // The one instruction that decides whether the result means anything: moving the phone pulls the
        // cable and looks exactly like a loose socket.
        text = "Leave the phone and the cable alone. Moving either one looks identical to a loose " +
            "socket, and this is counting how many times charging stops.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    LiveState(state)

    if (state.dropouts > 0) {
        Text(
            text = "Charging has already stopped and restarted ${state.dropouts} time" +
                if (state.dropouts == 1) "" else "s",
            style = MaterialTheme.typography.titleSmall,
            color = PhoneProofTheme.colors.caution,
        )
    }
}

/**
 * What the phone is doing right now.
 *
 * Live numbers rather than a spinner, because they are the evidence. A buyer watching the wattage settle sees
 * the measurement being taken, and a buyer watching "plugged in" flicker has found the fault before the
 * verdict is written.
 */
@Composable
private fun LiveState(state: ChargingUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (state.plugged) PhoneProofTheme.colors.pass else PhoneProofTheme.colors.border,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LiveRow(
            label = "Charger",
            value = if (state.plugged) "connected" else "not connected",
            highlight = state.plugged,
        )
        LiveRow(label = "Battery", value = "${state.percent}%")
        state.watts?.let {
            LiveRow(label = "Power now", value = "%.1f W".format(it))
        }
    }
}

@Composable
private fun LiveRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlight) {
                PhoneProofTheme.colors.pass
            } else {
                PhoneProofTheme.colors.textPrimary
            },
        )
    }
}

@Composable
private fun Done(state: ChargingUiState, onRestart: () -> Unit) {
    state.result?.let { CheckResultCard(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "ABOUT THE SPEED",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Text(
            // Beside the verdict, because a buyer comparing this against the wattage on the box is about to
            // blame the phone for the charger in their hand.
            text = ChargingCheck.SPEED_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
    }

    OutlinedButton(
        onClick = onRestart,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Test again")
    }
}
