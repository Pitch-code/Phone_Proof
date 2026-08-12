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
    FREE,
    PREMIUM,
    SHOP,
    ;

    /** Keeping every report, PDF export and side-by-side comparison. */
    val hasPremiumExtras: Boolean get() = this != FREE

    /** Branding a report with a shop's own name and logo. */
    val hasShopBranding: Boolean get() = this == SHOP

    val label: String
        get() = when (this) {
            FREE -> "Free"
            PREMIUM -> "Premium"
            SHOP -> "Shop"
        }
}
