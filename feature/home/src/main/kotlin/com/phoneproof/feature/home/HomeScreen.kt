package com.phoneproof.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * Home.
 *
 * Not a dashboard. One dominant action, because the person holding this phone has about three
 * minutes and an audience — a grid of equally-weighted tiles would make them stop and read.
 *
 * The individual checks are listed below the primary action only because the full guided run does
 * not exist yet. As checks land they fold into the single run and disappear from this list.
 */
@Composable
fun HomeScreen(
    checks: List<HomeCheck>,
    onStartFullTest: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            // safeDrawing, not statusBars: it also covers the navigation bar, gesture areas and
            // display cutouts, so this holds on a notch phone and a 3-button phone alike rather
            // than being tuned to one handset.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))

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

        Spacer(Modifier.height(8.dp))

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

        Text(
            // Wording is deliberate and must not be strengthened. The app shows ads, so an
            // advertising ID does leave the device; claiming "nothing leaves this device" would be
            // false, and overclaiming privacy is worse than not claiming it.
            text = "Your test results stay on this device",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Individual checks",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )

        checks.forEach { check ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
                    .clickable(onClick = check.onClick)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = check.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = PhoneProofTheme.colors.textPrimary,
                    )
                    Text(
                        text = check.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhoneProofTheme.colors.textTertiary,
                    )
                }
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Given its own row above Settings rather than buried inside it. A report the buyer cannot
        // find again is the same as no report, and this is the screen they return to.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenReports)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Saved reports",
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }

        Text(
            text = "Settings",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
    }
}

data class HomeCheck(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)
