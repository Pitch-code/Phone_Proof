package com.phoneproof.core.billing

import com.phoneproof.core.preferences.Entitlement

/**
 * The Play product ids, and what each one grants.
 *
 * These strings must match the products configured in the Play Console exactly. A typo here is not a
 * crash — it is a purchase Play completes and the app never recognises, which takes money and grants
 * nothing. So they live in one place, and the tests below assert the mapping in both directions.
 */
object BillingProducts {

    /** One-time purchase. The only thing on sale; see `monetisation.md`. */
    const val PREMIUM: String = "phoneproof_premium"

    /**
     * A yearly subscription for shops, configured but **not on sale**.
     *
     * Deliberate, and recorded in `monetisation.md`: entitlement is tied to a Google account, and a shop
     * inspects other people's phones, where their own account is not signed in. Selling them a tier they
     * could not use on the handset in their hand would be taking money for something that does not work.
     * The mapping exists so a purchase made in future is honoured; nothing offers it yet.
     */
    const val SHOP_YEARLY: String = "phoneproof_shop_yearly"

    /** Products the app actually offers for sale today. */
    val onSale: List<String> = listOf(PREMIUM)

    /**
     * Everything the app will honour if Play reports it as owned.
     *
     * Wider than [onSale] on purpose. If the shop tier is ever sold and later withdrawn, the people who
     * bought it must keep it — an entitlement that evaporates because a product was delisted would be
     * taking something back that was paid for.
     */
    val recognised: List<String> = listOf(PREMIUM, SHOP_YEARLY)

    /** Null for anything this app does not sell, so an unexpected id is ignored rather than trusted. */
    fun entitlementFor(productId: String): Entitlement? = when (productId) {
        PREMIUM -> Entitlement.PREMIUM
        SHOP_YEARLY -> Entitlement.SHOP
        else -> null
    }

    /** Whether a product is bought outright or renews, which decides how Play is queried for it. */
    fun isSubscription(productId: String): Boolean = productId == SHOP_YEARLY
}
