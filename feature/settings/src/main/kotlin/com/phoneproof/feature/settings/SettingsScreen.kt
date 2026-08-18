package com.phoneproof.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneproof.core.designsystem.MANUAL_CHECKS_TITLE
import com.phoneproof.core.designsystem.component.ScreenTitle
import com.phoneproof.core.designsystem.component.decorative
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.PhoneProofType
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.reports.ShopBranding

/**
 * Settings.
 *
 * Deliberately one scrolling column with no nested screens. There are five things here, and burying
 * any of them behind another tap would be worse than the scroll.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onShareApp: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onChoosePlan: (PremiumPlan) -> Unit,
    onShopNameChanged: (String) -> Unit = {},
    onShopContactChanged: (String) -> Unit = {},
    onPickLogo: () -> Unit = {},
    onRemoveLogo: () -> Unit = {},
    onEntitlementSelected: (Entitlement) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneProofTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle(text = "Settings", modifier = Modifier.padding(top = 12.dp))

        Section("Appearance") {
            ThemeMode.entries.forEach { mode ->
                ThemeRow(
                    mode = mode,
                    selected = state.themeMode == mode,
                    onClick = { onThemeSelected(mode) },
                )
            }
        }

        Section("Your plan") {
            // The free trial gets a card of its own, listed like the paid ones. Someone deciding
            // whether to pay needs to see what they already have beside what they would gain; a bare
            // "2 scans left" tells them the cost of not paying without telling them the benefit.
            FreeTrialCard(
                active = state.entitlement == Entitlement.FREE,
                scansLeft = state.freeScansLeft,
            )
        }

        Section("Premium") {
            PremiumPlan.entries.forEach { plan ->
                PlanCard(
                    plan = plan,
                    owned = state.ownedPlan == plan,
                    purchasable = state.isPurchasable(plan),
                    onSale = state.isOnSale(plan),
                    price = state.priceOf(plan),
                    pending = state.isPending(plan),
                    onClick = { onChoosePlan(plan) },
                )
            }
            if (!state.billingAvailable) {
                // Each card already carries this state, so this only adds the reason. Worded for a
                // real user, not for us: billingAvailable is also false when Play Billing fails to
                // start on a real install, so this copy can genuinely reach someone.
                Text(
                    text = "Buying is only available when PhoneProof is installed from Google Play.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }

            // Who takes the money, and who can give it back.
            //
            // Said before the purchase rather than discovered after it, because the commonest support
            // question about a paid app is "how do I get my money back" and the answer is not the
            // developer. This app has no payment relationship with anyone: Play takes the payment, so
            // Play is the only party that can reverse it.
            //
            // Deliberately does NOT claim purchases are non-refundable. See monetisation.md — the
            // developer cannot make that true, Google refunds regardless of what an app says, and a
            // false statement on the screen that asks for money is the last place this app can afford
            // one. Do not "tighten" this wording into a no-refunds policy.
            Text(
                text = PURCHASE_TERMS,
                style = MaterialTheme.typography.labelSmall,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }

        if (state.entitlement.hasShopBranding) {
            Section("Your shop") {
                Text(
                    text = "Printed at the top of every PDF report you hand a customer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
                // autoCorrectEnabled = false on both, and it is not a nicety. A shop name and a
                // phone number are precisely the two kinds of text a predictive keyboard has no
                // business rewriting: it treats them as misspelled words and substitutes across the
                // composing region, which is one of the two things that was scrambling this input.
                OutlinedTextField(
                    value = state.shopName.orEmpty(),
                    onValueChange = { onShopNameChanged(it.take(ShopBranding.MAX_NAME_LENGTH)) },
                    label = { Text("Shop name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.shopContact.orEmpty(),
                    onValueChange = { onShopContactChanged(it.take(ShopBranding.MAX_CONTACT_LENGTH)) },
                    label = { Text("Phone or address") },
                    singleLine = true,
                    // Deliberately not KeyboardType.Phone. The field takes a phone number *or* a
                    // street address — "98765 43210 · MG Road" is the example in its own render — and
                    // a dial pad cannot type an address. With the value no longer being replaced
                    // under the cursor, the keyboard now stays on whichever page was chosen instead
                    // of snapping back to letters after every digit.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionRow(
                    label = if (state.shopLogoPath == null) "Choose a logo" else "Change the logo",
                    onClick = onPickLogo,
                )
                if (state.shopLogoPath != null) {
                    ActionRow(label = "Remove the logo", onClick = onRemoveLogo)
                }
                // Stated plainly. A shop that expected to hand over a document with only its own
                // name on it should know before it prints a hundred of them.
                Text(
                    text = "Every report still says the measurements came from PhoneProof. That " +
                        "line cannot be removed — it is what makes the report worth showing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
        }

        // Two files named TierOverride.kt exist, one in src/debug and one in src/release. The debug one
        // draws a switcher for every tier; the release one draws nothing. So a shipped APK contains no
        // code that can grant a paid tier — there is no flag here to get wrong.
        TierOverride(current = state.entitlement, onSelect = onEntitlementSelected)

        Section("About") {
            InfoRow("Version", state.versionName)
            InfoRow("Build", state.versionCode.toString())
            ActionRow("Privacy policy", onOpenPrivacyPolicy)
            ActionRow("Share this app", onShareApp)
            ActionRow("Diagnostics", onOpenDiagnostics)
        }

        Text(
            text = "PhoneProof measures this phone and keeps the results on it. Nothing about the " +
                "phones you test is uploaded anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textTertiary,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
// internal, not private: the debug-only tier switcher lives in src/debug and composes with these.
internal fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // titleSmall and bold in secondary ink, up from labelSmall in tertiary — the smallest and
        // faintest combination the design system has, used for the only labels that tell you where
        // you are on the screen. "YOUR SHOP" was reported as not catching the eye; it was set two
        // steps below the body text underneath it, so the heading was quieter than its own contents.
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PhoneProofTheme.colors.textSecondary,
        )
        content()
    }
}

@Composable
private fun ThemeRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = PhoneProofTheme.colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.55f) else PhoneProofTheme.colors.border,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A filled ring rather than a tick glyph, so the selected state does not depend on colour
        // alone for anyone who cannot distinguish the accent from the border.
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(2.dp, if (selected) accent else PhoneProofTheme.colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(9.dp).background(accent, CircleShape))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = mode.label,
                style = MaterialTheme.typography.titleMedium,
                color = PhoneProofTheme.colors.textPrimary,
            )
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: PremiumPlan,
    owned: Boolean,
    purchasable: Boolean,
    /** Whether this tier is offered at all — see the status label below. */
    onSale: Boolean,
    /** Play's own figure where it is known; the built-in constant until Play answers. */
    price: String,
    /** A payment started and not yet settled — routine with UPI. */
    pending: Boolean,
    onClick: () -> Unit,
) {
    val accent = PhoneProofTheme.colors.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(14.dp))
            .border(
                if (plan.recommended) 2.dp else 1.dp,
                if (plan.recommended) accent.copy(alpha = 0.5f) else PhoneProofTheme.colors.border,
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = purchasable && !owned, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                )
                Text(
                    text = plan.audience,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // Play's price, not the constant, whenever Play has answered: it varies by country,
                    // tax and promotion, and disagreeing with the checkout sheet is a policy problem.
                    text = price,
                    style = PhoneProofType.NumericLarge,
                    color = PhoneProofTheme.colors.textPrimary,
                )
                Text(
                    text = plan.billing,
                    style = MaterialTheme.typography.labelSmall,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
        }

        plan.benefits.forEach { benefit ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "✓",
                    color = PhoneProofTheme.colors.pass,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = benefit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textSecondary,
                )
            }
        }

        Text(
            text = when {
                owned -> "Active on this device"
                // Said before "unavailable", because to someone who has just paid by UPI the two look
                // nothing alike: one is the app waiting, the other is the app refusing.
                pending -> "Waiting for your payment to clear"
                // Separated from "unavailable" after looking at the render: with billing working, the
                // Shop card said "Unavailable", which is not what is happening. It is not on sale, which
                // is a decision — and one worth stating plainly rather than leaving someone tapping.
                !onSale -> "Not on sale yet"
                !purchasable -> "Unavailable"
                else -> "Choose ${plan.title}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when {
                owned -> PhoneProofTheme.colors.pass
                pending -> PhoneProofTheme.colors.caution
                // Not an action and not a fault, so neither accent nor amber.
                !onSale -> PhoneProofTheme.colors.textTertiary
                else -> accent
            },
        )
        if (pending) {
            Text(
                // The sentence that stops a second payment. A buyer who thinks a UPI payment failed
                // will try again, and paying twice for a one-time product is a refund request and a
                // bad review.
                text = "Your payment has not failed. UPI and bank transfers can take a few minutes, " +
                    "and this unlocks by itself as soon as Google confirms it — you do not need to " +
                    "pay again.",
                style = MaterialTheme.typography.bodyMedium,
                color = PhoneProofTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneProofTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = PhoneProofType.Numeric,
            color = PhoneProofTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, PhoneProofTheme.colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.textPrimary,
        )
        Text(
            text = "›",
            // A glyph, not information. The row's own text says where it goes.
            modifier = Modifier.decorative(),
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun FreeTrialCard(active: Boolean, scansLeft: Int?) {
    val accent = PhoneProofTheme.colors.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (active) accent.copy(alpha = 0.5f) else PhoneProofTheme.colors.border,
                RoundedCornerShape(14.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Free trial",
                    style = MaterialTheme.typography.titleMedium,
                    color = PhoneProofTheme.colors.textPrimary,
                )
                Text(
                    text = "To try the app before paying",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
            }
            Text(
                text = "Free",
                style = PhoneProofType.NumericLarge,
                color = PhoneProofTheme.colors.textPrimary,
            )
        }

        // Included and not included, in one list. Splitting them into two sections lets a reader
        // skim only the good half, and the limit is the whole point of this card.
        listOf(
            true to "${Entitlement.FREE_SCAN_LIMIT} full scans of a phone",
            true to "Every check runs in full — nothing is watered down",
            true to "Touch grid, dead pixels and burn-in, remote lock, battery",
            true to "Your last 2 reports, kept on the phone",
            false to "Claimed against measured",
            false to MANUAL_CHECKS_TITLE,
            false to "PDF reports, comparing two phones, unlimited history",
        ).forEach { (included, text) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (included) "✓" else "✕",
                    color = if (included) {
                        PhoneProofTheme.colors.pass
                    } else {
                        PhoneProofTheme.colors.textTertiary
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (included) {
                        PhoneProofTheme.colors.textSecondary
                    } else {
                        PhoneProofTheme.colors.textTertiary
                    },
                )
            }
        }

        if (active) {
            Text(
                // "both scans used" was the third place the number two was written into the English.
                // Phrased as what is left rather than what is gone, it matches the two branches below
                // it and stays true at any limit.
                text = when (scansLeft) {
                    null -> "Active on this device"
                    0 -> "Active — no scans left"
                    1 -> "Active — 1 scan left"
                    else -> "Active — $scansLeft scans left"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (scansLeft == 0) {
                    PhoneProofTheme.colors.caution
                } else {
                    PhoneProofTheme.colors.pass
                },
            )
        }
    }
}

/** The theme row's selectable look, reused so the tier switcher does not invent a second one. */
@Composable
internal fun ThemeLikeRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = PhoneProofTheme.colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PhoneProofTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.55f) else PhoneProofTheme.colors.border,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(2.dp, if (selected) accent else PhoneProofTheme.colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(9.dp).background(accent, CircleShape))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PhoneProofTheme.colors.textPrimary,
        )
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Match my phone"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private val ThemeMode.description: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Follows your phone's own light or dark setting"
        // "The default" moves with the default. Leaving it on Dark would have made the settings
        // screen quietly lie to every new user about what they were looking at.
        ThemeMode.LIGHT -> "The default. Easier to read in bright sunlight"
        ThemeMode.DARK -> "Kinder to the eyes indoors"
    }

/**
 * What a buyer is told about the money, before they part with it.
 *
 * Internal rather than inline so a test can assert what it does and does not say.
 *
 * Every clause here is something the developer can actually stand behind:
 *
 *  - Play handles the payment, so the app genuinely never sees a card.
 *  - The app cannot issue a refund. True: there is no server, no merchant account and no payment
 *    relationship — only Google can reverse a Google payment.
 *  - Refund requests go to Google, and Google decides. Accurate, and it sends the request to the only
 *    party who can act on it instead of to an inbox that cannot.
 *  - A refund switches the features off again. Also true, and worth saying: entitlement is recomputed
 *    from Play on every launch, so it is not a threat, it is a description.
 *
 * What it does not say is that purchases are non-refundable, and that omission is deliberate.
 */
internal const val PURCHASE_TERMS: String =
    "One payment, taken by Google Play — this app never sees your card and cannot take or return " +
        "money itself. Any refund is Google's decision and is requested through Google Play; if a " +
        "purchase is refunded, the paid features switch off again."
