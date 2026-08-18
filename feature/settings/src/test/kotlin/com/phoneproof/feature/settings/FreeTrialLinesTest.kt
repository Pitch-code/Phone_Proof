package com.phoneproof.feature.settings

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.PaidChecks
import org.junit.Test

/**
 * What the free-trial card claims.
 *
 * Every sentence here appears on the screen that asks for money, which makes a stale one worse than a bug: a
 * limit a buyer would have accepted becomes a deception they discover for themselves two taps later.
 *
 * This card promised "Every check runs in full — nothing is watered down" for some time after three measured
 * checks had been removed from the trial. Nothing failed, nothing crashed, and no test could have noticed,
 * because the claim lived in a `listOf` inside a composable and was only ever seen in a screenshot.
 */
class FreeTrialLinesTest {

    private val lines = freeTrialLines()

    private val included = lines.filter { it.first }.map { it.second }
    private val excluded = lines.filterNot { it.first }.map { it.second }

    @Test
    fun the_card_does_not_claim_every_check_is_included() {
        // The exact stale promise, and any close relative of it.
        val text = included.joinToString(" ").lowercase()

        assertThat(text).doesNotContain("every check")
        assertThat(text).doesNotContain("all checks")
    }

    @Test
    fun every_check_the_trial_leaves_out_is_named_as_excluded() {
        // Read from PaidChecks, so locking a fourth check without mentioning it here fails rather than
        // quietly leaving the payment screen overpromising.
        val excludedText = excluded.joinToString(" ")

        PaidChecks.routes.forEach { route ->
            val title = PaidChecks.copyFor(route)!!.title
            assertThat(excludedText).contains(title)
        }
    }

    @Test
    fun nothing_is_both_offered_and_withheld() {
        // A name appearing on both sides of the list would be worse than either alone.
        val includedText = included.joinToString(" ")

        PaidChecks.routes.forEach { route ->
            assertThat(includedText).doesNotContain(PaidChecks.copyFor(route)!!.title)
        }
    }

    @Test
    fun the_scan_allowance_comes_from_the_entitlement_rather_than_a_typed_number() {
        // A hardcoded "2" here would survive changing the allowance and then lie about it.
        assertThat(included.any { it.contains(Entitlement.FREE_SCAN_LIMIT.toString()) }).isTrue()
    }

    @Test
    fun the_promise_that_is_kept_is_still_made() {
        // Removing the false claim must not remove the true one with it. The app never degrades a
        // measurement to sell an upgrade, and that is worth saying out loud on this card.
        assertThat(included.joinToString(" ").lowercase()).contains("watered-down")
    }

    @Test
    fun both_halves_of_the_list_are_populated() {
        // Guards the test itself: an empty half would make every assertion above pass for the wrong reason.
        assertThat(included).isNotEmpty()
        assertThat(excluded).isNotEmpty()
    }
}
