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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.device.ChargingCheck
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ConditionPrompt
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.component.ScreenTitle
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
            ScreenTitle(stringResource(R.string.charging_title))
        }

        when (state.stage) {
            ChargingStage.WAITING -> Waiting(state, onGiveUp)
            ChargingStage.MEASURING -> Measuring(state)
            ChargingStage.DONE -> Done(state, onRestart)
        }

        Spacer(Modifier.height(24.dp))
    }

    // The instruction, made unmissable, and nothing else.
    //
    // No action of its own: the give-up button lives in the page above, always visible, so this card does
    // not need to duplicate it and cannot hide it. Bottom-aligned for the same reason — the page's own
    // controls stay where the buyer can see them.
    //
    // This is the one test the app cannot start on its own, so the screen can sit here indefinitely, and on
    // a real phone the instruction was easy to miss even though it is the headline.
    ConditionPrompt(
        visible = state.stage == ChargingStage.WAITING && !state.plugged,
        headline = stringResource(R.string.charging_prompt_headline),
        detail = stringResource(R.string.charging_prompt_detail),
    )
}

@Composable
private fun Waiting(state: ChargingUiState, onGiveUp: () -> Unit) {
    Text(
        text = stringResource(R.string.charging_waiting_headline),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        // The reason to bother, and it is not the speed. Said here because a buyer who thinks this is a
        // benchmark will skip it, and this is the one check they cannot repeat after paying.
        text = stringResource(R.string.charging_waiting_why),
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    LiveState(state)

    // The way out stays here, in the page, and the prompt below carries no action of its own.
    //
    // It was briefly moved into the prompt to avoid two identical buttons. That was wrong: the prompt is
    // hidden while a charger is attached, and WAITING with a charger attached is a real state — the moment
    // between the sample arriving and the test starting. Moving the button left that instant with no visible
    // exit at all. One button that is always present beats one that is usually present.
    OutlinedButton(
        onClick = onGiveUp,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(stringResource(R.string.charging_waiting_give_up))
    }
    Text(
        text = stringResource(R.string.charging_waiting_skip_note),
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
    )
}

@Composable
private fun Measuring(state: ChargingUiState) {
    Text(
        text = stringResource(R.string.charging_measuring_headline, state.secondsLeft),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.accent,
    )
    Text(
        // The one instruction that decides whether the result means anything: moving the phone pulls the
        // cable and looks exactly like a loose socket.
        text = stringResource(R.string.charging_measuring_hold_still),
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    LiveState(state)

    if (state.dropouts > 0) {
        Text(
            // pluralStringResource, not an if: English needs two forms and puts the boundary at one,
            // and several of the languages this app is aimed at do neither.
            text = pluralStringResource(
                R.plurals.charging_dropouts_live,
                state.dropouts,
                state.dropouts,
            ),
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
            label = stringResource(R.string.charging_live_charger),
            value = if (state.plugged) {
                stringResource(R.string.charging_live_connected)
            } else {
                stringResource(R.string.charging_live_not_connected)
            },
            highlight = state.plugged,
        )
        LiveRow(
            label = stringResource(R.string.charging_live_battery),
            value = stringResource(R.string.charging_live_battery_value, state.percent),
        )
        state.watts?.let {
            LiveRow(
                label = stringResource(R.string.charging_live_power),
                // The number is formatted first and passed as text, so its decimal separator follows the
                // phone's locale rather than being baked in as a full stop.
                value = stringResource(R.string.charging_live_power_value, "%.1f".format(it)),
            )
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
            text = stringResource(R.string.charging_about_speed_label),
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

    ResultActions(retestLabel = stringResource(R.string.charging_retest), onRetest = onRestart)
}
