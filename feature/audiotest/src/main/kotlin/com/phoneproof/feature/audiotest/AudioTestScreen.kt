package com.phoneproof.feature.audiotest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * Microphone and speaker, in that order, and the order is not arbitrary.
 *
 * The speaker test hears the tone **through the microphone**, so a broken microphone makes a working
 * speaker undetectable. Testing the microphone first means that by the time the speaker is judged, one of
 * the two possible explanations for silence has already been ruled out — and if the microphone failed,
 * the app knows not to blame the speaker for it.
 */
@Composable
fun AudioTestScreen(
    state: AudioTestUiState,
    onStartMicrophone: () -> Unit,
    onStartSpeaker: () -> Unit,
    onAnswerHeard: (Boolean) -> Unit,
    onDeclineToAnswer: () -> Unit,
    onPlayBack: () -> Unit,
    onRestart: () -> Unit,
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
        // No heading here: AudioTestRoute owns it, because it has to sit above the permission gate as
        // well as above this content, and two titles on one screen was the first thing the render showed.
        Text(
            text = "The microphone is tested first, because the speaker is checked by listening for a " +
                "tone through it. A blocked microphone would make a working speaker look dead.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        if (state.captureFailed) {
            Text(
                text = "The phone would not hand over any audio. Something else may be using the " +
                    "microphone, or it is muted in the phone's own privacy settings. That is not a " +
                    "fault in this handset.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.caution,
            )
        }

        Waveform(levels = state.levels, live = state.isBusy)

        when (state.stage) {
            AudioStage.READY -> Instruction(
                text = "Hold the phone as you would for a call, and say something out loud for three " +
                    "seconds.",
            )

            AudioStage.LISTENING -> Instruction(text = "Listening — keep talking.")

            AudioStage.PLAYING_TONE -> Instruction(
                text = "Playing a tone. Keep your hand off the speaker grille and stay quiet.",
            )

            AudioStage.ASKING, AudioStage.MICROPHONE_DONE, AudioStage.FINISHED -> Unit
        }

        state.microphone?.let { CheckResultCard(it) }
        state.speaker?.let { CheckResultCard(it) }

        // The volume warning sits above the speaker button, not after the result. A buyer who is about to
        // test a speaker at zero volume needs to know before the test, not once it has failed.
        if (state.stage == AudioStage.MICROPHONE_DONE && (state.volume.isMuted || state.volume.isTooLow)) {
            Text(
                text = if (state.volume.isMuted) {
                    "Media volume is muted. Turn it up or the speaker test cannot mean anything."
                } else {
                    "Media volume is low — about ${(state.volume.fraction * 100).toInt()}%. Turn it up " +
                        "before testing the speaker."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.caution,
            )
        }

        when (state.stage) {
            AudioStage.READY -> Primary(text = "Test the microphone", onClick = onStartMicrophone)

            AudioStage.MICROPHONE_DONE -> {
                Primary(text = "Now test the speaker", onClick = onStartSpeaker)
                // Offered before the speaker test, not after: this is the moment the buyer has just
                // spoken and can still remember what they said. A level meter and a verdict prove the
                // microphone works; hearing their own voice back is the only way to judge whether it
                // sounds muffled, distant or crackly — which is what they will live with on calls.
                if (state.canPlayBack) {
                    OutlinedButton(
                        onClick = onPlayBack,
                        enabled = !state.isPlayingBack,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = if (state.isPlayingBack) {
                                "Playing your recording…"
                            } else {
                                "Play back what you said"
                            },
                        )
                    }
                    Text(
                        // Says it out loud on the screen where it matters, not only in the permission
                        // dialog the buyer has already tapped through.
                        text = "Played from memory. Nothing was saved to this phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = PhoneProofTheme.colors.textTertiary,
                    )
                }
            }

            // The question itself is a dialog now — see below. This is what is left behind it.
            AudioStage.ASKING -> Instruction(text = "Waiting for your answer about the tone.")

            AudioStage.FINISHED -> OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Test again") }

            AudioStage.LISTENING, AudioStage.PLAYING_TONE -> Unit
        }

        if (state.stage == AudioStage.FINISHED) {
            Text(
                text = "Worth repeating somewhere quieter if either result was inconclusive. Noise is " +
                    "the usual reason a good speaker cannot be measured.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
        }
    }

    if (state.stage == AudioStage.ASKING) {
        ToneQuestionDialog(
            onAnswerHeard = onAnswerHeard,
            onDecline = onDeclineToAnswer,
        )
    }
}

/**
 * The one question in this app that has to interrupt.
 *
 * It used to be the last thing in a scrolling column, below two result cards — and on a real phone it was
 * off the bottom of the screen. A buyer had to think to scroll for it, which meant most of them never
 * answered, and the screen sat there looking as though the test had finished inconclusively.
 *
 * A dialog because this genuinely blocks: nothing else on the screen means anything until it is answered.
 * Dismissible, though, and with an explicit third option — the app is asking for a favour, not demanding
 * one, and a question with no way out is how you get people tapping whichever button makes it go away.
 */
@Composable
private fun ToneQuestionDialog(
    onAnswerHeard: (Boolean) -> Unit,
    onDecline: () -> Unit,
) {
    Dialog(onDismissRequest = onDecline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(16.dp))
                .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Did you hear the tone?",
                style = MaterialTheme.typography.headlineSmall,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = "The app played a 1 kHz tone and could not pick it out of the recording. That " +
                    "happens in a noisy room and is not by itself a fault, so this one is your ear's " +
                    "to decide.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
            // Both answers drawn identically, and this took a render to get right.
            //
            // The first version made "Yes" a filled accent button and "No" an outline, which is the
            // ordinary Compose convention for a primary action and a secondary one — and it is exactly
            // wrong here. It puts the app's thumb on the scale in favour of the reassuring answer, and
            // the reassuring answer is the one that costs the buyer money. There is no primary action on
            // this question: the app has no stake in which is true, and the buttons have to say so.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onAnswerHeard(false) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("No", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { onAnswerHeard(true) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Yes", style = MaterialTheme.typography.titleMedium)
                }
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "I would rather not say",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun Instruction(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = PhoneProofTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Primary(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneProofTheme.colors.accent,
            contentColor = Color.White,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The recording drawn as bars, one per 20 ms frame.
 *
 * This is the part of the screen a buyer will actually trust. A verdict card is the app's opinion; a
 * waveform that jumps when they speak is something they can verify with their own eyes, and if the app
 * ever gets the verdict wrong this is the evidence that contradicts it.
 *
 * Drawn from the same `frameLevels` the verdict is computed from, deliberately — not a second, prettier
 * measurement taken alongside it. If the picture and the verdict could disagree, one of them would be
 * lying and there would be no way to tell which.
 */
@Composable
private fun Waveform(levels: List<Float>, live: Boolean) {
    val ink = PhoneProofTheme.colors.accent
    val idle = PhoneProofTheme.colors.gridEmpty

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        val midline = size.height / 2f

        if (levels.isEmpty()) {
            // A flat line rather than an empty box, so the space reads as an instrument waiting for a
            // signal instead of a component that failed to load.
            drawRect(
                color = idle,
                topLeft = Offset(0f, midline - 1f),
                size = Size(size.width, 2f),
            )
            return@Canvas
        }

        val barWidth = size.width / levels.size
        levels.forEachIndexed { index, level ->
            // Square-rooted, because levels are amplitudes and quiet speech would otherwise be a barely
            // visible smear at the bottom of the chart. The verdict uses the raw numbers; this is only
            // how they are drawn.
            val scaled = kotlin.math.sqrt(level.coerceIn(0f, 1f))
            val half = (scaled * midline).coerceAtLeast(1f)
            drawRect(
                color = if (live) ink else ink.copy(alpha = 0.75f),
                topLeft = Offset(index * barWidth, midline - half),
                size = Size((barWidth - 1f).coerceAtLeast(1f), half * 2f),
            )
        }
    }
}
