package com.phoneproof.feature.settings

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.preferences.PaidChecks
import org.junit.Test

/**
 * What the Premium card promises.
 *
 * Every line here is printed on the screen that asks for ₹99, which makes a stale one worse than a bug: it
 * is a thing somebody paid for and did not receive.
 *
 * This file exists because that happened. The list opened with **"No ads, anywhere"** — selling the absence
 * of something that never existed, since there is no advertising code anywhere in this app and never has
 * been. It survived because nothing checked it, and because two code comments asserted the opposite loudly
 * enough to be believed.
 */
class PremiumBenefitsTest {

    private val premium = PremiumPlan.PREMIUM.benefits
    private val prose = premium.joinToString(" ")

    @Test
    fun nothing_is_offered_about_ads() {
        // There is no ads SDK in this project. A paid tier cannot remove them, and offering to is a
        // promise that cannot be kept. Also inconsistent with the Play declaration, which says no ads.
        assertThat(prose.lowercase()).doesNotContain("ads")
        assertThat(prose.lowercase()).doesNotContain("advert")
    }

    @Test
    fun every_check_the_trial_leaves_out_is_offered_here() {
        // The genuinely missing benefit, and the reason the list had room. Read from PaidChecks so that
        // locking a fourth check without mentioning it on the payment screen fails here rather than
        // quietly overpromising — the same ratchet FreeTrialLinesTest applies to the free card.
        PaidChecks.routes.forEach { route ->
            val title = PaidChecks.copyFor(route)!!.title
            assertThat(prose).contains(title)
        }
    }

    @Test
    fun the_promises_that_are_kept_are_still_made() {
        // Removing a false claim must not quietly take the true ones with it.
        val lower = prose.lowercase()
        assertThat(lower).contains("report")
        assertThat(lower).contains("pdf")
        assertThat(lower).contains("compare")
        // And the thing buyers of a one-time purchase most want to hear.
        assertThat(lower).contains("not a subscription")
    }

    @Test
    fun no_benefit_names_a_price() {
        // Prices belong to Play, which localises them. A number typed here is wrong everywhere it was not
        // typed for, and wrong everywhere the moment the Console changes.
        assertThat(prose).doesNotContain("₹")
        assertThat(prose).doesNotContain("99")
    }

    @Test
    fun the_shop_tier_still_describes_itself_honestly() {
        // Not offered for sale, but the constant still exists and is shown to anyone who owns it.
        val shop = PremiumPlan.SHOP.benefits.joinToString(" ").lowercase()
        assertThat(shop).doesNotContain("ads")
        assertThat(shop).isNotEmpty()
    }
}
