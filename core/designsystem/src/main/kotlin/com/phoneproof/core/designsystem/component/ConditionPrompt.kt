package com.phoneproof.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * A small card that appears while something is true and takes itself away when it stops being true.
 *
 * Asked for twice: "you can lift your fingers now", which should vanish when the fingers lift, and "please
 * connect the charger", which should vanish when the cable goes in. Both are the same shape — a message tied
 * to a live condition — so both are this.
 *
 * There is no `dismiss` and no visible close control by design. [visible] is derived from the screen's state,
 * so the prompt cannot be left on screen after the thing it is asking for has happened, and it cannot be
 * dismissed into a state where the buyer has silently lost the instruction. Where a condition might never
 * clear — no charger in the room — the way out belongs in [action], not in a close button.
 *
 * ## Why this is not a Dialog, which matters more than it looks
 *
 * The obvious implementation is `Dialog`, and on the multi-touch screen it would have been a bug.
 *
 * A dialog is a **new window**. When it takes focus the activity's window is told its current gesture has
 * been cancelled, so every finger already on the glass reports as lifted. On the screen that exists purely
 * to count fingers on the glass, that means the prompt would corrupt the measurement it was announcing, and
 * would then instantly close itself because the fingers now "look" lifted — dismissing for the wrong reason
 * and taking the reading with it.
 *
 * So this draws inside the existing window, and **installs no pointer handler anywhere except [action]**.
 * Touches pass straight through it to whatever is underneath, which is what lets it sit over a live touch
 * pad. `ConditionPromptTest` asserts that pass-through, because it is the property that makes the component
 * safe rather than an implementation detail.
 *
 * A real `Dialog` is still right when nothing is being touched — the multi-touch screen's own "did you get
 * all five fingers down?" question is a dialog, and correctly so: it is asked after the fingers have lifted.
 */
@Composable
fun ConditionPrompt(
    visible: Boolean,
    headline: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    /**
     * Bottom by default, so it does not cover the middle of a screen the buyer is working on. Centre it only
     * when the prompt carries everything that matters, since a centred card hides what is behind it.
     */
    alignment: Alignment = Alignment.BottomCenter,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = alignment) {
        AnimatedVisibility(
            visible = visible,
            // A fade in and out, and nothing else. One-shot, so it stays inside the rule against looping
            // animation, and no slide: a card sliding in over a touch pad reads as something to chase.
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .background(PhoneProofTheme.colors.surfaceRaised, RoundedCornerShape(16.dp))
                    .border(1.dp, PhoneProofTheme.colors.borderStrong, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PhoneProofTheme.colors.textPrimary,
                )
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhoneProofTheme.colors.textSecondary,
                    )
                }
                if (action != null && onAction != null) {
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(text = action, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
