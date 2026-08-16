package com.phoneproof.app

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.run.RunPlan
import com.phoneproof.core.run.StepEffort
import org.junit.Test

/**
 * Ties the guided run to the navigation graph.
 *
 * A [com.phoneproof.core.run.RunStep] stores the route of the screen that performs it, so the
 * checklist can navigate without a second id-to-route table that someone has to remember to update.
 * The cost of that shortcut is that a renamed route or a mistyped step id produces a step which
 * silently goes nowhere — the buyer taps "Touch response" during a run and nothing happens.
 *
 * `:core:run` cannot see these constants, and it should not: it has no business knowing about
 * navigation. So the assertion lives here, in the module that owns both facts.
 */
class RunPlanRoutesTest {

    @Test
    fun every_step_of_the_run_names_a_real_destination() {
        assertThat(RunPlan.stepIds).containsExactly(
            Routes.SCAN,
            Routes.TOUCH,
            Routes.SCREEN_PATTERNS,
            Routes.AUDIO,
            Routes.CAMERA,
            Routes.CLAIMS,
            Routes.IMEI,
            Routes.GUIDE,
        ).inOrder()
    }

    @Test
    fun the_run_itself_is_not_one_of_its_own_steps() {
        // A step pointing at the checklist would be a loop the buyer cannot get out of.
        assertThat(RunPlan.stepIds).doesNotContain(Routes.RUN)
        assertThat(RunPlan.stepIds).doesNotContain(Routes.VERDICT)
    }

    @Test
    fun no_step_takes_a_navigation_argument() {
        // Routes.REPORT_DETAIL is templated. A step id containing a placeholder would navigate to a
        // literal "{reportId}" and crash rather than fail visibly.
        RunPlan.stepIds.forEach { assertThat(it).doesNotContain("{") }
    }

    @Test
    fun the_only_step_the_app_cannot_measure_is_the_walkthrough() {
        // The run marks LOOK_YOURSELF steps as shown when they are opened, since it has no way to
        // check what the buyer saw. If a second step ever took that route, that leniency would start
        // applying to something the app could have measured properly.
        val unmeasurable = RunPlan.steps
            .filter { it.effort == StepEffort.LOOK_YOURSELF }
            .map { it.id }
        assertThat(unmeasurable).containsExactly(Routes.GUIDE)
    }
}
