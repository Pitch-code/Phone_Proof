package com.phoneproof.checks.touch

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class MultiTouchCheckTest {

    @Test
    fun five_tracked_on_a_phone_claiming_five_passes() {
        val result = MultiTouchCheck.evaluate(maxObserved = 5, claimedPoints = 5)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun a_phone_that_honestly_claims_two_and_delivers_two_is_working_as_sold() {
        // The bar is the phone's own claim, never a number this app decided on. Failing a budget handset
        // for not being a flagship would be the app inventing a defect out of a spec sheet it disagrees
        // with.
        val result = MultiTouchCheck.evaluate(maxObserved = 2, claimedPoints = 2)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("what this phone claims to support")
    }

    @Test
    fun beating_the_claim_is_still_a_pass() {
        // JAZZHAND promises "five or more", so a screen that manages seven has exceeded its own spec.
        val result = MultiTouchCheck.evaluate(maxObserved = 7, claimedPoints = 5)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.measurements.first { it.label == "Most fingers tracked" }.value)
            .isEqualTo("7")
    }

    @Test
    fun a_phone_that_claims_nothing_is_held_to_the_five_a_hand_has() {
        assertThat(MultiTouchCheck.evaluate(maxObserved = 5, claimedPoints = null).outcome)
            .isEqualTo(CheckOutcome.PASS)
        assertThat(MultiTouchCheck.evaluate(maxObserved = 3, claimedPoints = null).outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun an_untouched_screen_reports_nothing_rather_than_a_fault() {
        val result = MultiTouchCheck.evaluate(maxObserved = 0, claimedPoints = 5)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("Nothing was placed on the screen")
        assertThat(result.action).isNotEmpty()
    }

    // ------------------------------------------------------------------ the honest gap

    @Test
    fun falling_short_is_a_question_before_it_is_ever_a_finding() {
        // There is no second sensor here to vouch for the buyer, unlike the gyroscope and the accelerometer.
        // Three points measured is indistinguishable from three fingers used, so the app has to ask.
        val result = MultiTouchCheck.evaluate(maxObserved = 3, claimedPoints = 5)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.action).contains("one finger at a time")
    }

    @Test
    fun a_buyer_who_admits_they_used_fewer_fingers_settles_nothing() {
        val result = MultiTouchCheck.evaluate(
            maxObserved = 3,
            claimedPoints = 5,
            fingersDown = FingersDown.FEWER,
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("proves nothing about the screen")
    }

    @Test
    fun all_five_down_and_only_three_tracked_is_a_caution_never_a_failure() {
        val result = MultiTouchCheck.evaluate(
            maxObserved = 3,
            claimedPoints = 5,
            fingersDown = FingersDown.ALL_OF_THEM,
        )

        // Merged fingers, palm rejection and screen protectors are all common enough that a confident
        // failure here would accuse working phones.
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.falsePositiveCauses).isNotEmpty()
    }

    @Test
    fun the_consequence_names_gaming_and_typing_and_admits_scrolling_feels_fine() {
        val result = MultiTouchCheck.evaluate(
            maxObserved = 3,
            claimedPoints = 5,
            fingersDown = FingersDown.ALL_OF_THEM,
        )

        // The last part is what makes this check worth having: everyday use feels normal, which is exactly
        // why the fault survives a shop demonstration.
        assertThat(result.consequence).contains("typing")
        assertThat(result.consequence).contains("scrolling will feel")
    }

    @Test
    fun the_buyers_answer_cannot_overturn_a_measurement_that_already_passed() {
        val result = MultiTouchCheck.evaluate(
            maxObserved = 5,
            claimedPoints = 5,
            fingersDown = FingersDown.FEWER,
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun it_is_a_different_check_from_touch_coverage() {
        // Dead areas and dead capacity are different faults with different prices. A screen can respond
        // across every millimetre of glass and still lose the fourth finger.
        assertThat(MultiTouchCheck.CHECK_ID).isEqualTo("screen.multi_touch")
        assertThat(MultiTouchCheck.CHECK_ID).startsWith("screen.")
    }

    @Test
    fun every_outcome_tells_the_buyer_what_to_do_next() {
        listOf(
            MultiTouchCheck.evaluate(0, 5),
            MultiTouchCheck.evaluate(3, 5),
            MultiTouchCheck.evaluate(3, 5, FingersDown.FEWER),
            MultiTouchCheck.evaluate(3, 5, FingersDown.ALL_OF_THEM),
        ).forEach { assertThat(it.action).isNotEmpty() }
    }

    @Test
    fun the_report_always_shows_the_claim_next_to_the_measurement() {
        // The claim is the accuser here, so it has to be in the report — a buyer arguing about this needs
        // the phone's own spec sheet in front of them.
        val labels = MultiTouchCheck.evaluate(3, 5, FingersDown.ALL_OF_THEM).measurements.map { it.label }

        assertThat(labels).containsExactly("Most fingers tracked", "This phone claims").inOrder()
    }

    @Test
    fun a_phone_saying_nothing_about_its_capability_is_described_that_way() {
        val value = MultiTouchCheck.evaluate(5, null).measurements
            .first { it.label == "This phone claims" }.value

        assertThat(value).isEqualTo("nothing specific")
    }
}
