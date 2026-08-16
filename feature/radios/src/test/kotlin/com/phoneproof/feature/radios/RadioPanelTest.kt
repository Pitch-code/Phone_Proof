package com.phoneproof.feature.radios

import com.google.common.truth.Truth.assertThat
import com.phoneproof.checks.radios.RadioKind
import com.phoneproof.checks.radios.RadioObservation
import com.phoneproof.core.model.CheckOutcome
import org.junit.Test

/**
 * The gate in front of this check's only negative verdict.
 *
 * A CAUTION here requires the buyer to say they flipped the switch, so the rules about when that question is
 * asked — and when the answer stops applying — are the difference between a fair finding and an invented one.
 */
class RadioPanelTest {

    private fun panel(
        enabled: Boolean = false,
        present: Boolean = true,
        stateReadable: Boolean = true,
        visitedSettings: Boolean = false,
        enableClaim: Boolean? = null,
    ) = RadioPanel(
        kind = RadioKind.BLUETOOTH,
        observation = RadioObservation(
            kind = RadioKind.BLUETOOTH,
            present = present,
            stateReadable = stateReadable,
            enabled = enabled,
        ),
        visitedSettings = visitedSettings,
        enableClaim = enableClaim,
    )

    @Test
    fun nobody_is_asked_anything_before_they_have_been_sent_to_settings() {
        // A screen that opened with "did you switch it on?" would be asking about something that never
        // happened, and a yes would then convict the phone on nothing.
        assertThat(panel().asking).isFalse()
    }

    @Test
    fun the_question_appears_only_when_settings_left_the_radio_off() {
        assertThat(panel(visitedSettings = true).asking).isTrue()
    }

    @Test
    fun a_radio_that_came_on_is_never_questioned() {
        // The app can see this for itself, so asking would be noise — and a stray yes would contradict a
        // working radio.
        assertThat(panel(enabled = true, visitedSettings = true).asking).isFalse()
    }

    @Test
    fun the_question_is_asked_once_and_then_stays_answered() {
        for (answer in listOf(true, false)) {
            assertThat(panel(visitedSettings = true, enableClaim = answer).asking).isFalse()
        }
    }

    @Test
    fun absent_or_unreadable_hardware_is_never_questioned() {
        // Both are already conclusive in their own way, and neither is something the buyer can act on by
        // flipping a switch.
        assertThat(panel(present = false, visitedSettings = true).asking).isFalse()
        assertThat(panel(stateReadable = false, visitedSettings = true).asking).isFalse()
    }

    @Test
    fun only_an_explicit_yes_produces_a_negative_verdict() {
        assertThat(panel(visitedSettings = true, enableClaim = true).result.outcome)
            .isEqualTo(CheckOutcome.CAUTION)

        // Everything else about this radio is identical; the buyer's answer is the whole difference.
        assertThat(panel(visitedSettings = true, enableClaim = false).result.outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(panel(visitedSettings = true, enableClaim = null).result.outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun a_working_radio_ignores_a_stale_yes() {
        // Belt and braces alongside the ViewModel clearing the claim: even if a yes survived a radio coming
        // on, the verdict must follow the measurement rather than the answer.
        assertThat(panel(enabled = true, visitedSettings = true, enableClaim = true).result.outcome)
            .isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun both_panels_of_a_fresh_state_report_under_different_ids() {
        val state = RadiosUiState(
            wifi = RadioPanel(
                kind = RadioKind.WIFI,
                observation = RadioObservation(kind = RadioKind.WIFI, present = true, enabled = true, associated = true),
            ),
            bluetooth = panel(enabled = true),
        )

        assertThat(state.results.map { it.id }).containsNoDuplicates()
        assertThat(state.results).hasSize(2)
        assertThat(state.allProved).isTrue()
    }

    @Test
    fun all_proved_is_false_while_anything_is_still_unknown() {
        val state = RadiosUiState(
            wifi = RadioPanel(
                kind = RadioKind.WIFI,
                // On but not joined: the case that must not count as proved.
                observation = RadioObservation(kind = RadioKind.WIFI, present = true, enabled = true),
            ),
            bluetooth = panel(enabled = true),
        )

        assertThat(state.allProved).isFalse()
    }
}
