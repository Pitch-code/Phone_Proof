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
import androidx.compose.ui.text.font.FontWeight
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
        // Secondary rather than tertiary ink. These are the operating instructions for the test —
        // the reader cannot do it correctly without them — and they were the palest text on the page.
        Text(
            text = "Turn the brightness up, hold the phone straight on, and tap anywhere to move " +
                "to the next colour.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
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
        // One ink, at full strength. There used to be a second at 45% for the lines judged less
        // important, on the reasoning that a faint wash hides fewer pixels — but 45% of the panel
        // colour on the panel colour is close to invisible, and it was reported as unreadable. The
        // saving was a few hundred pixels at the bottom of the screen; the cost was the only control
        // on it. Keeping the text small and cornered is what protects the pixels being inspected,
        // not making it faint.
        val ink = if (pattern.isLight) {
            Color.Black.copy(alpha = 0.82f)
        } else {
            Color.White.copy(alpha = 0.88f)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // titleMedium, up from labelSmall, and in full-strength ink rather than the 45% wash the
            // rest of the hint uses. This is read at arm's length while the phone is tilted to catch
            // the light, which is the worst possible reading condition in the app.
            Text(
                text = "${pattern.name} · ${state.position} of ${state.total}",
                style = MaterialTheme.typography.titleMedium,
                color = ink,
            )
            // lookFor was written for all six patterns and then never rendered, while its own
            // docstring claimed it was shown. So the pattern screen told the buyer nothing about
            // what each colour reveals — that stuck pixels are invisible on white, that green is the
            // subpixel the eye notices most. It is the most useful line on the screen.
            Text(
                text = pattern.lookFor,
                // bodyMedium, up from labelSmall. This is the most useful sentence on the screen —
                // it is what tells the buyer that stuck pixels are invisible on white.
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            // bodyMedium in the stronger ink, up from labelSmall at 45%. Reported as unreadable, and
            // it was the faintest text in the app: the smallest style at under half opacity, on a
            // screen deliberately viewed at arm's length while the phone is tilted to catch light.
            Text(
                text = "Tap for the next colour",
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
            // Underlined, because the render showed it looking exactly like the static hint above
            // it — a control nobody can tell is a control. An underline reads as tappable while
            // costing almost no pixels, which a button or a filled chip would not.
            // titleMedium and bold in full-strength ink. This is the only control on the screen and
            // it was set in the same faint 45% labelSmall as the hint above it — the one thing a
            // buyer who has just spotted a dead pixel needs to find, drawn as the least visible
            // thing on the panel.
            Text(
                text = "I saw something",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
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
        // Secondary, not tertiary. This line is what distinguishes the three answers from each
        // other — "points that stayed in the same place" versus "larger areas of uneven colour" —
        // so it is doing the actual work of the question, in the faintest ink available.
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
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
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
