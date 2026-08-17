package com.phoneproof.feature.imei

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.phoneproof.checks.imei.Imei
import com.phoneproof.checks.imei.ImeiCheck
import com.phoneproof.core.designsystem.component.CheckResultCard
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.PhoneProofType

/**
 * The IMEI screen: fifteen digits in, and a pointer at the one service that can answer the question
 * the buyer actually has.
 *
 * The screen is honest about being a form rather than a measurement, which makes it the odd one out in
 * this app. Every other check reads the phone; this one cannot, because the platform has refused apps
 * the IMEI since Android 10. Saying so up front is better than a screen that looks like it is
 * measuring something and is really waiting for typing.
 *
 * The verdict updates as the digits arrive rather than behind a button. There is no cost to
 * re-evaluating — it is a fifteen-digit checksum, not a scan — and a buyer copying a worn sticker
 * benefits from finding out at the fifteenth digit rather than after hunting for a Check button.
 */
@Composable
fun ImeiScreen(
    typed: String,
    onTypedChanged: (String) -> Unit,
    onOpenCeir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imei = Imei.of(typed)
    val result = ImeiCheck.evaluate(imei)

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
        ScreenTitle("IMEI and the stolen-phone register")
        Text(
            text = "Android does not let any app read the IMEI, so this one is typed in. On the " +
                "phone, dial *#06#, or look in Settings, About phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        OutlinedTextField(
            value = typed,
            // Filtered to digits here as well as in Imei.of, because the field should not let a
            // character appear and then vanish from the formatted line underneath it.
            onValueChange = { onTypedChanged(it.filter(Char::isDigit).take(Imei.FULL_LENGTH)) },
            label = { Text("IMEI") },
            singleLine = true,
            // A number pad: fifteen digits, typed one-handed, in a shop. autoCorrectEnabled is off for
            // the same reason it is off on the shop name — there is nothing here to correct, and a
            // predictive keyboard rewriting a serial number is the bug that took two goes to find.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // The digits grouped the way the box and the *#06# dialog group them, so a buyer can compare
        // what they typed against what they are reading without counting characters.
        if (imei.length > 0) {
            Text(
                text = imei.formatted,
                style = PhoneProofType.NumericLarge,
                color = if (imei.isComplete && !imei.isValid) {
                    PhoneProofTheme.colors.caution
                } else {
                    PhoneProofTheme.colors.textPrimary
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        CheckResultCard(result)

        Button(
            onClick = onOpenCeir,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.accent,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = if (imei.isComplete) "Copy and open the CEIR portal" else "Open the CEIR portal",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Deliberately explicit about which site, and about the limits of what it can tell you.
        //
        // The web is full of IMEI-lookup sites that will happily report a phone as "clean" for money,
        // and a buyer who has just used this screen is exactly who they are aimed at. Naming the
        // government portal, and only that, is the useful thing this screen can do.
        Text(
            text = "CEIR is the Indian government's register, at ceir.sancharsaathi.gov.in. It is " +
                "the only place that knows whether a handset has been reported lost or stolen. " +
                "Ignore any other IMEI-checking site that offers you a verdict.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
        Text(
            text = "A phone can be blocked days after a sale, once the previous owner files a " +
                "report. Check it while you are still standing in front of the seller.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.caution,
        )
    }
}
