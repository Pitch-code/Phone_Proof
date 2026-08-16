package com.phoneproof.checks.buttons

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class VolumeButtonCheckTest {

    private val untouched = ButtonObservation()
    private fun pressed(times: Int = 1) =
        ButtonObservation(presses = times, releases = times, longestHoldMillis = 120L)

    private fun jammed() = ButtonObservation(
        presses = 1,
        releases = 0,
        longestHoldMillis = VolumeButtonCheck.STUCK_HOLD_MILLIS + 500L,
    )

    @Test
    fun both_buttons_pressed_and_released_passes() {
        val result = VolumeButtonCheck.evaluate(pressed(), pressed())

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    // ------------------------------------------------------------------ the witness

    @Test
    fun neither_button_heard_is_never_reported_as_two_dead_buttons() {
        // The most important test here. If the app has failed to hook the volume keys, both buttons look
        // dead — and blaming the phone for a bug in this software is the one outcome that must not happen.
        listOf(PressedBoth.NOT_ASKED, PressedBoth.YES, PressedBoth.NO).forEach { answer ->
            val result = VolumeButtonCheck.evaluate(untouched, untouched, answer)

            assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
            assertThat(result.headline).contains("may be the app's fault")
        }
    }

    @Test
    fun one_button_working_is_what_makes_the_others_silence_mean_anything() {
        val result = VolumeButtonCheck.evaluate(
            up = pressed(),
            down = untouched,
            pressedBoth = PressedBoth.YES,
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.headline).contains("volume down never registered")
    }

    @Test
    fun it_names_whichever_button_was_silent() {
        val upSilent = VolumeButtonCheck.evaluate(untouched, pressed(), PressedBoth.YES)
        assertThat(upSilent.headline).contains("volume up never registered")

        val downSilent = VolumeButtonCheck.evaluate(pressed(), untouched, PressedBoth.YES)
        assertThat(downSilent.headline).contains("volume down never registered")
    }

    @Test
    fun a_button_not_pressed_yet_is_a_prompt_rather_than_a_finding() {
        val result = VolumeButtonCheck.evaluate(pressed(), untouched)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.action).contains("Press the volume down button")
    }

    @Test
    fun a_buyer_who_admits_they_never_pressed_it_settles_nothing() {
        val result = VolumeButtonCheck.evaluate(pressed(), untouched, PressedBoth.NO)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("never pressed")
    }

    // ------------------------------------------------------------------ jammed

    @Test
    fun a_key_held_down_and_never_released_is_reported_as_jammed() {
        val result = VolumeButtonCheck.evaluate(up = pressed(), down = jammed())

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("volume down button is reporting as held down")
    }

    @Test
    fun a_jam_outranks_a_silent_button_because_it_explains_more() {
        // Both faults present. The jam is the more serious one and it explains the phone's behaviour on its
        // own, so it is the headline.
        val result = VolumeButtonCheck.evaluate(
            up = jammed(),
            down = untouched,
            pressedBoth = PressedBoth.YES,
        )

        assertThat(result.headline).contains("held down")
    }

    @Test
    fun both_keys_jammed_reads_as_plural() {
        val result = VolumeButtonCheck.evaluate(jammed(), jammed())

        assertThat(result.headline).contains("volume up and volume down buttons are")
    }

    @Test
    fun the_jam_verdict_names_recovery_mode_and_screenshots() {
        // The reason this matters is not the volume. It is a phone that appears to act on its own, which is
        // what a buyer actually experiences and would never guess the cause of.
        val result = VolumeButtonCheck.evaluate(pressed(), jammed())

        assertThat(result.consequence).contains("recovery mode")
        assertThat(result.consequence).contains("screenshot")
    }

    @Test
    fun a_normal_press_is_never_mistaken_for_a_jam() {
        // A held button and a pressed one differ by nearly four seconds, so the threshold barely matters —
        // but a slow press must not tip into an accusation.
        val slowPress = ButtonObservation(
            presses = 1,
            releases = 1,
            longestHoldMillis = VolumeButtonCheck.STUCK_HOLD_MILLIS + 2_000L,
        )

        // Released, so not stuck however long it was held.
        assertThat(VolumeButtonCheck.evaluate(slowPress, pressed()).outcome)
            .isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun a_key_still_down_but_not_yet_long_enough_is_not_accused() {
        val briefly = ButtonObservation(presses = 1, releases = 0, longestHoldMillis = 500L)

        val result = VolumeButtonCheck.evaluate(briefly, pressed())
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    // ------------------------------------------------------------------ the report

    @Test
    fun the_report_says_what_each_button_did() {
        val result = VolumeButtonCheck.evaluate(pressed(times = 3), untouched)

        assertThat(result.measurements.map { it.label })
            .containsExactly("Volume up", "Volume down").inOrder()
        assertThat(result.measurements.first().value).isEqualTo("3 presses")
        assertThat(result.measurements.last().value).isEqualTo("not pressed")
    }

    @Test
    fun a_single_press_is_not_described_as_one_presses() {
        // Copy that reads like an unfinished template undermines the finding next to it.
        val result = VolumeButtonCheck.evaluate(pressed(times = 1), pressed(times = 1))

        assertThat(result.measurements.first().value).isEqualTo("1 press")
    }

    @Test
    fun a_jammed_key_is_labelled_as_held_rather_than_counted() {
        val result = VolumeButtonCheck.evaluate(pressed(), jammed())

        assertThat(result.measurements.last().value).isEqualTo("held down")
    }

    @Test
    fun every_outcome_tells_the_buyer_what_to_do_next() {
        listOf(
            VolumeButtonCheck.evaluate(untouched, untouched),
            VolumeButtonCheck.evaluate(pressed(), untouched),
            VolumeButtonCheck.evaluate(pressed(), untouched, PressedBoth.NO),
            VolumeButtonCheck.evaluate(pressed(), untouched, PressedBoth.YES),
            VolumeButtonCheck.evaluate(pressed(), jammed()),
        ).forEach { assertThat(it.action).isNotEmpty() }
    }
}
