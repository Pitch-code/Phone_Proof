package com.phoneproof.core.run

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import org.junit.Test

class RunSessionTest {

    @Test
    fun nothing_is_recorded_before_a_run_is_started() {
        val session = RunSession()
        session.record("scan", listOf(pass("hardware.storage")))

        // A buyer poking at one check from Home must not silently begin a run, or the verdict screen
        // would eventually appear describing a phone they were only curious about.
        assertThat(session.state.value.active).isFalse()
        assertThat(session.state.value.allResults).isEmpty()
    }

    @Test
    fun starting_a_run_discards_the_previous_phone() {
        val session = RunSession()
        session.start()
        session.record("scan", listOf(fail("security.root")))
        session.start()

        assertThat(session.state.value.allResults).isEmpty()
        assertThat(session.state.value.settledCount).isEqualTo(0)
    }

    @Test
    fun an_empty_emission_does_not_tick_a_step_off() {
        val session = RunSession()
        session.start()
        // Every feature's state starts with no results and the navigation layer forwards it on every
        // change, so this arrives for all eight steps before the buyer has done anything.
        RunPlan.stepIds.forEach { session.record(it, emptyList()) }

        assertThat(session.state.value.doneCount).isEqualTo(0)
        assertThat(session.state.value.nextStep!!.id).isEqualTo("scan")
    }

    @Test
    fun a_step_id_that_is_not_in_the_plan_is_ignored() {
        val session = RunSession()
        session.start()
        session.record("typo", listOf(pass("hardware.storage")))
        session.skip("also-a-typo")

        assertThat(session.state.value.statuses).isEmpty()
        assertThat(session.state.value.results).isEmpty()
    }

    @Test
    fun recording_marks_the_step_done_and_keeps_its_findings() {
        val session = RunSession()
        session.start()
        session.record("touch", listOf(fail("screen.touch_coverage")))

        val state = session.state.value
        assertThat(state.statusOf("touch")).isEqualTo(RunStepStatus.DONE)
        assertThat(state.resultsOf("touch")).hasSize(1)
    }

    @Test
    fun re_running_a_step_replaces_its_findings_rather_than_adding_to_them() {
        val session = RunSession()
        session.start()
        session.record("touch", listOf(fail("screen.touch_coverage")))
        // The buyer wiped a greasy screen and ran it again. The report must not now contain both the
        // failure and the pass, which would show a fault the phone does not have.
        session.record("touch", listOf(pass("screen.touch_coverage")))

        assertThat(session.state.value.resultsOf("touch")).containsExactly(
            pass("screen.touch_coverage"),
        )
    }

    @Test
    fun a_skipped_step_can_still_be_done_afterwards() {
        val session = RunSession()
        session.start()
        session.skip("audio")
        assertThat(session.state.value.statusOf("audio")).isEqualTo(RunStepStatus.SKIPPED)

        session.record("audio", listOf(pass("hardware.microphone")))
        assertThat(session.state.value.statusOf("audio")).isEqualTo(RunStepStatus.DONE)
        assertThat(session.state.value.skippedCount).isEqualTo(0)
    }

    @Test
    fun marking_done_works_for_a_step_that_measures_nothing() {
        val session = RunSession()
        session.start()
        session.markDone("guide")

        assertThat(session.state.value.statusOf("guide")).isEqualTo(RunStepStatus.DONE)
        assertThat(session.state.value.resultsOf("guide")).isEmpty()
    }

    @Test
    fun the_next_step_is_the_first_one_not_yet_dealt_with() {
        val session = RunSession()
        session.start()
        session.record("scan", listOf(pass("hardware.storage")))
        session.skip("touch")

        // Reads the plan rather than naming a step, so inserting a new one does not make this assertion
        // wrong — it is testing that "next" skips what is settled, not what the third step happens to be.
        val expected = RunPlan.stepIds.first { it != "scan" && it != "touch" }
        assertThat(session.state.value.nextStep!!.id).isEqualTo(expected)
    }

    @Test
    fun progress_counts_skipped_steps_as_dealt_with() {
        val state = runWith(
            results = mapOf("scan" to listOf(pass("hardware.storage"))),
            skipped = listOf("touch"),
        )

        assertThat(state.settledCount).isEqualTo(2)
        assertThat(state.doneCount).isEqualTo(1)
        assertThat(state.skippedCount).isEqualTo(1)
        assertThat(state.progress).isWithin(0.001f).of(2f / RunPlan.steps.size)
        assertThat(state.isFinished).isFalse()
    }

    @Test
    fun a_run_is_finished_once_every_step_is_done_or_skipped() {
        val session = RunSession()
        session.start()
        RunPlan.stepIds.forEach(session::skip)

        assertThat(session.state.value.isFinished).isTrue()
        assertThat(session.state.value.nextStep).isNull()
        assertThat(session.state.value.remainingSeconds).isEqualTo(0)
    }

    @Test
    fun findings_come_out_in_run_order_however_the_buyer_jumped_around() {
        val session = RunSession()
        session.start()
        session.record("camera", listOf(pass("hardware.camera")))
        session.record("scan", listOf(pass("hardware.storage")))
        session.record("audio", listOf(pass("hardware.microphone")))

        assertThat(session.state.value.allResults.map { it.id })
            .containsExactly("hardware.storage", "hardware.microphone", "hardware.camera")
            .inOrder()
    }

    @Test
    fun remaining_time_shrinks_as_steps_are_dealt_with() {
        val session = RunSession()
        session.start()
        val before = session.state.value.remainingSeconds
        session.record("scan", listOf(pass("hardware.storage")))

        assertThat(session.state.value.remainingSeconds)
            .isEqualTo(before - RunPlan.step("scan")!!.typicalSeconds)
    }

    @Test
    fun reset_puts_the_session_back_to_dormant() {
        val session = RunSession()
        session.start()
        session.record("scan", listOf(pass("hardware.storage")))
        session.reset()

        assertThat(session.state.value.active).isFalse()
        assertThat(session.state.value.allResults).isEmpty()
    }

    @Test
    fun the_start_time_is_recorded_so_a_saved_report_can_be_dated() {
        val session = RunSession(now = { 1_700_000_000_000L })
        session.start()

        assertThat(session.state.value.startedAtEpochMs).isEqualTo(1_700_000_000_000L)
    }
}


/** The badge the checklist puts against a finished step. */
class RunStateOutcomeTest {

    @Test
    fun a_step_that_measured_nothing_has_no_badge() {
        val state = runWith(done = listOf("guide"))

        assertThat(state.worstOutcomeOf("guide")).isNull()
        assertThat(state.worstOutcomeOf("audio")).isNull()
    }

    @Test
    fun the_worst_finding_is_the_one_shown() {
        val state = runWith(
            results = mapOf(
                "scan" to listOf(pass("a"), unknown("b"), caution("c"), fail("d")),
            ),
        )

        assertThat(state.worstOutcomeOf("scan")).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun could_not_tell_outranks_passed_so_a_gap_never_looks_clean() {
        val state = runWith(results = mapOf("scan" to listOf(pass("a"), unknown("b"))))

        assertThat(state.worstOutcomeOf("scan")).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun all_clear_shows_a_pass() {
        val state = runWith(results = mapOf("scan" to listOf(pass("a"), pass("b"))))

        assertThat(state.worstOutcomeOf("scan")).isEqualTo(CheckOutcome.PASS)
    }
}
