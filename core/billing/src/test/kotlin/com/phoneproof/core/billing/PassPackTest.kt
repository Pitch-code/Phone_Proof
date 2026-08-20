package com.phoneproof.core.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * That buying passes does **not** upgrade the phone that bought them.
 *
 * This is the single decision in the pass model most likely to be "corrected" by someone tidying up, because
 * on the surface it looks like an omission: a paid product that maps to no entitlement.
 *
 * It is not an omission. A pack yields a **code**, and the code unlocks whichever handset it is typed into —
 * because the phone being inspected belongs to the *seller*, and the buyer's Google account is not signed in
 * on it. That mismatch is the entire reason passes exist rather than a second tier.
 *
 * Mapping `PASSES_5` to `PREMIUM` would compile, pass every other test, and quietly deliver the opposite of
 * what the buyer paid for: their own phone upgraded, and the phone they are about to inspect still locked.
 */
class PassPackTest {

    @Test
    fun a_pass_pack_grants_no_tier_on_the_phone_that_bought_it() {
        assertThat(BillingProducts.entitlementFor(BillingProducts.PASSES_5)).isNull()
    }

    @Test
    fun it_is_a_pack_rather_than_a_tier() {
        assertThat(BillingProducts.packs).contains(BillingProducts.PASSES_5)
        // And it is not in the list of things that grant a tier, which is what `recognised` means.
        assertThat(BillingProducts.recognised).doesNotContain(BillingProducts.PASSES_5)
    }

    @Test
    fun nothing_in_packs_maps_to_an_entitlement() {
        // Stated as a property so a second pack added later inherits the rule instead of rediscovering it.
        BillingProducts.packs.forEach { id ->
            assertThat(BillingProducts.entitlementFor(id)).isNull()
        }
    }

    @Test
    fun the_id_matches_what_the_console_will_hold() {
        // A typo here is not a crash: it is a purchase Play completes and the app never recognises, which
        // takes money and grants nothing. Pinned so it cannot drift from the Play Console entry.
        assertThat(BillingProducts.PASSES_5).isEqualTo("phoneproof_passes_5")
    }

    @Test
    fun tiers_and_packs_do_not_overlap() {
        assertThat(BillingProducts.packs.intersect(BillingProducts.recognised.toSet())).isEmpty()
    }
}
