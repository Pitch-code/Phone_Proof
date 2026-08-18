package com.phoneproof.core.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which checks the free trial leaves out, and — more importantly — which it must never leave out.
 *
 * The rule they were chosen by: **lock what a buyer wants to see, never what protects them.** A locked row
 * creates a wish to pay through completeness, which is honest. Withholding the test that would have caught
 * an expensive fault creates it through fear, which is not — and if someone bought a phone with a dead
 * earpiece because this app hid the earpiece test behind ₹99, the app would have done more harm than not
 * existing at all.
 *
 * The list below is therefore pinned in both directions. The locked set is asserted exactly, and every
 * protective check is asserted to be free by name, so adding one to the paid set fails here with a test
 * name that says why it is wrong.
 */
class PaidChecksTest {

    @Test
    fun exactly_three_checks_are_locked() {
        // Pinned as a set, so both adding and removing one is a deliberate act with a failing test.
        assertThat(PaidChecks.routes).containsExactly("multi-touch", "radios", "vibration")
    }

    @Test
    fun every_locked_check_says_how_to_get_the_answer_without_paying() {
        // The property that makes these three safe to lock, and the one thing this file has to enforce
        // rather than describe:
        //  - multi-touch: a spec, not a fault. Dead patches are found by the free touch test.
        //  - radios: ten seconds with the two toggles.
        //  - vibration: a finger on the back of the phone.
        //
        // This test used to assert `routes.hasSize(3)` under this name, which checked nothing of the sort —
        // it would have passed just as happily with the earpiece test locked and no way out written down.
        // Now that the wording lives beside the decision, the claim in the name is the claim being made.
        PaidChecks.routes.forEach { route ->
            val copy = PaidChecks.copyFor(route)
            assertThat(copy).isNotNull()

            // Long enough to be an actual instruction. A placeholder like "Upgrade." would satisfy a
            // null check and defeat the entire point of having the field.
            assertThat(copy!!.doItYourself.length).isGreaterThan(40)
            assertThat(copy.whatItFinds.length).isGreaterThan(40)
        }
    }

    @Test
    fun a_locked_check_cannot_exist_without_wording() {
        // `routes` is derived from the copy map, so this is structural rather than a convention someone has
        // to remember. The failure it prevents is specific and ugly: a row marked Premium that opens onto a
        // paywall with an empty body, asking for money and saying nothing.
        assertThat(PaidChecks.routes.mapNotNull { PaidChecks.copyFor(it) })
            .hasSize(PaidChecks.routes.size)
    }

    @Test
    fun no_paywall_promises_a_refund_window_or_denies_one() {
        // Same rule PurchaseTermsTest holds for the terms screen, applied to the three screens that ask for
        // money first. The developer cannot make "no refunds" true — Google refunds at its own discretion —
        // and Play requires the disclosure to be accurate, so neither claim may appear here.
        // The false claims only. The bare word is not banned: a truthful pointer to the terms screen would
        // be fine here, and a test that forbade it would be enforcing silence rather than accuracy.
        val forbidden = listOf("non-refundable", "no refunds", "all sales final")

        PaidChecks.routes.forEach { route ->
            val copy = PaidChecks.copyFor(route)!!
            val prose = "${copy.explanation} ${copy.whatUnlockingGives}".lowercase()
            forbidden.forEach { claim ->
                assertThat(prose).doesNotContain(claim)
            }
        }
    }

    @Test
    fun no_paywall_names_a_price() {
        // Prices belong to Play, which localises and changes them. A number typed into a paywall is wrong
        // in every country it was not typed for, and wrong everywhere the moment the Console changes.
        PaidChecks.routes.forEach { route ->
            val copy = PaidChecks.copyFor(route)!!
            val prose = "${copy.explanation} ${copy.whatUnlockingGives}"
            assertThat(prose).doesNotContain("₹")
            assertThat(prose).doesNotContain("Rs")
        }
    }

    @Test
    fun the_unlock_sentence_offers_more_than_the_screen_being_unblocked() {
        // "Pay to see this screen" is a toll. The tier has to be worth buying on its own terms, so every
        // paywall names what else comes with it.
        PaidChecks.routes.forEach { route ->
            val gives = PaidChecks.copyFor(route)!!.whatUnlockingGives
            assertThat(gives).contains("report")
        }
    }

    @Test
    fun the_checks_that_find_expensive_faults_are_all_free() {
        // Each of these finds something a buyer cannot establish by hand in a shop, and each would be a
        // costly thing to miss. If one of these ever appears in the paid set, this is the test that says so.
        val mustStayFree = listOf(
            "charging",       // a loose socket charges while you watch and gives up overnight
            "lock",           // a lender who can brick the phone after you have paid
            "imei",           // whether it is stolen
            "touch",          // dead strips on the glass
            "screen-patterns", // dead pixels and burn-in
            "audio",          // a phone that cannot take a call
            "camera",         // among the first three reasons anyone buys a phone
            "sensors",        // a dead proximity sensor ends calls against a cheek
            "storage-speed",  // recycled flash: full size, terrible quality
            "volume-buttons", // self-testable, so locking it would be pettiness rather than a model
            "scan",           // the parts list itself
        )

        mustStayFree.forEach { route ->
            assertThat(PaidChecks.requiresPremium(route)).isFalse()
        }
    }

    @Test
    fun the_touch_test_stays_free_even_though_multi_touch_is_locked() {
        // The pair that makes the multi-touch lock defensible. Locking the interesting one is fine only
        // because the one that finds the actual fault is still there.
        assertThat(PaidChecks.requiresPremium("multi-touch")).isTrue()
        assertThat(PaidChecks.requiresPremium("touch")).isFalse()
    }

    @Test
    fun a_free_trial_cannot_open_a_locked_check() {
        PaidChecks.routes.forEach { route ->
            assertThat(PaidChecks.isUnlocked(route, Entitlement.FREE)).isFalse()
        }
    }

    @Test
    fun both_paid_tiers_can_open_everything() {
        listOf(Entitlement.PREMIUM, Entitlement.SHOP).forEach { tier ->
            PaidChecks.routes.forEach { route ->
                assertThat(PaidChecks.isUnlocked(route, tier)).isTrue()
            }
        }
    }

    @Test
    fun a_free_trial_can_open_everything_else() {
        // The limit is three named checks and the scan allowance. Not a general lock on measuring.
        listOf("charging", "touch", "audio", "camera", "sensors", "storage-speed", "imei").forEach {
            assertThat(PaidChecks.isUnlocked(it, Entitlement.FREE)).isTrue()
        }
    }

    @Test
    fun an_unknown_route_is_never_treated_as_paid() {
        // A screen added later is free until somebody decides otherwise, which is the safe default: the
        // failure mode is a check given away, not a buyer blocked from something they were promised.
        assertThat(PaidChecks.requiresPremium("some-new-check")).isFalse()
        assertThat(PaidChecks.isUnlocked("some-new-check", Entitlement.FREE)).isTrue()
    }
}
