package com.phoneproof.core.preferences

/**
 * What this install has paid for.
 *
 * One source of truth, so the gate on a paid feature is a single question asked in one place rather
 * than a scattering of booleans that drift apart.
 *
 * Nothing here talks to Play Billing yet, and the honest consequence is that this value comes from
 * local storage. That is fine for a gate whose only job today is to decide what to show, and it must
 * be replaced by a verified purchase before anything is actually sold — a locally stored entitlement
 * is trivially editable by anyone with a rooted phone, which is precisely the audience this app
 * attracts.
 */
enum class Entitlement {
    /** The free trial: every measurement, but a limited number of scans. See [FREE_SCAN_LIMIT]. */
    FREE,
    PREMIUM,
    SHOP,
    ;

    /** Keeping every report, PDF export and side-by-side comparison. */
    val hasPremiumExtras: Boolean get() = this != FREE

    /**
     * Whether scans are unlimited.
     *
     * The free trial gets [FREE_SCAN_LIMIT] and then stops. That is a deliberate reversal of this
     * project's earlier position that scanning would always be unlimited, made by the product owner:
     * a trial that measures everything forever gives nobody a reason to pay. Recorded in
     * .kiro/steering/monetisation.md so it is not quietly reverted later.
     */
    val hasUnlimitedScans: Boolean get() = this != FREE

    /** Claimed against measured, and the by-hand guide. Extras rather than measurements. */
    val hasAdvisoryTools: Boolean get() = this != FREE

    /** Branding a report with a shop's own name and logo. */
    val hasShopBranding: Boolean get() = this == SHOP

    val label: String
        get() = when (this) {
            FREE -> "Free trial"
            PREMIUM -> "Premium"
            SHOP -> "Shop"
        }

    companion object {
        /**
         * Scans a free-trial install gets before it stops.
         *
         * Two, set by the product owner. One place only, so the number in the button, the number in
         * the block message and the number actually enforced cannot drift apart — three copies of a
         * limit is how an app ends up promising two and giving one.
         */
        const val FREE_SCAN_LIMIT: Int = 2
    }
}
