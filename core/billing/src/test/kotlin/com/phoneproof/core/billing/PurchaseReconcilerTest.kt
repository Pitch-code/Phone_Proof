package com.phoneproof.core.billing

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.preferences.Entitlement
import org.junit.Test

/**
 * Every rule that decides who has paid for what.
 *
 * These are tested exhaustively and without Android because **the Play interaction itself cannot be
 * verified anywhere in this project**: a purchase needs a real device signed into a real Google account,
 * with the app distributed through a Play track. Everything that can be checked off-device therefore
 * should be, and that is precisely the decision logic — who gets which tier, what must be acknowledged,
 * and what a failure is allowed to change.
 */
class PurchaseReconcilerTest {

    private fun purchase(
        productId: String = BillingProducts.PREMIUM,
        state: PurchaseState = PurchaseState.PURCHASED,
        acknowledged: Boolean = true,
        token: String = "token-1",
    ) = PurchaseRecord(listOf(productId), state, acknowledged, token)

    private fun succeeded(vararg purchases: PurchaseRecord) =
        PurchaseReconciler.reconcile(PurchaseQuery.Succeeded(purchases.toList()))

    // ------------------------------------------------------- a failure must never take anything away

    @Test
    fun a_failed_query_changes_nothing() {
        // The most important case in this file. Entitlement is recomputed on every cold start so that a
        // refund removes access — and that same mechanism, applied to a query that failed because the
        // phone was in a tunnel, would remove access from someone who had paid.
        val result = PurchaseReconciler.reconcile(PurchaseQuery.Failed("no connection"))

        assertThat(result.entitlement).isNull()
        assertThat(result.toAcknowledge).isEmpty()
        assertThat(result.pendingProductIds).isEmpty()
    }

    @Test
    fun a_successful_query_with_nothing_owned_does_take_it_away() {
        // Not the same case, and it must not be conflated: Play answered, and the answer was that this
        // account owns nothing. A refund, a chargeback or a different account all land here.
        val result = succeeded()

        assertThat(result.entitlement).isEqualTo(Entitlement.FREE)
    }

    // ------------------------------------------------------------------------ what grants what

    @Test
    fun a_completed_premium_purchase_grants_premium() {
        assertThat(succeeded(purchase()).entitlement).isEqualTo(Entitlement.PREMIUM)
    }

    @Test
    fun a_pending_purchase_grants_nothing_at_all() {
        // UPI and net-banking sit pending for minutes, and the temptation is to grant access optimistically
        // so the screen feels responsive. That is giving away a paid feature for a payment that may never
        // arrive.
        val result = succeeded(purchase(state = PurchaseState.PENDING, acknowledged = false))

        assertThat(result.entitlement).isEqualTo(Entitlement.FREE)
        assertThat(result.toAcknowledge).isEmpty()
    }

    @Test
    fun a_pending_purchase_is_reported_so_the_screen_can_explain_itself() {
        // Without this the buyer sees "Free trial" immediately after paying, which looks like the payment
        // failed — and the usual response to that is to pay again.
        val result = succeeded(purchase(state = PurchaseState.PENDING, acknowledged = false))

        assertThat(result.pendingProductIds).containsExactly(BillingProducts.PREMIUM)
    }

    @Test
    fun an_unspecified_state_is_treated_exactly_like_pending() {
        // Play does not know. Neither does the app, so it grants nothing and says it is waiting.
        val result = succeeded(purchase(state = PurchaseState.UNSPECIFIED, acknowledged = false))

        assertThat(result.entitlement).isEqualTo(Entitlement.FREE)
        assertThat(result.pendingProductIds).containsExactly(BillingProducts.PREMIUM)
    }

    @Test
    fun the_highest_tier_wins_when_more_than_one_is_owned() {
        // Otherwise the tier depends on the order Play happened to return the purchases in, and someone
        // who bought both would be demoted at random.
        val result = succeeded(
            purchase(productId = BillingProducts.PREMIUM, token = "a"),
            purchase(productId = BillingProducts.SHOP_YEARLY, token = "b"),
        )

        assertThat(result.entitlement).isEqualTo(Entitlement.SHOP)
    }

    @Test
    fun order_does_not_change_the_answer() {
        val forwards = succeeded(
            purchase(productId = BillingProducts.PREMIUM, token = "a"),
            purchase(productId = BillingProducts.SHOP_YEARLY, token = "b"),
        )
        val backwards = succeeded(
            purchase(productId = BillingProducts.SHOP_YEARLY, token = "b"),
            purchase(productId = BillingProducts.PREMIUM, token = "a"),
        )

        assertThat(forwards.entitlement).isEqualTo(backwards.entitlement)
    }

    @Test
    fun a_product_this_app_does_not_sell_is_ignored() {
        // Defends against a typo in the Play Console and against anything else appearing on the account.
        val result = succeeded(purchase(productId = "some_other_app_product"))

        assertThat(result.entitlement).isEqualTo(Entitlement.FREE)
        assertThat(result.pendingProductIds).isEmpty()
    }

    // --------------------------------------------------------------- acknowledgement, or Play refunds it

    @Test
    fun a_completed_purchase_that_has_not_been_acknowledged_is_returned_for_acknowledgement() {
        // Three days and Play refunds it automatically. The user pays, gets access, then loses both.
        val unacknowledged = purchase(acknowledged = false)

        val result = succeeded(unacknowledged)

        assertThat(result.toAcknowledge).containsExactly(unacknowledged)
    }

    @Test
    fun an_already_acknowledged_purchase_is_not_acknowledged_twice() {
        assertThat(succeeded(purchase(acknowledged = true)).toAcknowledge).isEmpty()
    }

    @Test
    fun access_is_granted_alongside_acknowledgement_not_after_it() {
        // The entitlement must not wait for the acknowledgement round trip to succeed. If it did, a user
        // who paid while offline-ish would be told they had not.
        val result = succeeded(purchase(acknowledged = false))

        assertThat(result.entitlement).isEqualTo(Entitlement.PREMIUM)
        assertThat(result.toAcknowledge).hasSize(1)
    }

    @Test
    fun every_unacknowledged_purchase_is_returned_not_only_the_first() {
        val result = succeeded(
            purchase(productId = BillingProducts.PREMIUM, acknowledged = false, token = "a"),
            purchase(productId = BillingProducts.SHOP_YEARLY, acknowledged = false, token = "b"),
        )

        assertThat(result.toAcknowledge.map { it.purchaseToken }).containsExactly("a", "b")
    }

    // --------------------------------------------------------------------------------- the product table

    @Test
    fun only_premium_is_on_sale_but_both_tiers_are_honoured() {
        // The shop tier is configured and not sold; see monetisation.md. If it is ever sold and later
        // withdrawn, whoever bought it keeps it — an entitlement that evaporates on delisting would be
        // taking back something paid for.
        assertThat(BillingProducts.onSale).containsExactly(BillingProducts.PREMIUM)
        assertThat(BillingProducts.recognised)
            .containsExactly(BillingProducts.PREMIUM, BillingProducts.SHOP_YEARLY)
    }

    @Test
    fun every_recognised_product_maps_to_a_tier_and_every_paid_tier_has_a_product() {
        // Both directions. A product with no tier takes money and grants nothing; a paid tier with no
        // product can never be bought.
        BillingProducts.recognised.forEach {
            assertThat(BillingProducts.entitlementFor(it)).isNotNull()
        }

        val mapped = BillingProducts.recognised.mapNotNull(BillingProducts::entitlementFor)
        assertThat(mapped).containsExactly(Entitlement.PREMIUM, Entitlement.SHOP)
    }

    @Test
    fun the_product_ids_are_the_ones_configured_in_the_play_console() {
        // Pinned as literals. These strings have to match the Console exactly, and a rename here that is
        // not mirrored there produces a purchase the app never recognises.
        assertThat(BillingProducts.PREMIUM).isEqualTo("phoneproof_premium")
        assertThat(BillingProducts.SHOP_YEARLY).isEqualTo("phoneproof_shop_yearly")
    }

    @Test
    fun the_one_time_product_is_not_treated_as_a_subscription() {
        // They are queried through different Play APIs, so getting this wrong means the purchase is never
        // found at all.
        assertThat(BillingProducts.isSubscription(BillingProducts.PREMIUM)).isFalse()
        assertThat(BillingProducts.isSubscription(BillingProducts.SHOP_YEARLY)).isTrue()
    }
}
