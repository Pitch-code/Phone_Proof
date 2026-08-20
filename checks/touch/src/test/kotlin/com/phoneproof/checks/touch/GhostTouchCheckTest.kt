package com.phoneproof.checks.touch

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

/**
 * A screen that taps itself.
 *
 * The rules worth pinning are all about **not overclaiming in either direction**. This check can only report
 * what did not happen while it was looking, and the fault it hunts is intermittent — so a quiet watch is weak
 * evidence and must never be dressed up as a clean bill of health.
 */
class GhostTouchCheckTest {

    private fun watch(
        millis: Long = GhostTouchCheck.FULL_WATCH_MILLIS,
        contacts: List<GhostTouchCheck.Contact> = emptyList(),
        handsOff: Boolean = true,
    ) = GhostTouchCheck.Watch(millis, contacts, handsOff)

    private fun contact(at: Long, x: Float = 0.2f, y: Float = 0.2f) =
        GhostTouchCheck.Contact(at, x, y)

    @Test
    fun a_quiet_full_watch_passes_but_only_with_low_confidence() {
        // The most important assertion here. Nothing arrived, so it passes — but this fault comes and goes,
        // and a seller who knows about it can simply wait the test out. HIGH confidence would be a lie that
        // reassures a buyer into a phone that types by itself.
        val result = GhostTouchCheck.evaluate(watch())

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun a_watch_cut_short_says_it_cannot_tell_even_when_nothing_arrived() {
        // A quiet four seconds is not evidence. Reporting it as a pass would be the most reassuring lie this
        // app could tell, because the buyer would stop looking.
        val result = GhostTouchCheck.evaluate(watch(millis = 4_000))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("long enough")
    }

    @Test
    fun one_phantom_touch_is_a_fail_rather_than_something_to_haggle_over() {
        // Not CAUTION. A screen that enters PINs and answers calls in a pocket is not a discount, and framing
        // it as one would push a buyer towards a phone they will hate.
        val result = GhostTouchCheck.evaluate(watch(contacts = listOf(contact(1_000))))

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun a_flurry_from_one_bad_spot_counts_as_one_touch() {
        // A single ghost touch arrives as several reports. Counting each would turn one fault into "four
        // touches nobody made" — and that inflated number is what the buyer repeats to the seller, so being
        // caught exaggerating costs more than the finding is worth.
        val flurry = listOf(contact(1_000), contact(1_080), contact(1_150), contact(1_300))

        assertThat(GhostTouchCheck.distinctEvents(flurry)).hasSize(1)
        assertThat(GhostTouchCheck.evaluate(watch(contacts = flurry)).headline)
            .isEqualTo("The screen registered a touch nobody made")
    }

    @Test
    fun touches_well_apart_in_time_are_counted_separately() {
        val spread = listOf(contact(1_000), contact(9_000), contact(20_000))

        assertThat(GhostTouchCheck.distinctEvents(spread)).hasSize(3)
        assertThat(GhostTouchCheck.evaluate(watch(contacts = spread)).headline).contains("3 touches")
    }

    @Test
    fun contacts_arriving_out_of_order_are_still_grouped_correctly() {
        // Pointer events are not guaranteed to be handed over in order once they have been through a queue,
        // and an unsorted grouping would split one flurry into several.
        val jumbled = listOf(contact(1_300), contact(1_000), contact(1_150), contact(1_080))

        assertThat(GhostTouchCheck.distinctEvents(jumbled)).hasSize(1)
    }

    @Test
    fun an_unconfirmed_watch_reports_the_finding_without_condemning_the_phone() {
        // Someone resting a thumb on the edge produces exactly this evidence. Hiding the finding would waste
        // it, so it is reported — as a CAUTION.
        //
        // Not a judgement call: CheckResult forbids a LOW confidence FAIL, because a shaky negative costs the
        // buyer the deal or the seller the price. My first version tried exactly that and the model's own
        // invariant rejected it, which is the guardrail working.
        val result = GhostTouchCheck.evaluate(
            watch(contacts = listOf(contact(1_000)), handsOff = false),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
        assertThat(result.consequence).contains("resting thumb")
        // And it says how to get a trustworthy answer rather than leaving them with a maybe.
        assertThat(result.consequence).contains("flat on a table")
    }

    @Test
    fun a_shaky_finding_is_never_reported_as_a_failure() {
        // The invariant itself, stated where a future change to this check would break it. Every combination
        // must satisfy it, not just the one case that happened to be written first.
        listOf(true, false).forEach { handsOff ->
            listOf(0L, 4_000L, 12_000L, GhostTouchCheck.FULL_WATCH_MILLIS).forEach { millis ->
                val result = GhostTouchCheck.evaluate(
                    watch(millis = millis, contacts = listOf(contact(1_000)), handsOff = handsOff),
                )
                assertThat(result.outcome == CheckOutcome.FAIL && result.confidence == Confidence.LOW)
                    .isFalse()
            }
        }
    }

    @Test
    fun a_repeating_spot_is_described_where_a_buyer_can_press_it() {
        // The more useful finding: it points at a specific injury rather than a generally unwell panel, and
        // the buyer can press that spot to see for themselves.
        val sameSpot = listOf(contact(1_000, 0.1f, 0.1f), contact(9_000, 0.2f, 0.2f))

        assertThat(GhostTouchCheck.evaluate(watch(contacts = sameSpot)).action)
            .contains("top left")
    }

    @Test
    fun touches_from_all_over_are_described_as_such() {
        val scattered = listOf(contact(1_000, 0.1f, 0.1f), contact(9_000, 0.9f, 0.9f))

        assertThat(GhostTouchCheck.evaluate(watch(contacts = scattered)).action)
            .contains("different parts")
    }

    @Test
    fun a_failure_always_offers_reasons_it_might_not_be_the_phone() {
        // Required by CheckResult for anything that is not a plain pass, and the list is real rather than
        // decorative: a bad charger genuinely makes some panels report touches that never happened.
        val result = GhostTouchCheck.evaluate(watch(contacts = listOf(contact(1_000))))

        assertThat(result.falsePositiveCauses).isNotEmpty()
        assertThat(result.falsePositiveCauses.joinToString(" ")).contains("charger")
    }

    @Test
    fun no_verdict_here_names_a_price() {
        // The rule from NoInventedPricesTest, asserted where a new check is most likely to break it.
        listOf(
            GhostTouchCheck.evaluate(watch()),
            GhostTouchCheck.evaluate(watch(millis = 4_000)),
            GhostTouchCheck.evaluate(watch(contacts = listOf(contact(1_000)))),
        ).forEach { result ->
            val prose = "${result.headline} ${result.consequence} ${result.action}"
            assertThat(prose).doesNotContain("₹")
            assertThat(prose).doesNotMatch(""".*\b\d[\d,]*\s+off\b.*""")
        }
    }

    @Test
    fun the_watch_records_how_long_it_actually_ran() {
        // A buyer will cut this short, because the seller wants the phone back. A verdict from twelve seconds
        // must not read like one from thirty, so the number is reported rather than assumed.
        val result = GhostTouchCheck.evaluate(watch(millis = 12_000))

        assertThat(result.measurements.first { it.label == "Watched for" }.value).isEqualTo("12")
    }
}
