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
 * The model changed here, and the old comment claiming "every check stays unlimited and free" had to
 * go with it: the free trial now gets [Entitlement.FREE_SCAN_LIMIT] scans and then stops, and the two
 * advisory screens are paid. Leaving that sentence in place would have been a false promise printed
 * on the screen where the app asks for money.
 *
 * What the trial still does *not* do is weaken a measurement. Every check that runs, runs in full
 * and reports the same verdict at every tier — the limit is how many times, never how honestly.
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
    val themeMode: ThemeMode = ThemeMode.LIGHT,
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
    /** Scans left on the free trial, or null when unlimited. Shown on the free-trial card. */
    val freeScansLeft: Int? = null,
    val ownedPlan: PremiumPlan? = null,
)
