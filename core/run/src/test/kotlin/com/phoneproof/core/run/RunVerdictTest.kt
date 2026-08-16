package com.phoneproof.core.run

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

/**
 * The grading rules, which are the most consequential logic in the app: this is what decides whether
 * someone hands over eighteen thousand rupees.
 */
class RunVerdictTest {

    private fun verdict(
        results: Map<String, List<com.phoneproof.core.model.CheckResult>> = emptyMap(),
        skipped: List<String> = emptyList(),
        done: List<String> = emptyList(),
    ) = RunVerdict.of(runWith(results = results, skipped = skipped, done = done))

    // ---------------------------------------------------------------- clean and incomplete

    @Test
    fun a_run_that_measured_nothing_is_incomplete_not_clean() {
        val result = verdict()

        assertThat(result.grade).isEqualTo(RunGrade.INCOMPLETE)
        assertThat(result.headline).isEqualTo("Not enough tested to say")
    }

    @Test
    fun every_essential_step_passing_is_a_clean_bill() {
        val result = verdict(results = allEssentialsPassing())

        assertThat(result.grade).isEqualTo(RunGrade.LOOKS_GOOD)
        assertThat(result.problems).isEmpty()
        assertThat(result.passCount).isEqualTo(RunPlan.steps.count { it.essential })
    }

    @Test
    fun a_clean_bill_still_says_the_app_cannot_see_a_bent_frame() {
        val result = verdict(results = allEssentialsPassing())

        // The single most likely way this app misleads someone: a green verdict read as "this phone is
        // fine" when the whole physical-damage category was never in scope.
        assertThat(result.detail).contains("your own eyes")
    }

    @Test
    fun skipping_an_essential_step_forfeits_a_clean_bill() {
        val result = verdict(
            results = allEssentialsPassing() - "audio",
            skipped = listOf("audio"),
        )

        assertThat(result.grade).isEqualTo(RunGrade.INCOMPLETE)
        // Named from the step's own title rather than a copy of it, so renaming the step cannot make this
        // assertion quietly stop checking anything. It caught the earpiece rename that produced it.
        assertThat(result.detail).contains(RunPlan.step("audio")!!.title.lowercase())
        assertThat(result.detail).contains("A phone is not clean because nobody looked.")
        assertThat(result.skipped.map { it.id }).containsExactly("audio")
    }

    @Test
    fun skipping_the_optional_steps_does_not_forfeit_a_clean_bill() {
        // Two of these three are behind the paywall, so a free-trial buyer skips them by having no
        // choice. Their verdict must still be able to come back clean.
        val result = verdict(
            results = allEssentialsPassing(),
            skipped = listOf("claims", "imei", "guide"),
        )

        assertThat(result.grade).isEqualTo(RunGrade.LOOKS_GOOD)
    }

    @Test
    fun unknowns_are_listed_as_gaps_rather_than_counted_as_passes() {
        val result = verdict(
            results = allEssentialsPassing() +
                mapOf("scan" to listOf(pass("hardware.storage"), unknown("hardware.battery"))),
        )

        assertThat(result.grade).isEqualTo(RunGrade.LOOKS_GOOD)
        assertThat(result.unknownCount).isEqualTo(1)
        assertThat(result.detail).contains("gaps rather than good news")
    }

    // ---------------------------------------------------------------- negotiate

    @Test
    fun one_fault_is_a_negotiation() {
        val result = verdict(
            results = allEssentialsPassing() +
                mapOf("touch" to listOf(fail("screen.touch_coverage", "Touch response"))),
        )

        assertThat(result.grade).isEqualTo(RunGrade.NEGOTIATE)
        assertThat(result.headline).isEqualTo("Worth having, but not at the asking price")
    }

    @Test
    fun a_caution_on_its_own_is_still_a_negotiation() {
        val result = verdict(
            results = allEssentialsPassing() +
                mapOf("scan" to listOf(caution("hardware.battery", "Battery"))),
        )

        assertThat(result.grade).isEqualTo(RunGrade.NEGOTIATE)
        assertThat(result.problemCount).isEqualTo(1)
    }

    @Test
    fun two_faults_are_still_worth_haggling_over() {
        val result = verdict(
            results = allEssentialsPassing() + mapOf(
                "touch" to listOf(fail("screen.touch_coverage")),
                "camera" to listOf(fail("hardware.camera")),
            ),
        )

        assertThat(result.grade).isEqualTo(RunGrade.NEGOTIATE)
    }

    @Test
    fun faults_outrank_incompleteness_so_a_finding_is_never_buried() {
        // Barely anything was measured, but what was measured failed. Reporting "not enough tested to
        // say" here would hide a real fault behind a procedural complaint.
        val result = verdict(results = mapOf("touch" to listOf(fail("screen.touch_coverage"))))

        assertThat(result.grade).isEqualTo(RunGrade.NEGOTIATE)
        assertThat(result.unmeasuredEssentials).isNotEmpty()
    }

    // ---------------------------------------------------------------- walk away

    @Test
    fun three_faults_stop_being_a_negotiation() {
        val result = verdict(
            results = allEssentialsPassing() + mapOf(
                "touch" to listOf(fail("screen.touch_coverage")),
                "camera" to listOf(fail("hardware.camera")),
                "audio" to listOf(fail("hardware.speaker")),
            ),
        )

        assertThat(result.grade).isEqualTo(RunGrade.WALK_AWAY)
        assertThat(result.detail).contains("3 separate faults")
    }

    @Test
    fun a_lender_still_holding_the_phone_ends_it_whatever_else_passed() {
        val result = verdict(
            results = allEssentialsPassing() +
                mapOf("scan" to listOf(fail("security.device_admin_lock", "Remote lock"))),
        )

        assertThat(result.grade).isEqualTo(RunGrade.WALK_AWAY)
        assertThat(result.detail).contains("This is not about the price")
        assertThat(result.detail).contains("lock this phone remotely")
    }

    @Test
    fun root_ends_it_because_it_invalidates_everything_that_passed() {
        val result = verdict(
            results = allEssentialsPassing() +
                mapOf("scan" to listOf(fail("security.root", "Root"))),
        )

        assertThat(result.grade).isEqualTo(RunGrade.WALK_AWAY)
        assertThat(result.detail).contains("including the parts that passed")
    }

    @Test
    fun a_dishonest_build_ends_it() {
        val result = verdict(
            results = allEssentialsPassing() +
                mapOf("scan" to listOf(fail("software.build_integrity", "Build"))),
        )

        assertThat(result.grade).isEqualTo(RunGrade.WALK_AWAY)
    }

    @Test
    fun a_failed_imei_checksum_never_reaches_walk_away() {
        // A mistyped digit must not be able to accuse a seller of handling a stolen phone, which is
        // why that check only ever reports CAUTION. This asserts the two decisions agree.
        val result = verdict(
            results = allEssentialsPassing() +
                mapOf("imei" to listOf(caution("security.imei_checksum", "IMEI checksum"))),
        )

        assertThat(result.grade).isEqualTo(RunGrade.NEGOTIATE)
    }

    // ---------------------------------------------------------------- ordering and talking points

    @Test
    fun failures_are_listed_before_cautions_and_the_confident_ones_first() {
        val result = verdict(
            results = mapOf(
                "scan" to listOf(
                    caution("hardware.battery", confidence = Confidence.LOW),
                    fail("hardware.storage", confidence = Confidence.HIGH),
                ),
                "touch" to listOf(caution("screen.touch_coverage", confidence = Confidence.MEDIUM)),
                "camera" to listOf(fail("hardware.camera", confidence = Confidence.MEDIUM)),
            ),
        )

        assertThat(result.problems.map { it.id }).containsExactly(
            "hardware.storage",
            "hardware.camera",
            "screen.touch_coverage",
            "hardware.battery",
        ).inOrder()
    }

    @Test
    fun every_fault_produces_something_to_say_to_the_seller() {
        val result = verdict(
            results = mapOf(
                "touch" to listOf(
                    fail("screen.touch_coverage", "Touch response", action = "Get 2,000 off"),
                ),
                "scan" to listOf(caution("hardware.battery", "Battery", action = "Ask its age")),
            ),
        )

        // One line per fault, in the same worst-first order as the problem list, so reading down the
        // screen and reading out the argument are the same act.
        assertThat(result.talkingPoints).hasSize(2)
        assertThat(result.talkingPoints.first().finding).isEqualTo("Touch response")
        assertThat(result.talkingPoints.first().sayThis).isEqualTo("Get 2,000 off")
        assertThat(result.talkingPoints.map { it.finding })
            .isEqualTo(result.problems.map { it.title })
    }

    @Test
    fun a_clean_run_has_nothing_to_argue_about() {
        assertThat(verdict(results = allEssentialsPassing()).talkingPoints).isEmpty()
    }

    @Test
    fun the_three_result_buckets_between_them_hold_everything_measured() {
        val state = runWith(
            results = mapOf(
                "scan" to listOf(pass("a"), unknown("b"), caution("c")),
                "touch" to listOf(fail("d")),
            ),
        )
        val result = RunVerdict.of(state)

        assertThat(result.problemCount + result.passCount + result.unknownCount)
            .isEqualTo(state.allResults.size)
        assertThat(result.couldNotTell.single().outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }
}
