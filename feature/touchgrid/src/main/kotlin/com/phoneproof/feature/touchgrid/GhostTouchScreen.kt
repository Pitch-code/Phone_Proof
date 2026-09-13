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
import androidx.compose.ui.window.Dialog
import com.phoneproof.checks.touch.GhostTouchCheck
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ResultActions
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.nounFor

/**
 * Watches an untouched screen for taps nobody made.
 *
 * This is the one check that asks the buyer to do *nothing* — the finding is whatever the screen reports
 * while their hands are off it. So the screen's whole job is to make "leave it alone" unmistakable, count
 * any phantom touches as they arrive so the buyer sees the fault happen rather than being told about it,
 * and then ask the single question that separates a broken digitiser from a resting thumb.
 */
@Composable
fun GhostTouchScreen(
    state: GhostTouchUiState,
    onStart: () -> Unit,
    onContact: (xFraction: Float, yFraction: Float) -> Unit,
    onStop: () -> Unit,
    onAnswerHandsOff: (Boolean) -> Unit,
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
        // Dropped once the result card is up, for the same reason as the other single-check screens: the
        // card carries the title, and a heading repeated directly above it reads as a rendering fault.
        if (state.stage != GhostTouchStage.DONE) {
            ScreenTitle("Touches nobody made")
        }

        when (state.stage) {
            GhostTouchStage.READY -> Ready(onStart = onStart)
            GhostTouchStage.WATCHING, GhostTouchStage.ASKING ->
                Watching(state = state, onContact = onContact, onStop = onStop)
            GhostTouchStage.DONE -> Finished(state = state, onRestart = onRestart)
        }

        Spacer(Modifier.height(20.dp))
    }

    if (state.stage == GhostTouchStage.ASKING) {
        HandsOffQuestion(count = state.contactCount, onAnswer = onAnswerHandsOff)
    }
}

@Composable
private fun ColumnScope.Ready(onStart: () -> Unit) {
    Text(
        text = "A faulty screen taps itself — it opens apps, types into fields and answers calls in a " +
            "pocket. It comes from a cracked or replaced screen, or a swollen battery pressing on the " +
            "panel from behind.",
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Text(
        text = "Lay the phone flat on a table, take your hands off it, and leave it alone while it " +
            "watches. The fault comes and goes, so the longer you can leave it, the more the result is " +
            "worth.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )
    Text(
        // Named first because it is the commonest false alarm, and the one the buyer can rule out before
        // starting rather than have explained away afterwards.
        text = "If a charger is plugged in, unplug it first. A cheap charger can make a healthy screen " +
            "report touches that are not there.",
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
    state: GhostTouchUiState,
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
                    "Watching the screen. ${state.contactCount} " +
                        "${nounFor(state.contactCount, "touch", "touches")} nobody made so far."
                } else {
                    "Watching the screen. No touches so far. Keep your hands off the phone."
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width <= 0f || height <= 0f) continue
                        // Every pressed pointer on every event: a ghost touch may be a single down with no
                        // movement, which Compose still reports as a change. The check clusters a flurry
                        // into one event, so reporting generously here is safe.
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
                    "Hands off — nothing so far"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (caught) {
                    PhoneProofTheme.colors.caution
                } else {
                    PhoneProofTheme.colors.textTertiary
                },
                textAlign = TextAlign.Center,
                // Announced only when it changes, and politely, so a blind user hears a phantom touch land
                // without the countdown chattering over them every second.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    Text(
        text = "Do not touch the screen. It watches for ${GhostTouchCheck.FULL_WATCH_MILLIS / 1000}s, or " +
            "stop early if you have to hand the phone back.",
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textTertiary,
    )

    OutlinedButton(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            // Wording changes with whether stopping now yields anything: below the useful minimum the app
            // will only be able to say "not watched long enough", so it must not pretend otherwise.
            text = if (state.watchedLongEnough) "Stop and see the result" else "Stop watching",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun Finished(state: GhostTouchUiState, onRestart: () -> Unit) {
    state.result?.let { CheckResultCard(it) }
    ResultActions(retestLabel = "Watch again", onRetest = onRestart)
}

/**
 * The one question that decides the verdict.
 *
 * A screen that types by itself is a dealbreaker; a thumb resting on the edge is not — and they produce
 * identical evidence. So when something is found, the honest thing is to ask, and to weight a "yes, hands
 * off" as a confident fault and an "I'm not sure" as a caution. Both buttons are the same size: the app is
 * not steering the answer.
 */
@Composable
private fun HandsOffQuestion(count: Int, onAnswer: (Boolean) -> Unit) {
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
                text = "Were your hands off the phone the whole time?",
                style = MaterialTheme.typography.headlineSmall,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = "The screen registered $count ${nounFor(count, "touch", "touches")} nobody made. " +
                    "If nothing was touching it, that is a real fault worth acting on. If a finger or " +
                    "thumb was resting on the edge, that would explain it and the phone may be fine.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            OutlinedButton(
                onClick = { onAnswer(true) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Nothing was touching it", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = { onAnswer(false) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("A finger might have been", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
