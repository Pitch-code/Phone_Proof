package com.phoneproof.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The checks on their own, for a buyer who does not want the whole run.
 *
 * This list used to sit on Home underneath the button that starts the guided run, which walks through
 * every one of these in order. A real user asked the obvious question: why is the same thing on the
 * screen twice, and what is the difference?
 *
 * The difference is real — one sequences everything and produces a verdict, the other answers a single
 * question — but nine duplicated rows on the front page was a poor way of saying so. Moving them here
 * leaves Home with one dominant action, and gives the distinction somewhere to be explained in words
 * instead of implied by layout.
 */
@Composable
fun ChecksScreen(
    checks: List<HomeCheck>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "One test at a time",
            style = MaterialTheme.typography.displaySmall,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            // Answers the "why is this here as well?" question in the product rather than leaving the
            // buyer to work it out. Both sentences are needed: the first says when to use this screen,
            // the second says what it costs them compared with the run.
            text = "For when you want one answer rather than the whole inspection — the IMEI on a " +
                "phone you have already decided about, or the touch test again after wiping the " +
                "screen.",
            style = MaterialTheme.typography.bodyLarge,
            color = PhoneProofTheme.colors.textSecondary,
        )
        Text(
            text = "Nothing here is added up. \"Test this phone\" on the home screen runs all of " +
                "them in a sensible order and ends with a verdict you can argue with.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )

        Spacer(Modifier.height(4.dp))

        checks.forEach { check ->
            NavigationRow(
                title = check.title,
                subtitle = check.subtitle,
                onClick = check.onClick,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}
