package com.phoneproof.feature.settings

import androidx.compose.runtime.Immutable
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.preferences.Entitlement

/**
 * The paid tiers.
 *
 * Prices are held here as display strings only. When billing is wired, the *authoritative* price
 * comes from Play — it varies by country, tax and any promotion, and a hardcoded price that
 * disagrees with the checkout sheet is both a support burden and a Play policy problem. These exist
 * so the wording and structure can be reviewed before any of that is set up.
 *
 * Note what no tier removes: every check stays unlimited and free. Revenue comes from keeping and
 * sharing results, never from rationing the measurement — rationing the core function of a
 * trust-focused app teaches people to distrust it.
 */
enum class PremiumPlan(
    val productId: String,
    val title: String,
    val audience: String,
    val price: String,
    val billing: String,
    val recommended: Boolean,
    val benefits: List<String>,
) {
    PREMIUM(
        productId = "phoneproof_premium",
        title = "Premium",
        audience = "For buying a phone for yourself",
        price = "₹99",
        billing = "one-time",
        recommended = true,
        benefits = listOf(
            "No ads, anywhere",
            "Keep every report instead of only the last two",
            "Save a report as a PDF to send or print",
            "Compare two phones side by side before deciding",
            "One payment. Not a subscription, ever",
        ),
    ),
    SHOP(
        productId = "phoneproof_shop_yearly",
        title = "Shop",
        audience = "For dealers and repair shops",
        price = "₹999",
        billing = "per year",
        recommended = false,
        benefits = listOf(
            "Everything in Premium",
            "Your shop name and logo on every report",
            "Unlimited saved devices with notes on each",
            "Export all reports at once",
            "Hand a customer a branded report they can keep",
        ),
    ),
}

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val versionName: String = "",
    val versionCode: Long = 0L,
    /**
     * False until Play Billing is wired and the app is distributed through Play. Kept explicit so
     * the UI can say so rather than offering a button that quietly fails.
     */
    val billingAvailable: Boolean = false,
    /** What this install currently has. Drives which paid features are reachable. */
    val entitlement: Entitlement = Entitlement.FREE,
    val shopName: String? = null,
    val shopContact: String? = null,
    val shopLogoPath: String? = null,
    /**
     * Whether to offer the tier switcher.
     *
     * True only in a debug build. Play Billing cannot complete a purchase in a sideloaded APK, so
     * without this the paid features could not be exercised at all before release — they would ship
     * having never been run. It is passed in from the app module rather than read here, so a release
     * build physically cannot show it.
     */
    val showTestingControls: Boolean = false,
    val ownedPlan: PremiumPlan? = null,
)
