package com.phoneproof.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneproof.core.designsystem.theme.PhoneProofColors
import com.phoneproof.core.model.CheckOutcome

/**
 * The outcome marker used everywhere a verdict appears.
 *
 * Deliberately renders **glyph + colour + word** together. Colour alone is never enough here:
 * the report card is designed to be photographed and forwarded, sometimes in greyscale, and
 * colour-only encoding excludes colour-blind users. A plain text glyph is also used in place of
 * an icon font so the badge survives being screenshotted at any scale.
 */
@Composable
fun OutcomeBadge(
    outcome: CheckOutcome,
    modifier: Modifier = Modifier,
) {
    val accent = outcome.accent()
    Row(
        modifier = modifier
            .background(PhoneProofColors.fill(accent), RoundedCornerShape(6.dp))
            .border(1.dp, PhoneProofColors.outline(accent), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = outcome.glyph(),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = outcome.label(),
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

fun CheckOutcome.accent(): Color = when (this) {
    CheckOutcome.PASS -> PhoneProofColors.Pass
    CheckOutcome.CAUTION -> PhoneProofColors.Caution
    CheckOutcome.FAIL -> PhoneProofColors.Fail
    CheckOutcome.UNKNOWN -> PhoneProofColors.Unknown
}

fun CheckOutcome.glyph(): String = when (this) {
    CheckOutcome.PASS -> "✓"
    CheckOutcome.CAUTION -> "!"
    CheckOutcome.FAIL -> "✕"
    CheckOutcome.UNKNOWN -> "?"
}

fun CheckOutcome.label(): String = when (this) {
    CheckOutcome.PASS -> "PASS"
    CheckOutcome.CAUTION -> "CHECK AGAIN"
    CheckOutcome.FAIL -> "PROBLEM"
    // "Can't tell" rather than "unknown": it names a limit of Android, not a failure of the
    // phone, and a buyer must not read it as a defect.
    CheckOutcome.UNKNOWN -> "CAN'T TELL"
}
