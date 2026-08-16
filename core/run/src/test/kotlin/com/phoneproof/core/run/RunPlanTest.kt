package com.phoneproof.core.run

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the order itself.
 *
 * The order of the run is a product decision that took longer to settle than the code implementing
 * it, and it is exactly the kind of thing a later refactor reorders by accident while "tidying up the
 * list". These tests fail when the reasoning in [RunPlan]'s documentation stops matching the list
 * underneath it.
 */
class RunPlanTest {

    @Test
    fun the_automatic_scan_comes_first_because_it_needs_nothing_from_anybody() {
        assertThat(RunPlan.steps.first().id).isEqualTo("scan")
        assertThat(RunPlan.steps.first().effort).isEqualTo(StepEffort.AUTOMATIC)
    }

    @Test
    fun the_walkthrough_is_last_because_the_app_measures_nothing_in_it() {
        assertThat(RunPlan.steps.last().id).isEqualTo("guide")
        assertThat(RunPlan.steps.last().effort).isEqualTo(StepEffort.LOOK_YOURSELF)
    }

    @Test
    fun nothing_is_asked_of_the_seller_until_the_phone_has_been_measured() {
        val firstSellerQuestion = RunPlan.steps.indexOfFirst {
            it.effort == StepEffort.ASK_THE_SELLER
        }
        val lastMeasurement = RunPlan.steps.indexOfLast {
            it.effort == StepEffort.AUTOMATIC || it.effort == StepEffort.HANDS_ON
        }
        assertThat(firstSellerQuestion).isGreaterThan(lastMeasurement)
    }

    @Test
    fun the_two_screen_tests_are_adjacent_so_quiet_is_only_asked_for_once() {
        val touch = RunPlan.stepIds.indexOf("touch")
        val patterns = RunPlan.stepIds.indexOf("screen-patterns")
        assertThat(patterns - touch).isEqualTo(1)
    }

    @Test
    fun both_screen_tests_declare_that_a_notification_banner_ruins_them() {
        listOf("touch", "screen-patterns").forEach { id ->
            assertThat(RunPlan.step(id)!!.needs).contains(RunCondition.NO_INTERRUPTIONS)
        }
    }

    @Test
    fun the_audio_step_is_the_only_one_that_needs_quiet() {
        val needQuiet = RunPlan.steps.filter { RunCondition.QUIET in it.needs }.map { it.id }
        assertThat(needQuiet).containsExactly("audio")
    }

    /**
     * The constraint that decided which steps are essential. A free-trial buyer cannot open claims or
     * the walkthrough at all, so if either gated a clean verdict the app would be reporting
     * "incomplete" as a way of selling a subscription.
     */
    @Test
    fun no_paywalled_step_is_required_for_a_clean_verdict() {
        val paywalled = setOf("claims", "guide")
        paywalled.forEach { id ->
            assertThat(RunPlan.step(id)!!.essential).isFalse()
        }
    }

    @Test
    fun asking_the_seller_is_never_essential_because_they_can_always_refuse() {
        RunPlan.steps.filter { it.effort == StepEffort.ASK_THE_SELLER }.forEach {
            assertThat(it.essential).isFalse()
        }
    }

    @Test
    fun every_step_says_why_it_matters() {
        RunPlan.steps.forEach {
            assertThat(it.why).isNotEmpty()
            assertThat(it.title).isNotEmpty()
        }
    }

    @Test
    fun the_run_announces_a_believable_length() {
        assertThat(RunPlan.typicalMinutes).isAtLeast(5)
        assertThat(RunPlan.typicalMinutes).isAtMost(15)
        // The buyer in a hurry has to be able to do meaningfully less than everything.
        assertThat(RunPlan.essentialMinutes).isLessThan(RunPlan.typicalMinutes)
    }

    @Test
    fun step_lookup_does_not_invent_steps() {
        assertThat(RunPlan.step("no-such-step")).isNull()
        assertThat(RunPlan.step("scan")).isNotNull()
    }
}
