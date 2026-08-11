package com.phoneproof.feature.emilock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CategoryChip
import com.phoneproof.core.designsystem.component.CheckCategory
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
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
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Can anyone lock this phone remotely?",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = "Phones sold on instalments are locked through Android's device administrator " +
                "system. If a lender still controls this handset, it can be bricked weeks after " +
                "you pay — while you are the one holding it.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        if (result == null) {
            CheckingRow()
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

/** Mirrors the running row used by the full scan, so the two screens read as one instrument. */
@Composable
private fun CheckingRow() {
    val category = CheckCategory.SECURITY
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, category.tint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(category.tint, RoundedCornerShape(2.dp)),
        )
        Text(
            text = "Asking who controls this phone…",
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        CategoryChip(category)
    }
}
