package com.phoneproof.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.licence.RedeemResult
import com.phoneproof.core.preferences.passes.PassCode

/** Where the redeem screen is in its short life. */
enum class RedeemStage { ENTERING, WORKING, DONE }

data class RedeemUiState(
    val typed: String = "",
    val stage: RedeemStage = RedeemStage.ENTERING,
    val result: RedeemResult? = null,
) {
    /**
     * Whether the code is complete enough to be worth sending.
     *
     * Shape only, not the check character. Someone mid-type should not be told they are wrong; someone who
     * has finished typing eight characters should get an instant answer either way.
     */
    val canSubmit: Boolean
        get() = stage == RedeemStage.ENTERING && PassCode.normalise(typed) != null
}

/**
 * Unlocking a phone with a code bought on another one.
 *
 * The screen that makes the whole inspection-pass model work, and the one place in this app where the person
 * reading it is under the most pressure: they are holding someone else's phone, the seller wants it back, and
 * they have just typed something they paid for. Every message here is written for that moment.
 *
 * ## What it will not do
 *
 * It never says only "that didn't work". Each failure is told apart because the right next action differs
 * completely — check what you typed, move towards the door for signal, or accept that the pack is spent. A
 * single generic error would leave someone poking at a keyboard when their real problem is a basement.
 */
@Composable
fun RedeemScreen(
    state: RedeemUiState,
    onTypedChanged: (String) -> Unit,
    onRedeem: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "", modifier = Modifier.height(10.dp))
        ScreenTitle("I have a code")

        val granted = state.result as? RedeemResult.Granted
        if (granted != null && state.stage == RedeemStage.DONE) {
            Granted(granted, onDone)
            return@Column
        }

        Text(
            // Says where a code comes from, because someone may open this screen having never bought one.
            text = "Buy inspection passes on your own phone, then type the code here to unlock this one " +
                "for 24 hours. Nothing is left on this phone afterwards.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )

        OutlinedTextField(
            value = state.typed,
            onValueChange = onTypedChanged,
            label = { Text("Code") },
            placeholder = { Text("PP-XXXX-XXXX") },
            singleLine = true,
            enabled = state.stage != RedeemStage.WORKING,
            // Capitalised and finished with Done, because every code is upper case and there is nothing
            // after this field to move to.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        state.result?.let { Problem(it) }

        Button(
            onClick = onRedeem,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PhoneProofTheme.colors.accent,
                contentColor = PhoneProofTheme.colors.onAccent,
            ),
        ) {
            if (state.stage == RedeemStage.WORKING) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = PhoneProofTheme.colors.onAccent,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Unlock this phone", style = MaterialTheme.typography.titleMedium)
            }
        }

        Text(
            // The one thing that stops this screen feeling like a risk: it needs a network for a second, and
            // then it does not. Said before they tap, not after it fails.
            text = "This checks the code once, over the internet. The tests themselves never need a " +
                "connection.",
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun Granted(granted: RedeemResult.Granted, onDone: () -> Unit) {
    val hours = granted.pass.hoursLeftAt(System.currentTimeMillis())

    Text(
        text = if (granted.alreadyActive) "This phone was already unlocked" else "This phone is unlocked",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = PhoneProofTheme.colors.pass,
    )
    Text(
        text = buildString {
            append("Every check is available for the next ")
            append(if (hours == 1) "hour" else "$hours hours")
            append(".")
            if (granted.alreadyActive) {
                // The fairness point, said out loud. Being charged for reopening an app you closed by
                // accident is a small unfairness people remember longer than the price.
                append(" That cost you nothing — this phone already had a pass running.")
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = PhoneProofTheme.colors.textPrimary,
    )
    Text(
        text = if (granted.passesLeft == 1) {
            "1 inspection left on this code."
        } else {
            "${granted.passesLeft} inspections left on this code."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = PhoneProofTheme.colors.textSecondary,
    )

    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PhoneProofTheme.colors.accent,
            contentColor = PhoneProofTheme.colors.onAccent,
        ),
    ) {
        Text("Start testing", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * What went wrong, and what to do about it.
 *
 * Each case names an action. "Offline" in particular must not imply the code is bad — it is the expected
 * failure of this design, it is not the buyer's mistake, and telling someone their paid code is invalid when
 * the real problem is a basement would be the worst message this app could produce.
 */
@Composable
private fun Problem(result: RedeemResult) {
    val message = when (result) {
        is RedeemResult.Granted -> return
        RedeemResult.Malformed ->
            "That is not a complete code. Check the letters and numbers — O reads as zero, and I and L " +
                "read as one."
        RedeemResult.Unknown ->
            "We have no record of that code. Check it against the phone you bought it on."
        is RedeemResult.Exhausted ->
            "That code has no inspections left. Everything the free trial includes still works, and you " +
                "can buy more passes on your own phone."
        RedeemResult.Offline ->
            "Could not reach us to check the code. This needs a connection for one moment — try again " +
                "near a window or on mobile data. Your code is fine."
        RedeemResult.ServerProblem ->
            "Something went wrong at our end, not with your code. Please try again in a moment."
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (result is RedeemResult.Offline) {
            PhoneProofTheme.colors.textSecondary
        } else {
            PhoneProofTheme.colors.caution
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    )
}
