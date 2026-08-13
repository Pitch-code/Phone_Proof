package com.phoneproof.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * Shown where the free trial stops.
 *
 * One component for every locked case — the scan allowance, claimed-against-measured, and the manual
 * checks ([MANUAL_CHECKS_TITLE]) — so the wording, the tone and the route out are identical wherever
 * a buyer meets a limit. Three separately written lock screens would drift, and the one that drifts
 * is always the one that reads as a shakedown.
 *
 * The two advisory locks share [ADVISORY_TRIAL_EXCLUSION] for the reason they are locked, because
 * this component only keeps the tone identical if the explanation is not retyped per screen.
 *
 * Three rules this screen follows, because a paywall is where an app is most easily resented:
 *
 *  1. **Say what is locked and why**, in the same words as the plan that unlocks it.
 *  2. **Never dead-end.** [onOpenSettings] always goes somewhere the plans can be read in full,
 *     rather than a buy button with no explanation behind it.
 *  3. **Never imply a measurement was taken.** Nothing here reports on the phone.
 */
@Composable
fun LockedFeature(
    title: String,
    /** Why this is limited, in plain words. */
    explanation: String,
    /** What paying actually changes. Concrete, not "unlock premium features". */
    whatUnlockingGives: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Scrolls, because this screen is a fixed column of text whose length is decided by
            // whichever caller wrote the longest explanation. On a short phone the button was the
            // thing that fell off the bottom — a paywall with no way out. Home had the same bug.
            // Costs nothing when the content already fits: the layout is identical.
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "WITH A PLAN",
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofTheme.colors.textTertiary,
            )
            Text(
                text = whatUnlockingGives,
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
        }

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.accent,
                contentColor = Color.White,
            ),
        ) {
            Text("See the plans in Settings", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            text = "Nothing you have already tested is lost. Your saved reports stay on the phone.",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
