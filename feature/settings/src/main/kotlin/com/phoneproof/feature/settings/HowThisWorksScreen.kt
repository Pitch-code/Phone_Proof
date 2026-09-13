package com.phoneproof.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.component.decorative
import com.phoneproof.core.designsystem.theme.PhoneProofTheme

/**
 * The plain-English answer to "how do I actually pay, and how does a code work?".
 *
 * It exists because the two paid paths look similar on the shop screen and are not: Premium is a normal
 * purchase for your own phone, while a code is the awkward, unavoidable machinery for paying to inspect a
 * phone that is not yours and is signed into someone else's Google account. People were conflating them,
 * and a confused buyer on a payment screen is a buyer who does not buy — or who buys the wrong thing and
 * asks for a refund.
 *
 * ## What it deliberately does not say
 *
 * **No rupee figures.** Premium's price is whatever Play states at checkout — it moves with country, tax
 * and promotion — and a number typed here that disagrees with the sheet is both a support ticket and a
 * Play policy problem. The pack is not on sale yet, and a price on something nobody can buy is the exact
 * broken promise the shop screen was cleaned of. So this explains the *mechanics* — one payment, a pack of
 * inspections, 24 hours — and lets the card and the checkout sheet carry the actual money.
 *
 * Shown as a full-screen [Dialog] rather than a route (see [HowThisWorks]): the system back button and the
 * ✕ both simply dismiss it, returning to the shop screen underneath with nothing to wire into navigation.
 */
@Composable
fun HowThisWorksScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // The ✕ leads, at the top-start, because that is where a close control is looked for first and
        // because the title should not be the thing your thumb lands on. 48dp so it is a real target, and
        // labelled for a screen reader — a bare glyph would be read as "multiplication sign" or skipped.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloseButton(onClose)
            ScreenTitle("How this works")
        }

        Block(
            heading = "Two ways to pay — and they are not the same",
            body = listOf(
                "PhoneProof is free to try. When you want more, there are two paid options, and which one " +
                    "you need depends on whose phone you are testing.",
                "Premium is for a phone you are keeping. A code is for checking phones that belong to " +
                    "other people. Getting these two mixed up is the thing this page is here to prevent.",
            ),
        )

        Block(
            heading = "Premium — for a phone you are keeping",
            body = listOf(
                "One payment through Google Play, on the phone you want to unlock. It turns on every check " +
                    "on that phone and keeps them on for good. It is not a subscription and never renews.",
                "The price is on the Premium card and again on Google's payment sheet — that figure is the " +
                    "real one, so it is the only place it is shown.",
            ),
        )

        Example(
            title = "For example",
            lines = listOf(
                "You are buying a phone for yourself.",
                "You install PhoneProof on it, open the shop, and tap Premium.",
                "You pay once through Google Play.",
                "That phone now has every check, forever. Done.",
            ),
        )

        Block(
            heading = "A code — for checking other people's phones",
            body = listOf(
                "Here is the awkward part a code exists to solve. A Google Play purchase belongs to your " +
                    "Google account — but this app runs on the phone being inspected, which is usually the " +
                    "seller's phone, signed in to the seller's account. Your purchase is invisible there.",
                "A code carries what you paid for onto a phone that is not yours. You buy a pack of " +
                    "inspections once, on your own phone, and the app gives you a code that looks like " +
                    "PP-7K2M-9QXV. You take that code to any phone you want to test.",
                "On the seller's phone you install PhoneProof (free), tap \u201cI have a code\u201d, and type " +
                    "it in. That phone unlocks every check for 24 hours. When the day is up it goes back to " +
                    "free — nothing you paid for is left behind on a phone that is not yours.",
            ),
        )

        Block(
            heading = "Code limits and expiry",
            body = listOf(
                "A code is a pack of inspections. Each new phone you unlock uses one from the pack.",
                "Reopening the app on the same phone within the 24 hours costs nothing — you are never " +
                    "charged twice for the same phone, so closing the app by accident is not a trap.",
                "After 24 hours that phone drops back to the free trial. The inspection you spent does not " +
                    "come back.",
                "When the pack runs out, you buy another on your own phone. The tests themselves never need " +
                    "a connection — only typing in a code does, and only for a moment.",
            ),
        )

        Example(
            title = "A real example, from the first phone to the last",
            lines = listOf(
                "Ramesh checks second-hand phones before he buys them to resell.",
                "On his own phone he buys a pack of, say, 5 inspections. The app gives him one code.",
                "At the first seller's, he types the code. That phone unlocks for 24 hours — 4 left.",
                "While he is haggling he reopens the app twice on that same phone. Still 4 left, no charge.",
                "At the next seller's, he types the same code. That phone unlocks — 3 left.",
                "After five different phones the code is empty, and he buys another pack when he needs it.",
            ),
        )

        Block(
            heading = "What we can and cannot see",
            body = listOf(
                "The code and a scrambled, one-way fingerprint of the phone are all that reach us, and only " +
                    "to count inspections. No name, no number, no account. We cannot tell which phones you " +
                    "checked, or link one phone to another.",
                "Google takes the payment, not us — so Google is also the only one who can refund it.",
            ),
        )

        Spacer(Modifier.height(12.dp))
    }
}

/**
 * A full-screen window over the shop screen.
 *
 * `usePlatformDefaultWidth = false` so it fills the screen rather than sitting in a small centred card —
 * this is a page of reading, not an alert. `onDismissRequest` fires for both the system back button and a
 * tap outside, so [HowThisWorksScreen]'s ✕ and the hardware back gesture converge on the same one exit.
 */
@Composable
fun HowThisWorks(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        HowThisWorksScreen(onClose = onDismiss, modifier = Modifier.fillMaxSize())
    }
}

/** The ✕ close control: a real 48dp target, labelled for a screen reader rather than read as a glyph. */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClose)
            .semantics {
                contentDescription = "Close"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "\u2715",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
            // The glyph itself carries no meaning past the labelled parent; keep the reader off it.
            modifier = Modifier.decorative(),
        )
    }
}

/** A heading and its paragraphs. The heading is announced as one so a screen reader can jump between them. */
@Composable
private fun Block(heading: String, body: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = PhoneProofTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        body.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * A worked example, set apart in a card because a reader skimming for "show me" should be able to find it
 * without reading the explanation around it. Numbered so the order reads as steps, not as a shuffle.
 */
@Composable
private fun Example(title: String, lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PhoneProofTheme.colors.textSecondary,
        )
        lines.forEachIndexed { index, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = PhoneProofTheme.colors.accent,
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                )
            }
        }
    }
}
