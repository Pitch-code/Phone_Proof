package com.phoneproof.checks.media

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class EarpieceCheckTest {

    // ------------------------------------------------------------------ routing comes first

    @Test
    fun a_phone_with_no_earpiece_is_reported_as_a_fact_not_a_fault() {
        val result = EarpieceCheck.evaluate(EarpieceRouting.ABSENT)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        // HIGH, unusually for an UNKNOWN: the phone was asked what outputs it has and it answered. That
        // is a measurement, not a failure to measure.
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.headline).contains("no earpiece")
    }

    @Test
    fun refused_routing_is_never_a_fault_in_the_phone() {
        val result = EarpieceCheck.evaluate(EarpieceRouting.REFUSED)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
        assertThat(result.headline).contains("would not let the app send sound to the earpiece")
    }

    @Test
    fun a_yes_about_unrouted_sound_can_never_pass_the_earpiece() {
        // The single most important test in this file.
        //
        // If the platform will not confirm the earpiece as the output, whatever the buyer heard may have
        // come out of the loudspeaker. Accepting a "yes" then would pass a completely dead earpiece — and
        // the buyer would find out on their first call, after paying.
        listOf(HeardTone.YES, HeardTone.NO, HeardTone.NOT_ASKED).forEach { answer ->
            val result = EarpieceCheck.evaluate(
                routing = EarpieceRouting.REFUSED,
                toneRatio = 0.9f,
                heard = answer,
            )
            assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        }
    }

    @Test
    fun a_loud_tone_on_an_absent_earpiece_still_proves_nothing() {
        // Belt and braces on the same principle: a phone with no earpiece that somehow measures a tone is
        // measuring its loudspeaker.
        val result = EarpieceCheck.evaluate(
            routing = EarpieceRouting.ABSENT,
            toneRatio = 1f,
            heard = HeardTone.YES,
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    // ------------------------------------------------------------------ measured

    @Test
    fun a_measured_tone_through_a_confirmed_earpiece_passes_on_its_own() {
        val result = EarpieceCheck.evaluate(
            routing = EarpieceRouting.CONFIRMED,
            toneRatio = 0.3f,
            roomFloor = 0.01f,
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.measurements.map { it.label }).contains("Routing")
    }

    @Test
    fun the_bar_is_lower_than_the_loudspeakers_and_that_is_deliberate() {
        // An earpiece is built to be heard by one ear pressed against it, from the opposite end of the
        // phone to the microphone. Holding it to the loudspeaker's standard would fail working hardware.
        assertThat(EarpieceCheck.TONE_DETECTED_RATIO)
            .isLessThan(SpeakerCheck.TONE_DETECTED_RATIO)

        val faint = EarpieceCheck.evaluate(EarpieceRouting.CONFIRMED, toneRatio = 0.15f)
        assertThat(faint.outcome).isEqualTo(CheckOutcome.PASS)

        // The same reading would not have satisfied the loudspeaker.
        assertThat(0.15f).isLessThan(SpeakerCheck.TONE_DETECTED_RATIO)
    }

    // ------------------------------------------------------------------ then asked

    @Test
    fun an_unmeasurable_tone_becomes_a_question_rather_than_a_verdict() {
        val result = EarpieceCheck.evaluate(EarpieceRouting.CONFIRMED, toneRatio = 0.01f)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("proves nothing")
        assertThat(result.action).contains("Hold the phone to your ear")
    }

    @Test
    fun the_buyers_ear_can_pass_it_but_only_as_their_finding() {
        val result = EarpieceCheck.evaluate(
            routing = EarpieceRouting.CONFIRMED,
            toneRatio = 0.01f,
            heard = HeardTone.YES,
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        // The headline has to say who heard it. A report read a week later must not look like the app
        // confirmed this itself.
        assertThat(result.headline).startsWith("You heard it")
    }

    @Test
    fun two_independent_misses_are_the_only_route_to_a_failure() {
        val result = EarpieceCheck.evaluate(
            routing = EarpieceRouting.CONFIRMED,
            toneRatio = 0.01f,
            heard = HeardTone.NO,
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        // The consequence has to be the real one, which is not "no sound" but "no private calls".
        assertThat(result.consequence).contains("speakerphone")
        assertThat(result.falsePositiveCauses).isNotEmpty()
    }

    @Test
    fun a_failure_admits_that_a_working_earpiece_is_often_too_quiet_to_measure() {
        val result = EarpieceCheck.evaluate(
            routing = EarpieceRouting.CONFIRMED,
            toneRatio = 0.01f,
            heard = HeardTone.NO,
        )

        assertThat(result.falsePositiveCauses.first()).contains("quiet by design")
        // And it admits the routing itself may have lied, which is the one thing this check cannot verify
        // beyond taking the platform's word for it.
        assertThat(result.falsePositiveCauses.any { it.contains("still play through the loudspeaker") })
            .isTrue()
    }

    @Test
    fun a_measured_pass_never_needs_the_buyer_to_be_asked_anything() {
        // Their answer must not be able to overturn a measurement, in either direction.
        val heardNo = EarpieceCheck.evaluate(
            routing = EarpieceRouting.CONFIRMED,
            toneRatio = 0.4f,
            heard = HeardTone.NO,
        )

        assertThat(heardNo.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun it_is_a_different_check_from_the_loudspeaker() {
        // Separate ids, because they are separate parts with separate prices. Rolled into one row they
        // would produce a single vague line to argue with.
        assertThat(EarpieceCheck.CHECK_ID).isNotEqualTo(SpeakerCheck.CHECK_ID)
        assertThat(EarpieceCheck.CHECK_ID).startsWith("hardware.")
    }

    @Test
    fun every_outcome_carries_something_for_the_buyer_to_do() {
        // Including the ones that are not faults. "Cannot tell" with no next step is the most useless
        // thing this app could show, and on this check there is always a next step: make a call.
        listOf(
            EarpieceCheck.evaluate(EarpieceRouting.ABSENT),
            EarpieceCheck.evaluate(EarpieceRouting.REFUSED),
            EarpieceCheck.evaluate(EarpieceRouting.CONFIRMED, toneRatio = 0.01f),
            EarpieceCheck.evaluate(EarpieceRouting.CONFIRMED, toneRatio = 0.01f, heard = HeardTone.NO),
        ).forEach { assertThat(it.action).isNotEmpty() }
    }
}
