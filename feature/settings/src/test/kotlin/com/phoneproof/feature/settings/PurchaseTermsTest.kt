package com.phoneproof.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the purchase copy must say, and the one thing it must never say.
 *
 * The product owner asked for a no-refunds policy stated at the point of purchase. The first half is
 * done — who takes the money and who can give it back is now said before anyone pays. The second half
 * was declined, and this test is why it stays declined:
 *
 *  - **The developer cannot make it true.** Google refunds Play purchases at its own discretion, and a
 *    buyer can also charge back through their bank. An app announcing "no refunds" is describing a
 *    policy it has no power to enforce.
 *  - **Play requires accurate disclosures.** Misstating refund terms is a review risk on the one screen
 *    where a rejection costs the most.
 *  - **It would be a false statement where this app can least afford one.** The same reasoning shaped the
 *    privacy line on Home — which names what stays rather than claiming nothing leaves — and forced
 *    `ADVISORY_TRIAL_EXCLUSION` to name the real reason a screen is locked. A paywall that misstates
 *    its own terms is exactly the pattern those decisions exist to prevent.
 *
 *    (This paragraph used to justify that line by saying ads mean an advertising ID leaves the device.
 *    That was never true — there is no advertising code in this app — and it is corrected here because a
 *    false premise repeated in two files starts being treated as a fact about the product.)
 *
 * If a future product decision wants this revisited, revisit it deliberately — do not let it arrive as
 * a tightening of some wording.
 */
class PurchaseTermsTest {

    @Test
    fun the_terms_say_who_takes_the_money() {
        // The commonest support question about a paid app is "how do I get my money back", and the
        // answer is not the developer. Better said before the purchase than discovered after it.
        assertThat(PURCHASE_TERMS).contains("Google Play")
    }

    @Test
    fun the_terms_say_the_app_cannot_refund_and_who_can() {
        // True, not a dodge: there is no server, no merchant account and no payment relationship here.
        // Only Google can reverse a Google payment, so that is where the request has to go.
        assertThat(PURCHASE_TERMS).contains("cannot take or return")
        assertThat(PURCHASE_TERMS).contains("Google's decision")
    }

    @Test
    fun the_terms_say_a_refund_removes_the_features() {
        // A description rather than a threat: entitlement is recomputed from Play on every launch, so
        // this is simply what happens. Saying so avoids someone expecting to keep both.
        assertThat(PURCHASE_TERMS).contains("switch off again")
    }

    @Test
    fun the_terms_make_clear_it_is_not_a_subscription() {
        assertThat(PURCHASE_TERMS).contains("One payment")
    }

    @Test
    fun the_terms_never_claim_purchases_are_non_refundable() {
        // The guard. Google will refund regardless of what this app says, so any of these phrases would
        // be false — and false on the screen that asks for money.
        val forbidden = listOf(
            "non-refundable",
            "no refund",
            "no refunds",
            "not refundable",
            "all sales are final",
            "all sales final",
            "cannot be refunded",
            "no returns",
        )

        val terms = PURCHASE_TERMS.lowercase()
        forbidden.forEach { phrase ->
            assertThat(terms).doesNotContain(phrase)
        }
    }

    @Test
    fun the_terms_do_not_quote_a_refund_window() {
        // Google's self-service refund window has changed before and is Google's to change again. A
        // number here would be a promise about someone else's policy, and would rot silently.
        listOf("48 hour", "48 hours", "48-hour", "two days", "24 hour").forEach {
            assertThat(PURCHASE_TERMS.lowercase()).doesNotContain(it)
        }
    }

    @Test
    fun the_terms_are_short_enough_to_actually_be_read() {
        // Terms nobody reads protect nobody. This sits under the plan cards, where a wall of text would
        // simply be scrolled past, and the whole point is that it is read before paying.
        assertThat(PURCHASE_TERMS.length).isLessThan(280)
    }
}
