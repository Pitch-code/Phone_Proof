package com.phoneproof.app

import com.google.common.truth.Truth.assertThat
import com.phoneproof.feature.home.HomeCatalogue
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
            Routes.MULTI_TOUCH,
            Routes.SCREEN_PATTERNS,
            Routes.AUDIO,
            Routes.CAMERA,
            Routes.VOLUME_BUTTONS,
            Routes.SENSORS,
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
        assertThat(RunPlan.stepIds).doesNotContain(Routes.CHECKS)
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


/**
 * The same guard for Home's list of checks.
 *
 * [com.phoneproof.feature.home.HomeCatalogue] holds a route string per row so that the screenshot test
 * and the navigation graph can read one list instead of two — the previous arrangement had them written
 * out separately, and the test's copy silently fell five entries behind the real screen. Holding the
 * route as an opaque string is what keeps `feature:home` free of navigation, and this is what makes that
 * safe.
 */
class HomeCatalogueRoutesTest {

    @Test
    fun every_row_on_home_goes_somewhere_real() {
        val known = setOf(
            Routes.SCAN,
            Routes.LOCK,
            Routes.TOUCH,
            Routes.MULTI_TOUCH,
            Routes.SCREEN_PATTERNS,
            Routes.AUDIO,
            Routes.CAMERA,
            Routes.VOLUME_BUTTONS,
            Routes.SENSORS,
            Routes.CLAIMS,
            Routes.IMEI,
        )

        assertThat(HomeCatalogue.map { it.route }).containsExactlyElementsIn(known)
    }

    @Test
    fun no_row_is_listed_twice() {
        // Two rows sharing a route means one of them is a mistake, and on Home a duplicate reads as the
        // app not knowing what it offers.
        val routes = HomeCatalogue.map { it.route }
        assertThat(routes).hasSize(routes.toSet().size)
    }

    @Test
    fun the_instant_scan_is_offered_first() {
        // It is the one that needs no instructions and returns in seconds, so it is the row a buyer who
        // ignores the big button should meet first.
        assertThat(HomeCatalogue.first().route).isEqualTo(Routes.SCAN)
    }

    @Test
    fun every_row_says_what_it_is_for() {
        HomeCatalogue.forEach {
            assertThat(it.title).isNotEmpty()
            assertThat(it.subtitle).isNotEmpty()
        }
    }
}
