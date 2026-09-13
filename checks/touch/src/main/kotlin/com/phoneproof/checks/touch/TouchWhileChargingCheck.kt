package com.phoneproof.checks.touch

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * A screen that only taps itself while it is charging.
 *
 * The companion to [GhostTouchCheck], and the reason that one tells the buyer to *unplug* first. A whole
 * class of phantom-touch faults appears only under charge: a cheap charger with a poor earth couples noise
 * into the digitiser, a swollen battery presses on the panel that little bit harder when warm, or the
 * phone's own charging circuit is leaking into the touch controller. A phone can watch perfectly quietly on
 * the bench and then tap its way through a text the moment it is plugged in overnight.
 *
 * ## Why this can never be a FAIL
 *
 * The fault the buyer sees while charging might not belong to the phone at all — it can be the *charger* or
 * the *cable*, neither of which they are buying. Condemning a phone for a bad charger would be exactly the
 * shaky negative [CheckResult] forbids. So the ceiling here is CAUTION, and the advice always names the
 * cheap fix first: try another charger. Only the unplugged [GhostTouchCheck] can raise a FAIL, because only
 * it has removed the charger from the list of suspects.
 */
object TouchWhileChargingCheck {

    /**
     * What the screen did while a charger was connected.
     *
     * [chargerConnectedThroughout] is the whole reliability of this check. If the cable came out partway,
     * the watch measured a mix of charging and not, and the result means nothing — so that case is reported
     * as UNKNOWN rather than pretended into a pass or a caution.
     */
    data class Watch(
        val watchedMillis: Long,
        val contacts: List<GhostTouchCheck.Contact>,
        val chargerConnectedThroughout: Boolean,
    )

    /** Below this a quiet spell proves nothing, the same as the unplugged watch. */
    const val MINIMUM_USEFUL_MILLIS: Long = 8_000

    /** How long the screen is watched when the buyer lets it run. Shorter than the unplugged watch: a
     *  charger is in the buyer's hand and the seller wants the phone back sooner. */
    const val FULL_WATCH_MILLIS: Long = 20_000

    fun evaluate(watch: Watch): CheckResult {
        val events = GhostTouchCheck.distinctEvents(watch.contacts)
        val seconds = watch.watchedMillis / 1000

        val measurements = listOf(
            Measurement("Watched for", seconds.toString(), "s"),
            Measurement("While charging", "yes"),
            Measurement("Touches nobody made", events.size.toString()),
        )

        if (!watch.chargerConnectedThroughout) {
            // The cable came out before the watch finished, so part of it was not "while charging" at all.
            // Inconclusive rather than a verdict: the one thing this check controls for was not held.
            return CheckResult(
                id = ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "The charger came out before the watch finished",
                consequence = "This check only means something if the phone is charging the whole time, " +
                    "and the cable was disconnected partway.",
                action = "Plug the charger back in, leave it connected, and run it again without touching " +
                    "the phone.",
                measurements = measurements,
            )
        }

        if (watch.watchedMillis < MINIMUM_USEFUL_MILLIS) {
            return CheckResult(
                id = ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "Not watched for long enough to say",
                consequence = "A screen that taps itself while charging does it now and then, not " +
                    "constantly. A few seconds proves very little.",
                action = "Run it again with the charger connected and leave the phone alone for the full " +
                    "${FULL_WATCH_MILLIS / 1000} seconds.",
                measurements = measurements,
            )
        }

        if (events.isEmpty()) {
            return CheckResult(
                id = ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.LOW,
                headline = "Nothing touched the screen while it charged",
                consequence = "Watched for $seconds seconds on the charger with no phantom touches. This " +
                    "fault comes and goes, so a quiet spell is good news rather than proof.",
                action = "If the phone has a cracked screen, a swollen back or a replaced panel, charge it " +
                    "for longer and watch again before you decide.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        val clustered = clusterLabel(events)

        return CheckResult(
            id = ID,
            title = TITLE,
            // Never a FAIL: the charger and the cable are suspects too, and the buyer is not buying those.
            // A CAUTION at MEDIUM confidence — something real was observed, but where it comes from is not
            // yet settled.
            outcome = CheckOutcome.CAUTION,
            confidence = Confidence.MEDIUM,
            headline = if (events.size == 1) {
                "The screen registered a touch nobody made while charging"
            } else {
                "The screen registered ${events.size} touches nobody made while charging"
            },
            consequence = "A phone that taps itself on the charger will do it whenever it is plugged in — " +
                "overnight, in the car, on a desk. It can be the charger or cable rather than the phone, " +
                "but if it is the phone it usually means a swollen battery or a poorly bonded screen, " +
                "which is also a safety problem.",
            action = "Try a different charger and cable first — if it stops, it was the charger and the " +
                "phone is fine. If it keeps happening, or the screen also taps itself with nothing plugged " +
                "in, it is the phone: get the screen or battery quoted and take that off, or walk away. " +
                clustered,
            measurements = measurements,
            falsePositiveCauses = FALSE_POSITIVE_CAUSES,
        )
    }

    /** Where they landed, in words a buyer can press to check, reusing the unplugged check's phrasing. */
    private fun clusterLabel(events: List<GhostTouchCheck.Contact>): String {
        if (events.size < 2) return ""

        val sameArea = events.all { contact ->
            third(contact.xFraction) == third(events.first().xFraction) &&
                third(contact.yFraction) == third(events.first().yFraction)
        }
        if (!sameArea) return "They came from different parts of the screen."

        val vertical = when (third(events.first().yFraction)) {
            0 -> "top"
            1 -> "middle"
            else -> "bottom"
        }
        val horizontal = when (third(events.first().xFraction)) {
            0 -> "left"
            1 -> "centre"
            else -> "right"
        }
        return "They all came from the same place, around the $vertical $horizontal."
    }

    private fun third(fraction: Float): Int = when {
        fraction < 1f / 3f -> 0
        fraction < 2f / 3f -> 1
        else -> 2
    }

    const val ID: String = "touch-while-charging"
    const val TITLE: String = "Touches while charging"

    /**
     * Why a finding here might not be the phone's fault. The charger leads, because it is both the
     * commonest cause and the cheapest to rule out.
     */
    private val FALSE_POSITIVE_CAUSES: List<String> = listOf(
        "A cheap or damaged charger or cable, which is the first thing to swap out — a poor earth couples " +
            "electrical noise into the screen and makes a healthy panel report touches.",
        "A finger or thumb resting on the edge of the screen while it was being watched.",
        "A wet screen, or condensation under the glass on a phone brought in from the cold.",
    )
}
