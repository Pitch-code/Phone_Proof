package com.phoneproof.core.designsystem.component

import androidx.compose.foundation.background
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

/**
 * What kind of thing a check looked at, so a long report reads as grouped sections rather than a
 * uniform wall of cards.
 *
 * Derived from the check id's namespace rather than a hand-maintained list, so a new check is
 * categorised correctly the moment it is written. That is the whole reason ids look like
 * `hardware.storage` and `security.device_admin_lock`.
 *
 * Every hue here is deliberately kept away from green, amber and red. Those four colours mean
 * pass, caution, problem and can't-tell, and a category tint that could be mistaken for a verdict
 * would be worse than no colour at all.
 */
enum class CheckCategory(
    val label: String,
    val glyph: String,
    val tint: Color,
) {
    SECURITY("Security", "⛨", Color(0xFF6366F1)),
    SOFTWARE("Software", "◈", Color(0xFF14B8A6)),
    HARDWARE("Hardware", "▣", Color(0xFFA78BFA)),
    SCREEN("Screen", "▤", Color(0xFF22D3EE)),
    OTHER("Check", "•", Color(0xFF64748B)),
    ;

    companion object {
        fun forCheckId(id: String): CheckCategory = when (id.substringBefore('.')) {
            "security" -> SECURITY
            "software" -> SOFTWARE
            "hardware" -> HARDWARE
            "screen" -> SCREEN
            else -> OTHER
        }
    }
}

/** The small tinted chip that heads each result card. */
@Composable
fun CategoryChip(
    category: CheckCategory,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(category.tint.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = category.glyph, color = category.tint, fontSize = 10.sp)
        Text(
            text = category.label.uppercase(),
            color = category.tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
        )
    }
}
