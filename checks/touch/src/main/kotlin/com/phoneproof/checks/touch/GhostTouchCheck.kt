package com.phoneproof.checks.touch

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * A tap nobody made.
 *
 * The fault: a screen that reports touches on its own. It comes from a cracked digitiser, a swollen battery
 * pressing on the panel, water that has been and gone, or a cheap replacement screen that was never properly
 * bonded. Phones with it open apps by themselves, type characters into fields, and answer calls in a pocket.
 *
 * It is the one screen fault this app could not previously find, and the one most likely to be **hidden by a
 * seller** rather than merely unmentioned — because it is intermittent. A phone can behave for five minutes
 * and then misbehave for thirty seconds, so a buyer who is handed a working phone has learned very little.
 * The touch grid finds *dead* areas, which is the opposite problem and needs the buyer's finger; this needs
 * their finger to stay off.
 *
 * ## Why this is a watch rather than a test
 *
 * Nothing can provoke a ghost touch on demand. So the only honest design is to watch an untouched screen for
 * a while and report what arrived — which makes the *absence* of a finding weak evidence, and this file is
 * careful to say so rather than printing a confident pass.
 */
object GhostTouchCheck {

    /**
     * A pointer event that arrived while the screen was supposed to be untouched.
     *
     * [xFraction] and [yFraction] are 0..1 across the panel, so the geometry means the same thing on any
     * screen size — and so a cluster can be described to a buyer as "near the top left" rather than in
     * pixels they cannot verify.
     */
    data class Contact(
        val atMillis: Long,
        val xFraction: Float,
        val yFraction: Float,
    )

    /**
     * What the screen did while nobody was touching it.
     *
     * [watchedMillis] is recorded rather than assumed, because a buyer will cut this short — the seller wants
     * the phone back — and a verdict from four seconds of watching must not read like a verdict from thirty.
     */
    data class Watch(
        val watchedMillis: Long,
        val contacts: List<Contact>,
        /**
         * True when the buyer confirmed they were not touching the screen.
         *
         * Asked rather than assumed. Someone resting a thumb on the edge while they wait produces exactly the
         * evidence this check is looking for, and reporting that as a hardware fault would condemn a good
         * phone. Without the confirmation the finding is reported, but its confidence drops.
         */
        val confirmedHandsOff: Boolean = true,
    )

    /**
     * The shortest watch worth drawing a conclusion from.
     *
     * Ghost touches are intermittent, so a short quiet spell means almost nothing. Ten seconds is not enough
     * to trust a pass — nothing is, really — but below it the app should not even offer an opinion.
     */
    const val MINIMUM_USEFUL_MILLIS: Long = 10_000

    /** How long the screen is watched when the buyer lets it run. */
    const val FULL_WATCH_MILLIS: Long = 30_000

    /**
     * Contacts this close together in time are treated as one event.
     *
     * A single ghost touch usually arrives as a small flurry — the panel reports a down, a jitter and an up,
     * or the same bad spot fires three times in a row. Counting those as three separate faults would inflate
     * the number a buyer repeats to a seller, and the count is the whole finding here.
     */
    const val SAME_EVENT_WINDOW_MILLIS: Long = 400

    fun evaluate(watch: Watch): CheckResult {
        val events = distinctEvents(watch.contacts)
        val seconds = watch.watchedMillis / 1000

        val measurements = listOf(
            Measurement("Watched for", seconds.toString(), "s"),
            Measurement("Touches nobody made", events.size.toString()),
        )

        if (watch.watchedMillis < MINIMUM_USEFUL_MILLIS) {
            // Deliberately UNKNOWN even when nothing arrived. A quiet four seconds is not evidence of a good
            // screen, and saying so would be the most reassuring lie this app could tell.
            return CheckResult(
                id = ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "Not watched for long enough to say",
                consequence = "A screen that taps itself does it now and then, not constantly. A few " +
                    "seconds of quiet proves very little.",
                action = "Run it again and leave the phone alone on a table for the full " +
                    "${FULL_WATCH_MILLIS / 1000} seconds.",
                measurements = measurements,
            )
        }

        if (events.isEmpty()) {
            // A pass, and a quiet one. LOW confidence is the point: this check can only ever report what did
            // not happen while it was looking, and a seller who knows the fault can wait it out.
            return CheckResult(
                id = ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.LOW,
                headline = "Nothing touched the screen by itself",
                consequence = "Watched for $seconds seconds with no phantom touches. This fault comes " +
                    "and goes, so a quiet spell is good news rather than proof.",
                action = "If the phone has a cracked screen, a swollen back or a replaced panel, watch " +
                    "it for longer before you decide.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        val clustered = clusterLabel(events)
        val handsOffCaveat = if (watch.confirmedHandsOff) {
            ""
        } else {
            " You did not confirm the phone was untouched, so a resting thumb could explain this — " +
                "watch it again with the phone flat on a table before you judge it."
        }

        return CheckResult(
            id = ID,
            title = TITLE,
            // FAIL when the buyer confirmed the phone was untouched, because a screen that types by itself
            // is not a thing to negotiate over: it enters PINs, answers calls and taps buttons in a pocket,
            // and no amount off makes that liveable.
            //
            // CAUTION when they did not confirm it, and this is not a hedge — CheckResult forbids a LOW
            // confidence FAIL outright, on the grounds that a shaky negative costs the buyer the deal or the
            // seller the price. That rule is right and it caught this: a resting thumb produces exactly this
            // evidence, so without the confirmation the app has a finding worth reporting and no business
            // condemning the phone on it.
            outcome = if (watch.confirmedHandsOff) CheckOutcome.FAIL else CheckOutcome.CAUTION,
            confidence = if (watch.confirmedHandsOff) Confidence.HIGH else Confidence.LOW,
            headline = if (events.size == 1) {
                "The screen registered a touch nobody made"
            } else {
                "The screen registered ${events.size} touches nobody made"
            },
            consequence = "A screen that taps itself opens apps, types into fields and answers calls in " +
                "a pocket. It usually means a cracked or replaced digitiser, or a swollen battery " +
                "pressing on the panel from behind — which is also a safety problem." + handsOffCaveat,
            action = "Look at the back of the phone for a bulge or a gap around the frame, and at the " +
                "screen edges for a lift. This is a screen replacement, so get it quoted and take that " +
                "off — or walk away. $clustered",
            measurements = measurements,
            falsePositiveCauses = FALSE_POSITIVE_CAUSES,
        )
    }

    /**
     * Collapses a flurry of contacts into the events a person would count.
     *
     * A single ghost touch commonly arrives as several reports in quick succession. Counting each one would
     * turn one fault into "eleven touches nobody made", which is the number the buyer would repeat — and
     * being caught exaggerating costs more than the finding is worth.
     */
    internal fun distinctEvents(contacts: List<Contact>): List<Contact> {
        if (contacts.isEmpty()) return emptyList()

        val ordered = contacts.sortedBy { it.atMillis }
        val events = mutableListOf(ordered.first())
        ordered.drop(1).forEach { contact ->
            if (contact.atMillis - events.last().atMillis > SAME_EVENT_WINDOW_MILLIS) {
                events += contact
            }
        }
        return events
    }

    /**
     * Where they landed, in words a buyer can check against the phone in their hand.
     *
     * Named in ninths rather than given as coordinates, because "near the top left" is something someone can
     * press to confirm, and a pixel offset is not.
     */
    private fun clusterLabel(events: List<Contact>): String {
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
        // A repeating spot is the more useful finding: it points at a specific injury rather than a panel
        // that is generally unwell, and it is the thing a buyer can press to see for themselves.
        return "They all came from the same place, around the $vertical $horizontal."
    }

    private fun third(fraction: Float): Int = when {
        fraction < 1f / 3f -> 0
        fraction < 2f / 3f -> 1
        else -> 2
    }

    const val ID: String = "ghost-touch"
    const val TITLE: String = "Touches nobody made"

    /**
     * Why a finding here might not be the phone's fault.
     *
     * Required by [CheckResult] whenever an outcome is not a plain pass, and the honest list is short but
     * real: a thumb resting on the edge, a charger with a poor earth, and water still under the glass are all
     * things that produce this evidence without a broken digitiser.
     */
    private val FALSE_POSITIVE_CAUSES: List<String> = listOf(
        "A finger or thumb resting on the edge of the screen while it was being watched.",
        "A cheap or damaged charger plugged in, which can make some panels report touches that are not " +
            "there. Unplug it and watch again.",
        "A wet screen, or a phone brought in from the cold with condensation under the glass.",
    )
}
