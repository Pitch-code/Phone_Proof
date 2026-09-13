package com.phoneproof.feature.touchgrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.touch.TouchWhileChargingCheck
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.nounFor

/**
 * Watches for a screen that only taps itself while charging.
 *
 * The mirror image of the ghost-touch screen: that one says unplug, this one insists on a charger before it
 * will start, because the fault it hunts appears only under charge. The app cannot make a charger exist, so
 * "plug one in" is a first-class waiting state with its own screen rather than a disabled button with no
 * explanation.
 */
@Composable
fun TouchWhileChargingScreen(
    state: ChargeTouchUiState,
    onStart: () -> Unit,
    onContact: (xFraction: Float, yFraction: Float) -> Unit,
    onStop: () -> Unit,
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
        if (state.stage != ChargeTouchStage.DONE) {
            ScreenTitle("Touches while charging")
        }

        when (state.stage) {
            ChargeTouchStage.NEEDS_CHARGER -> WaitingForCharger()
            ChargeTouchStage.READY -> Ready(onStart = onStart)
            ChargeTouchStage.WATCHING -> Watching(state = state, onContact = onContact, onStop = onStop)
            ChargeTouchStage.DONE -> Finished(state = state, onRestart = onRestart)
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ColumnScope.WaitingForCharger() {
    Text(
        text = "Some phones tap themselves only while charging — from a swollen battery, a poorly bonded " +
            "screen, or a cheap charger. This watches for that, and it needs a charger connected to work.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )

    Spacer(Modifier.weight(1f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Plug in a charger to begin",
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            // Announced, so a blind user is not left waiting at a screen whose next move depends on a cable
            // they cannot see the state of.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.Ready(onStart: () -> Unit) {
    Text(
        text = "Charger connected. Lay the phone flat, take your hands off it, and leave it alone while it " +
            "watches — with the charger left plugged in the whole time.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Text(
        text = "If it finds touches, the first thing to try is a different charger and cable — that is the " +
            "commonest cause, and the cheapest to rule out.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )

    Spacer(Modifier.weight(1f))

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneProofTheme.colors.accent,
            contentColor = PhoneProofTheme.colors.onAccent,
        ),
    ) {
        Text("Start watching", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ColumnScope.Watching(
    state: ChargeTouchUiState,
    onContact: (Float, Float) -> Unit,
    onStop: () -> Unit,
) {
    val caught = state.contactCount > 0
    val surfaceColour = if (caught) PhoneProofTheme.colors.caution else PhoneProofTheme.colors.accent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, surfaceColour.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = if (caught) {
                    "Watching while charging. ${state.contactCount} " +
                        "${nounFor(state.contactCount, "touch", "touches")} nobody made so far."
                } else {
                    "Watching while charging. No touches so far. Keep your hands off the phone and leave " +
                        "the charger connected."
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width <= 0f || height <= 0f) continue
                        event.changes.filter { it.pressed }.forEach { change ->
                            onContact(change.position.x / width, change.position.y / height)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${state.remainingSeconds}s",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = surfaceColour,
            )
            Text(
                text = if (caught) {
                    "${state.contactCount} ${nounFor(state.contactCount, "touch", "touches")} nobody made"
                } else {
                    "Charging — hands off, nothing so far"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (caught) PhoneProofTheme.colors.caution else PhoneProofTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    Text(
        text = "Keep the charger plugged in and do not touch the screen. It watches for " +
            "${TouchWhileChargingCheck.FULL_WATCH_MILLIS / 1000}s. If the cable comes out, the watch is " +
            "void and has to be run again.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )

    OutlinedButton(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = if (state.watchedLongEnough) "Stop and see the result" else "Stop watching",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun Finished(state: ChargeTouchUiState, onRestart: () -> Unit) {
    state.result?.let { CheckResultCard(it) }
    ResultActions(retestLabel = "Watch again", onRetest = onRestart)
}
