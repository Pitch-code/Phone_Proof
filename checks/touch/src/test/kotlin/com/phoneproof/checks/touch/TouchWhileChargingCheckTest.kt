package com.phoneproof.checks.touch

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

/**
 * A screen that taps itself only while charging.
 *
 * The rule this pins hardest: it can **never** be a FAIL. The charger and cable are suspects the buyer is
 * not buying, so the worst this may say is CAUTION — and the advice must lead with the cheap fix, swapping
 * the charger, before it talks about the phone. The other rule is that a watch which lost the cable partway
 * measured nothing meaningful and must say UNKNOWN rather than invent a verdict.
 */
class TouchWhileChargingCheckTest {

    private fun watch(
        millis: Long = TouchWhileChargingCheck.FULL_WATCH_MILLIS,
        events: Int = 0,
        chargerThroughout: Boolean = true,
    ) = TouchWhileChargingCheck.Watch(
        watchedMillis = millis,
        // One contact per event, spaced past the same-event window so the shared clusterer counts them apart.
        contacts = (0 until events).map { GhostTouchCheck.Contact(it * 1_000L, 0.7f, 0.8f) },
        chargerConnectedThroughout = chargerThroughout,
    )

    @Test
    fun `a cable pulled out partway is inconclusive, not a verdict`() {
        val result = TouchWhileChargingCheck.evaluate(watch(events = 3, chargerThroughout = false))
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `too short a watch says so`() {
        val result = TouchWhileChargingCheck.evaluate(
            watch(millis = TouchWhileChargingCheck.MINIMUM_USEFUL_MILLIS - 1),
        )
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `a quiet watch on the charger is a low-confidence pass, never a confident all-clear`() {
        val result = TouchWhileChargingCheck.evaluate(watch(events = 0))
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `touches under charge are a caution, never a fail`() {
        // Even a flurry cannot condemn the phone here, because the charger is a suspect the buyer is not
        // buying. This is the whole reason the check exists separately from the unplugged one.
        val result = TouchWhileChargingCheck.evaluate(watch(events = 5))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `the caution tells the buyer to try another charger first`() {
        val result = TouchWhileChargingCheck.evaluate(watch(events = 2))
        assertThat(result.action).contains("different charger")
        // And it must admit the charger could be the innocent explanation.
        assertThat(result.falsePositiveCauses.joinToString(" ")).contains("charger")
    }

    @Test
    fun `a flurry from one spot is counted as the events a person would count`() {
        // Reuses the shared clustering: contacts inside the same-event window are one event, so the number
        // the buyer repeats to the seller is not inflated.
        val burst = TouchWhileChargingCheck.Watch(
            watchedMillis = TouchWhileChargingCheck.FULL_WATCH_MILLIS,
            contacts = listOf(
                GhostTouchCheck.Contact(1_000, 0.7f, 0.8f),
                GhostTouchCheck.Contact(1_100, 0.7f, 0.8f),
                GhostTouchCheck.Contact(1_200, 0.7f, 0.8f),
            ),
            chargerConnectedThroughout = true,
        )
        val result = TouchWhileChargingCheck.evaluate(burst)
        val touches = result.measurements.first { it.label == "Touches nobody made" }.value
        assertThat(touches).isEqualTo("1")
    }
}
