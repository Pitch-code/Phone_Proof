package com.phoneproof.feature.screentest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.device.ScreenFinding
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The screen pattern test.
 *
 * The pattern itself is drawn edge to edge with no insets and no controls on top of it. That is the
 * entire point of the screen: a dead pixel hiding under a translucent navigation bar, or behind a
 * button, is a dead pixel the buyer pays for. The hint about what to look for is shown *before* each
 * pattern for the same reason.
 */
@Composable
fun ScreenTestScreen(
    state: ScreenTestUiState,
    onStart: () -> Unit,
    onPatternSeen: () -> Unit,
    onStopEarly: () -> Unit,
    onAnswer: (ScreenFinding) -> Unit,
    onRetest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        ScreenTestPhase.INTRO -> Intro(onStart = onStart, total = state.total, modifier = modifier)

        ScreenTestPhase.PATTERN -> PatternLayout(
            state = state,
            onPatternSeen = onPatternSeen,
            onStopEarly = onStopEarly,
            modifier = modifier,
        )

        ScreenTestPhase.QUESTION -> Question(
            viewed = state.viewed,
            total = state.total,
            onAnswer = onAnswer,
            modifier = modifier,
        )

        ScreenTestPhase.FINISHED -> Finished(
            state = state,
            onRetest = onRetest,
            modifier = modifier,
        )
    }
}

@Composable
private fun Intro(
    onStart: () -> Unit,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Dead pixels and burn-in",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = "No app can look at the screen it is drawing on, so this one is down to your " +
                "eyes. The phone will fill the screen with $total plain colours, which is what " +
                "makes a fault obvious — a dead pixel is invisible on a home screen and stands " +
                "out immediately on plain white.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
        Text(
            text = "Wipe the screen first. Dust looks exactly like a dead pixel.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.caution,
        )
        Text(
            text = "Turn the brightness up, hold the phone straight on, and tap anywhere to move " +
                "to the next colour.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.accent,
                contentColor = Color.White,
            ),
        ) {
            Text("Start", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PatternLayout(
    state: ScreenTestUiState,
    onPatternSeen: () -> Unit,
    onStopEarly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pattern = state.current ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            // No inset padding, deliberately. The pattern has to reach the physical edges of the
            // panel, because the edges are exactly where a buyer would otherwise never look.
            .background(pattern.colour)
            .clickable(onClick = onPatternSeen),
    ) {
        // The only thing drawn over the pattern, kept small and pushed into a corner. Anything
        // larger would hide the very pixels the buyer is inspecting.
        val ink = if (pattern.isLight) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.5f)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${pattern.name} · ${state.position} of ${state.total}",
                style = MaterialTheme.typography.labelSmall,
                color = ink,
            )
            // lookFor was written for all six patterns and then never rendered, while its own
            // docstring claimed it was shown. So the pattern screen told the buyer nothing about
            // what each colour reveals — that stuck pixels are invisible on white, that green is the
            // subpixel the eye notices most. It is the most useful line on the screen.
            Text(
                text = pattern.lookFor,
                style = MaterialTheme.typography.labelSmall,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = "Tap for the next colour",
                style = MaterialTheme.typography.labelSmall,
                color = ink,
            )
            // Underlined, because the render showed it looking exactly like the static hint above
            // it — a control nobody can tell is a control. An underline reads as tappable while
            // costing almost no pixels, which a button or a filled chip would not.
            Text(
                text = "I saw something",
                style = MaterialTheme.typography.labelSmall,
                color = ink,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(onClick = onStopEarly)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun Question(
    viewed: Int,
    total: Int,
    onAnswer: (ScreenFinding) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "What did you see?",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = "$viewed of $total patterns viewed.",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(6.dp))

        // Ordered clean-first. Putting a fault at the top invites a tired tap on the worst answer.
        AnswerCard(
            title = "Nothing — every colour looked even",
            detail = "No dots, no patches, no shading.",
            onClick = { onAnswer(ScreenFinding.NOTHING) },
        )
        AnswerCard(
            title = "Small dots or specks",
            detail = "Points that stayed in the same place as the colour changed. Dead or stuck pixels.",
            onClick = { onAnswer(ScreenFinding.SMALL_DOTS) },
        )
        AnswerCard(
            title = "Patches, shadows or ghost images",
            detail = "Larger areas of uneven colour, or the faint outline of a keyboard or " +
                "status bar. This is burn-in.",
            onClick = { onAnswer(ScreenFinding.LARGE_PATCHES) },
        )
    }
}

@Composable
private fun AnswerCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun Finished(
    state: ScreenTestUiState,
    onRetest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        state.result?.let { CheckResultCard(it) }

        OutlinedButton(
            onClick = onRetest,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Test again")
        }
        Text(
            text = "Worth repeating in different light. A fault that hides indoors can be " +
                "obvious in daylight.",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
