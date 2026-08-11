package com.phoneproof.feature.emilock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.model.CheckResult

/**
 * Shows the remote-lock verdict.
 *
 * Stateless so the screenshot tests can render every outcome — including a device-owner failure,
 * which is impossible to reproduce on a normal handset — without owning a locked phone.
 */
@Composable
fun EmiLockScreen(
    result: CheckResult?,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Can anyone lock this phone remotely?",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofColors.TextPrimary,
        )
        Text(
            text = "Phones sold on instalments are locked through Android's device administrator " +
                "system. If a lender still controls this handset, it can be bricked weeks after " +
                "you pay — while you are the one holding it.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofColors.TextSecondary,
        )

        if (result == null) {
            Text(
                text = "Checking…",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofColors.TextTertiary,
            )
        } else {
            CheckResultCard(result)
        }

        OutlinedButton(
            onClick = onRecheck,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            // The action for almost every negative outcome here is "reset the phone, then check
            // again", so re-running has to be one obvious tap.
            Text("Check again")
        }
    }
}
