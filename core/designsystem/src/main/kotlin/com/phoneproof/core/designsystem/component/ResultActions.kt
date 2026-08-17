package com.phoneproof.core.designsystem.component

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.rememberAnimationsEnabled

/**
 * The pair of buttons that ends every test: go back, or run it again.
 *
 * ## Why Back is here at all
 *
 * Reported from a real phone. Every result screen offered "Test again" and nothing else, so the only
 * way out was the system back gesture — which on a phone being held by someone else, in a shop, is not
 * obvious and is easy to get wrong. A test that is easy to enter and unclear to leave feels like a trap.
 *
 * ## Why it does not take an onBack callback
 *
 * It uses the activity's own back dispatcher rather than a lambda threaded down from the navigation
 * graph. That is deliberate: it makes it **impossible** for this button and the system back gesture to
 * disagree, because they are the same call. Threading a callback through a dozen routes would have given
 * a dozen opportunities for one of them to pop the wrong destination, and "going back is not working
 * properly sometimes" is already a bug this project has had once.
 *
 * [onBack] is still available for a screen that genuinely needs to intercept — abandoning a half-finished
 * test, say — and for tests that want to assert the tap without an activity.
 *
 * ## Why the retest button pulses, and how that is allowed
 *
 * `design-system.md` forbids looping animation, because a measurement cannot be taken beside an
 * animating surface. It also states what a new exception must provide, and this provides all three:
 *
 *  - **A rate below the flash threshold.** 0.7 Hz — a 1.4 second smooth ramp on `RepeatMode.Reverse`.
 *    WCAG 2.3.1 puts the photosensitive-seizure threshold at three flashes per second, so this is an
 *    order of magnitude below it, and it breathes rather than blinking. It fades the container only,
 *    never the label, so the text never loses contrast.
 *  - **It cannot run during a measurement.** Not by a flag that someone has to remember, but
 *    structurally: this component is only ever shown on a finished result, after measuring has stopped.
 *    [pulseUntilTapped] exists for any caller that manages to break that assumption.
 *  - **It stops when it has done its job.** The pulse is there to be noticed; once tapped it never
 *    animates again, so it cannot become permanent decoration.
 *
 * It also honours the system "remove animations" setting, as every looping animation in this app now does.
 */
@Composable
fun ResultActions(
    retestLabel: String,
    onRetest: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    pulseUntilTapped: Boolean = true,
) {
    val dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    var tapped by remember { mutableStateOf(false) }

    val animate = rememberAnimationsEnabled()
    val pulsing = pulseUntilTapped && !tapped && animate
    val phase = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "retestPulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "retestPulseValue",
        )
        value
    } else {
        // Rest state is the plain accent, so a phone with animation switched off shows an ordinary
        // button rather than one frozen mid-fade.
        0f
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = {
                onBack?.invoke()
                    ?: dispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
            },
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(text = backLabel, style = MaterialTheme.typography.titleSmall)
        }

        Button(
            onClick = {
                tapped = true
                onRetest()
            },
            // Slightly wider than Back: it is the action the screen is inviting, and Back is the way out.
            modifier = Modifier.weight(1.3f).height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = lerp(
                    PhoneProofTheme.colors.accent,
                    PhoneProofTheme.colors.surfaceRaised,
                    // Kept shallow. Enough movement to catch an eye crossing the screen, not enough to
                    // read as a fault or to compete with a FAIL card breathing above it.
                    phase * 0.45f,
                ),
                // onAccent, not textPrimary: the latter fails WCAG AA on this blue in both themes.
                contentColor = PhoneProofTheme.colors.onAccent,
            ),
        ) {
            Text(text = retestLabel, style = MaterialTheme.typography.titleSmall)
        }
    }
}
