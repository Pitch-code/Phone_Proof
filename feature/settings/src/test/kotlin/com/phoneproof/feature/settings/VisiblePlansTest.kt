package com.phoneproof.feature.settings

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.billing.BillingProducts
import com.phoneproof.core.preferences.Entitlement
import org.junit.Test

/**
 * Which plans the payment screen is allowed to show.
 *
 * The rule is one sentence — show what can be bought, plus what is already owned — and it is worth pinning
 * because breaking it is silent. Nothing crashes when a price appears beside something unbuyable; a dealer
 * simply reads ₹999, five appealing lines and "Not on sale yet", and forms an opinion about the app.
 */
class VisiblePlansTest {

    @Test
    fun the_shop_tier_is_not_offered_to_someone_who_does_not_own_it() {
        // Not for sale because of how Play purchases work, not because it is unfinished: a purchase belongs
        // to a Google account, and this app runs on the phone being inspected — the customer's phone, signed
        // in to the customer's account. A shop would have to sign into every handset they test.
        val state = SettingsUiState(entitlement = Entitlement.FREE)

        assertThat(state.visiblePlans).containsExactly(PremiumPlan.PREMIUM)
    }

    @Test
    fun premium_is_offered_because_it_is_the_thing_actually_on_sale() {
        assertThat(SettingsUiState().visiblePlans).contains(PremiumPlan.PREMIUM)
        assertThat(PremiumPlan.PREMIUM.productId).isEqualTo(BillingProducts.PREMIUM)
    }

    @Test
    fun a_shop_owner_still_sees_their_own_plan() {
        // Anyone holding a shop entitlement must not open Settings and find the app has forgotten about it.
        val state = SettingsUiState(
            entitlement = Entitlement.SHOP,
            ownedPlan = PremiumPlan.SHOP,
        )

        assertThat(state.visiblePlans).contains(PremiumPlan.SHOP)
    }

    @Test
    fun every_visible_plan_can_be_bought_or_is_already_owned() {
        // The property itself, stated once. Any future plan added to the enum has to satisfy this or say why.
        listOf(
            SettingsUiState(),
            SettingsUiState(entitlement = Entitlement.PREMIUM, ownedPlan = PremiumPlan.PREMIUM),
            SettingsUiState(entitlement = Entitlement.SHOP, ownedPlan = PremiumPlan.SHOP),
        ).forEach { state ->
            state.visiblePlans.forEach { plan ->
                assertThat(state.isOnSale(plan) || state.ownedPlan == plan).isTrue()
            }
        }
    }

    @Test
    fun no_visible_plan_shows_a_price_it_cannot_take_money_for() {
        // The exact fault being removed: a card that names a price and then refuses.
        val state = SettingsUiState(entitlement = Entitlement.FREE)

        state.visiblePlans.forEach { plan ->
            assertThat(state.isOnSale(plan)).isTrue()
        }
    }
}
