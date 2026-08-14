package com.phoneproof.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.MANUAL_CHECKS_TITLE
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * Home.
 *
 * One dominant action, because the person holding this phone has about three minutes and an audience.
 *
 * **This screen scrolls, and it has to.** It was a fixed [Column] with a `Spacer(weight(1f))` pushing
 * the last two rows to the bottom, which worked with three checks and broke silently at six: the
 * spacer collapsed to nothing, "Saved reports" was clipped to a sliver with its label cut off, and
 * Settings was pushed off the screen entirely. Nothing failed and no test noticed, because a fixed
 * column simply draws what fits. Anything added below now extends the scroll instead of evicting
 * whatever was last.
 */
@Composable
fun HomeScreen(
    checks: List<HomeCheck>,
    onStartFullTest: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    /**
     * Scans left on the free trial, or null when they are unlimited.
     *
     * Shown under the button rather than inside it: the label stays "Test this phone" so the primary
     * action never turns into a counter, and a paid user sees no counter at all rather than a
     * reminder of a limit that does not apply to them.
     */
    freeScansLeft: Int? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            // safeDrawing, not statusBars: it also covers the navigation bar, gesture areas and
            // display cutouts, so this holds on a notch phone and a 3-button phone alike.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "PhoneProof",
            style = MaterialTheme.typography.displaySmall,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = "Find the faults before you pay for them.",
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = onStartFullTest,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.accent,
                contentColor = PhoneProofTheme.colors.textPrimary,
            ),
        ) {
            Text(text = "Test this phone", style = MaterialTheme.typography.titleLarge)
        }

        if (freeScansLeft != null) {
            // Pulsed rather than flashed, and this is a deliberate departure from what was asked for.
            //
            // A continuous flash was requested so the line gets noticed. WCAG 2.3.1 puts the
            // photosensitive-seizure threshold at three flashes per second, and this text sits on the
            // first screen of the app where it would run for as long as the app is open — so a blink
            // here is the worst place in the product to put one. A slow fade between two colours
            // draws the eye without ever going dark, at the same 0.7 Hz already used for a FAIL card,
            // which is the only other looping animation in this codebase.
            val pulse by rememberInfiniteTransition(label = "trialPulse").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "trialPulseValue",
            )
            val restColour = if (freeScansLeft == 0) {
                PhoneProofTheme.colors.caution
            } else {
                PhoneProofTheme.colors.accent
            }
            Text(
                text = when (freeScansLeft) {
                    0 -> "Free trial used up — see the plans in Settings"
                    1 -> "1 scan left on the free trial"
                    else -> "$freeScansLeft scans left on the free trial"
                },
                // titleSmall, up from labelSmall. This was the line reported as hard to read.
                style = MaterialTheme.typography.titleSmall,
                color = lerp(restColour, PhoneProofTheme.colors.textPrimary, pulse),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Text(
            // Wording is deliberate and must not be strengthened. The app shows ads, so an
            // advertising ID does leave the device; claiming "nothing leaves this device" would be
            // false, and overclaiming privacy is worse than not claiming it.
            text = "Your test results stay on this device",
            // bodyMedium, up from labelSmall. Left static: a privacy statement that blinks reads as
            // an advertisement for itself, and only one thing on a screen can be the thing moving.
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        SectionLabel("Test one thing at a time")
        checks.forEach { check ->
            NavigationRow(
                title = check.title,
                subtitle = check.subtitle,
                onClick = check.onClick,
            )
        }

        // Its own heading, because it is the opposite of everything above it: advice for the buyer's
        // hands rather than a measurement the phone can make. Grouping it with the checks implied the
        // app was testing something.
        SectionLabel("What the app cannot test")
        NavigationRow(
            title = MANUAL_CHECKS_TITLE,
            // The old subtitle opened "Eight things to look at yourself", which repeated both the
            // count in the title and the point already made by the heading above. It names the
            // faults instead: three concrete things beats a restatement.
            subtitle = "A twisted frame, a re-glued screen, the water sticker — with diagrams",
            onClick = onOpenGuide,
        )

        SectionLabel("Your reports")
        NavigationRow(
            title = "Saved reports",
            subtitle = "Read a past test, or compare two phones",
            onClick = onOpenReports,
        )
        NavigationRow(
            title = "Settings",
            subtitle = "Theme, premium, privacy and diagnostics",
            onClick = onOpenSettings,
            leading = {
                // A real icon rather than the plain grey text this used to be. It sat at the very
                // bottom looking like a footnote, and on a full screen it was not there at all.
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = PhoneProofTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = PhoneProofTheme.colors.textTertiary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

data class HomeCheck(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)
