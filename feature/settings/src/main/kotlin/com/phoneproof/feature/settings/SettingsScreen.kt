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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.reports.ShopBranding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneproof.core.designsystem.MANUAL_CHECKS_TITLE
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.PhoneProofType
import com.phoneproof.core.designsystem.theme.ThemeMode

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
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = PhoneProofTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )

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
                    purchasable = state.billingAvailable,
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
        }

        if (state.entitlement.hasShopBranding) {
            Section("Your shop") {
                Text(
                    text = "Printed at the top of every PDF report you hand a customer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.textTertiary,
                )
                OutlinedTextField(
                    value = state.shopName.orEmpty(),
                    onValueChange = { onShopNameChanged(it.take(ShopBranding.MAX_NAME_LENGTH)) },
                    label = { Text("Shop name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.shopContact.orEmpty(),
                    onValueChange = { onShopContactChanged(it.take(ShopBranding.MAX_CONTACT_LENGTH)) },
                    label = { Text("Phone or address") },
                    singleLine = true,
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

        if (state.showTestingControls) {
            Section("Testing only") {
                Text(
                    text = "This build cannot take payments, so the paid tiers are unlocked here " +
                        "to be tested. Debug builds only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhoneProofTheme.colors.caution,
                )
                Entitlement.entries.forEach { tier ->
                    ThemeLikeRow(
                        title = tier.label,
                        selected = state.entitlement == tier,
                        onClick = { onEntitlementSelected(tier) },
                    )
                }
            }
        }

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
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PhoneProofTheme.colors.textTertiary,
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
                    text = plan.price,
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
                !purchasable -> "Unavailable"
                else -> "Choose ${plan.title}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (owned) PhoneProofTheme.colors.pass else accent,
        )
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
                text = when (scansLeft) {
                    null -> "Active on this device"
                    0 -> "Active — both scans used"
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
private fun ThemeLikeRow(
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
