package com.phoneproof.feature.settings

import androidx.compose.runtime.Immutable
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.billing.BillingProducts
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
            // "No ads, anywhere" was here, and it was selling the absence of something that never
            // existed: there is no advertising code anywhere in this app, and never has been. On the
            // screen that asks for money, a benefit nobody is actually receiving is the worst possible
            // place for a stale claim - a buyer could pay partly for it.
            //
            // Replaced with a real benefit that was missing from this list entirely: the three measured
            // checks the free trial leaves out. Named here rather than derived, because this is an enum
            // constant; PremiumBenefitsTest asserts every locked check appears, so locking a fourth
            // without mentioning it fails rather than quietly overpromising.
            "Three more checks: Fingers at once, Vibration, Wi-Fi and Bluetooth",
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
    /**
     * Prices exactly as Play states them, keyed by product id.
     *
     * Play is the only authoritative source: prices vary by country, tax and promotion, and a hardcoded
     * figure disagreeing with the checkout sheet is a policy problem as well as a support burden. The
     * constants on [PremiumPlan] are a fallback for when Play has not answered yet.
     */
    val playPrices: Map<String, String> = emptyMap(),
    /**
     * Products the user has started paying for and Play has not settled.
     *
     * Not a rare case: UPI and net-banking are ordinary in India and routinely sit pending for minutes.
     * Without showing it, someone who has just paid sees "Free trial" and reasonably concludes the payment
     * failed — and the usual response to that is to pay a second time.
     */
    val pendingProductIds: List<String> = emptyList(),
) {
    /** What to print on a plan card: Play's price when known, the built-in one until then. */
    fun priceOf(plan: PremiumPlan): String = playPrices[plan.productId] ?: plan.price

    fun isPending(plan: PremiumPlan): Boolean = plan.productId in pendingProductIds

    /** Only products actually on sale can be bought, and only when Play answered. */
    fun isPurchasable(plan: PremiumPlan): Boolean =
        billingAvailable && isOnSale(plan) && ownedPlan != plan

    /**
     * Whether this tier is offered at all.
     *
     * Distinct from [isPurchasable] because the two mean different things to a reader. "Unavailable" is
     * about the app not being able to take payments; a tier that is deliberately not sold yet is a
     * decision, and saying "unavailable" for it invites someone to keep tapping and wondering.
     */
    fun isOnSale(plan: PremiumPlan): Boolean = plan.productId in BillingProducts.onSale

    /**
     * The plans worth putting on the screen: whatever is for sale, plus whatever this install already owns.
     *
     * Every plan used to be drawn, which meant a dealer saw a Shop card with a firm ₹999 on it, five
     * appealing lines, and then "Not on sale yet". A price beside something that cannot be bought is a small
     * broken promise, and it was sitting on the one screen that asks for money.
     *
     * Shop is not for sale because of how Play purchases work rather than because it is unfinished. A
     * purchase belongs to a Google account, and this app runs on the phone being inspected — which is the
     * customer's phone, signed in to the customer's account. A shop would have to sign into every customer's
     * handset for their own purchase to be recognised. Selling that would be selling something that does not
     * work.
     *
     * Owned plans stay visible so that anyone who somehow holds a Shop entitlement still sees their status
     * rather than a screen that has forgotten about them.
     */
    val visiblePlans: List<PremiumPlan>
        get() = PremiumPlan.entries.filter { isOnSale(it) || ownedPlan == it }
}
