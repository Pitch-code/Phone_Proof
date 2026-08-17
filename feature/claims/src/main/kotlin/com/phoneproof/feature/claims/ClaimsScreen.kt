package com.phoneproof.feature.claims

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.model.CheckResult

/**
 * Ask the buyer what the seller claimed, then contrast it.
 *
 * The figures are typed in rather than looked up. That is not a shortcut: no licensable device
 * catalogue permits commercial use, and the stale ones are actively wrong — a rival app was seen
 * reporting a current flagship as having 3 GB of RAM. More importantly, the claim *is* the thing
 * being tested, and only the seller made it.
 */
@Composable
fun ClaimsScreen(
    storage: String,
    ram: String,
    model: String,
    result: CheckResult?,
    onStorageChanged: (String) -> Unit,
    onRamChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onCompare: () -> Unit,
    onReset: () -> Unit,
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
        Spacer(Modifier.height(6.dp))
        ScreenTitle("What were you told?")
        Text(
            text = "Type in what the seller or the advert says. The phone will be measured and the " +
                "two put side by side.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        if (result == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = storage,
                    onValueChange = { onStorageChanged(it.filter(Char::isDigit).take(4)) },
                    label = { Text("Storage (GB)") },
                    // A number pad, not a full keyboard. This is a field for "128" and the buyer is
                    // typing one-handed in a shop.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ram,
                    onValueChange = { onRamChanged(it.filter(Char::isDigit).take(3)) },
                    label = { Text("RAM (GB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = model,
                onValueChange = { onModelChanged(it.take(60)) },
                label = { Text("Model, as advertised") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Leave anything blank that you were not told.",
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofTheme.colors.textTertiary,
            )

            Button(
                onClick = onCompare,
                enabled = storage.isNotBlank() || ram.isNotBlank() || model.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhoneProofTheme.colors.accent,
                    contentColor = Color.White,
                ),
            ) {
                Text("Compare", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            CheckResultCard(result)
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Change what you were told")
            }
        }
    }
}
