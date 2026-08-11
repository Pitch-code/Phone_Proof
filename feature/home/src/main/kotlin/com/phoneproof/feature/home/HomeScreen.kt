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
import com.phoneproof.core.designsystem.theme.PhoneProofColors

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
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofColors.Background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            text = "PhoneProof",
            style = MaterialTheme.typography.displaySmall,
            color = PhoneProofColors.TextPrimary,
        )
        Text(
            text = "Find the faults before you pay for them.",
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofColors.TextSecondary,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onStartFullTest,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofColors.Accent,
                contentColor = PhoneProofColors.TextPrimary,
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
            color = PhoneProofColors.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Individual checks",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofColors.TextTertiary,
        )

        checks.forEach { check ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PhoneProofColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, PhoneProofColors.Border, RoundedCornerShape(12.dp))
                    .clickable(onClick = check.onClick)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = check.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = PhoneProofColors.TextPrimary,
                    )
                    Text(
                        text = check.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhoneProofColors.TextTertiary,
                    )
                }
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = PhoneProofColors.TextTertiary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofColors.TextTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDiagnostics)
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
