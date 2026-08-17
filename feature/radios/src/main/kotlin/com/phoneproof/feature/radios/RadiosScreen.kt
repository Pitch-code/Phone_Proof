package com.phoneproof.feature.radios

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.radios.RadioCheck
import com.phoneproof.checks.radios.RadioKind
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.CheckOutcome

/**
 * The Wi-Fi and Bluetooth test.
 *
 * Two radios on one screen because they are one question to a buyer ("do the radios work?") and because
 * neither takes long enough to deserve its own trip through the menu. Two results are still saved, so the
 * report can say Wi-Fi is proved and Bluetooth is not.
 *
 * Nothing on this screen can be done by the app: it cannot toggle a radio (`setWifiEnabled` has been a no-op
 * since Android 10) and it will not ask for the permissions that would let it scan or pair. So the screen's
 * job is to say precisely what it is watching for, hand the buyer the right settings panel, and notice the
 * moment the radio comes up.
 */
@Composable
fun RadiosScreen(
    state: RadiosUiState,
    onOpenSettings: (RadioKind) -> Unit,
    onAnswerEnableClaim: (RadioKind, Boolean) -> Unit,
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
        Text(
            text = "Wi-Fi and Bluetooth",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )

        when (state.stage) {
            RadiosStage.WATCHING -> Watching(state, onOpenSettings, onAnswerEnableClaim, onFinish)
            RadiosStage.DONE -> Done(state, onRestart)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Watching(
    state: RadiosUiState,
    onOpenSettings: (RadioKind) -> Unit,
    onAnswerEnableClaim: (RadioKind, Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    Text(
        text = "Turn both radios on. Wi-Fi has to join a network — that is what proves it.",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        text = "The app watches while you do it and notices on its own, so come straight back here " +
            "after you flip each switch.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    state.panels.forEach { panel ->
        RadioRow(panel, onOpenSettings, onAnswerEnableClaim)
    }

    Note(title = "WHY THERE IS NO LIST OF NETWORKS", body = RadioCheck.NO_SCAN_NOTE)

    Button(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PhoneProofTheme.colors.accent),
    ) {
        Text(if (state.allProved) "Both radios proved — save" else "Save what we have")
    }
    if (!state.allProved) {
        Text(
            // Said plainly: a shop with no Wi-Fi is a normal reason to stop here, and the buyer should not
            // feel they have failed the test by leaving.
            text = "You can stop at any point. Anything unproven is reported as \"cannot tell\" rather " +
                "than as a fault.",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

/**
 * One radio: what it is doing, and the one thing that would move it forward.
 *
 * The dot and border carry the state so it is readable at arm's length in a shop, and the row only offers a
 * button when there is something useful behind it.
 */
@Composable
private fun RadioRow(
    panel: RadioPanel,
    onOpenSettings: (RadioKind) -> Unit,
    onAnswerEnableClaim: (RadioKind, Boolean) -> Unit,
) {
    val outcome = panel.result.outcome
    val tint = when (outcome) {
        CheckOutcome.PASS -> PhoneProofTheme.colors.pass
        CheckOutcome.CAUTION -> PhoneProofTheme.colors.caution
        CheckOutcome.FAIL -> PhoneProofTheme.colors.fail
        CheckOutcome.UNKNOWN -> PhoneProofTheme.colors.unknown
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, if (outcome == CheckOutcome.PASS) tint else PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(10.dp)
                        .background(tint, CircleShape),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = panel.result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                )
            }
            Text(
                text = liveLabel(panel),
                style = MaterialTheme.typography.titleSmall,
                color = tint,
            )
        }

        Text(
            text = panel.result.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        if (panel.asking) {
            EnableClaim(panel, onAnswerEnableClaim)
        } else if (!panel.observation.enabled && panel.observation.present && panel.canOpenSettings) {
            OutlinedButton(
                onClick = { onOpenSettings(panel.kind) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Open ${panel.result.title} settings")
            }
        } else if (panel.kind == RadioKind.WIFI && panel.observation.enabled && !panel.observation.associated &&
            panel.canOpenSettings
        ) {
            OutlinedButton(
                onClick = { onOpenSettings(panel.kind) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Pick a network")
            }
        }

        if (panel.kind == RadioKind.BLUETOOTH && outcome == CheckOutcome.PASS) {
            Text(
                // Kept next to the green tick, because this is the one pass in the app that covers less than
                // a buyer will assume it does.
                text = RadioCheck.BLUETOOTH_LIMIT_NOTE,
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
    }
}

/**
 * "Did you switch it on?"
 *
 * The only way this app reaches a negative verdict on a radio, so the two answers are given identical weight:
 * one filled button would nudge the buyer toward whichever answer looks like the expected one, and here that
 * would either invent a fault or hide one.
 */
@Composable
private fun EnableClaim(
    panel: RadioPanel,
    onAnswerEnableClaim: (RadioKind, Boolean) -> Unit,
) {
    Text(
        text = "It is still off. Did you switch it on?",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        text = "If you turned it on and it stayed off, that matters. If you did not get round to it, " +
            "say so and nothing will be held against the phone.",
        style = MaterialTheme.typography.bodySmall,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = { onAnswerEnableClaim(panel.kind, true) },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("I turned it on")
        }
        OutlinedButton(
            onClick = { onAnswerEnableClaim(panel.kind, false) },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("I did not try")
        }
    }
}

/** The short status on the right of each row, in the buyer's words rather than the API's. */
private fun liveLabel(panel: RadioPanel): String = when {
    !panel.observation.present -> "not fitted"
    !panel.observation.stateReadable -> "cannot read"
    !panel.observation.enabled -> "off"
    panel.kind == RadioKind.BLUETOOTH -> "on"
    panel.observation.associated && panel.observation.internetWorking -> "connected"
    panel.observation.associated -> "joined"
    else -> "on, not joined"
}

@Composable
private fun Note(title: String, body: String, accent: Color? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, accent ?: PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun Done(state: RadiosUiState, onRestart: () -> Unit) {
    state.results.forEach { CheckResultCard(it) }

    Note(title = "WHY THERE IS NO LIST OF NETWORKS", body = RadioCheck.NO_SCAN_NOTE)

    ResultActions(retestLabel = "Test again", onRetest = onRestart)
}
