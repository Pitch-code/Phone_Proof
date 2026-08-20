package com.phoneproof.checks.touch

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.model.plural

/** What the buyer said when asked how many fingers they actually got onto the glass. */
enum class FingersDown {
    NOT_ASKED,
    ALL_OF_THEM,
    FEWER,
}

/**
 * How many fingers can this screen follow at once?
 *
 * ## Why this is worth a check of its own
 *
 * The touch-coverage test finds dead *areas*. This finds a dead *capacity*, which is a different fault
 * and invisible to the other one: a digitiser can respond perfectly across every millimetre of glass and
 * still lose the fourth finger. Nothing in a shop demonstration reveals it — scrolling, typing a message
 * and swiping through photos are all one or two fingers.
 *
 * It matters for two things a buyer will actually do. Games steered with two thumbs and fired with a third
 * become unplayable. And fast typing on a phone that tracks three points drops letters, because a quick
 * typist genuinely has four fingers on the glass at overlapping moments.
 *
 * ## Claimed against measured
 *
 * The useful trick is that Android already states what the hardware is supposed to manage.
 * `FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND` means five or more independent points;
 * `..._MULTITOUCH_DISTINCT` means two. So the check does not have to invent a standard — it compares the
 * phone's own claim against what the glass just did, which is the same move the app makes with advertised
 * storage and RAM.
 *
 * A phone that claims two and delivers two passes. A phone that claims five and delivers three has
 * something wrong with it, and its own spec sheet is the accuser.
 */
object MultiTouchCheck {

    const val CHECK_ID: String = "screen.multi_touch"

    private const val TITLE = "Fingers at once"

    /**
     * What the buyer is asked for.
     *
     * Five, because that is what the common capability flag promises and because five is what a hand has.
     * Asking for ten would be asking a buyer to put a phone on a table and use both hands, which is not
     * how this app is used — one hand holds the phone in a shop.
     */
    const val TARGET_FINGERS: Int = 5

    private val FALSE_POSITIVE_CAUSES = listOf(
        "Fingers placed close together can be merged into one point by the digitiser.",
        "A thick or badly fitted screen protector reduces how many points register.",
        "Palm rejection can drop a finger resting near the edge of the screen.",
        "Wet, cold or very dry fingers register unreliably on any capacitive screen.",
        "Some phones cap the count in software below what the hardware can do.",
    )

    /**
     * @param maxObserved the most simultaneous points the screen reported during the test.
     * @param claimedPoints what the phone says it supports, or null when it says nothing useful.
     * @param fingersDown whether the buyer confirms they got all the fingers onto the glass.
     */
    fun evaluate(
        maxObserved: Int,
        claimedPoints: Int?,
        fingersDown: FingersDown = FingersDown.NOT_ASKED,
    ): CheckResult {
        val measurements = buildList {
            add(Measurement("Most fingers tracked", "$maxObserved"))
            add(
                Measurement(
                    "This phone claims",
                    claimedPoints?.let { if (it >= TARGET_FINGERS) "$it or more" else "$it" }
                        ?: "nothing specific",
                ),
            )
        }

        if (maxObserved == 0) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "Nothing was placed on the screen, so there is nothing to report.",
                action = "Run it again and put all five fingers on the glass at once.",
                measurements = measurements,
            )
        }

        // The bar is the phone's own claim, never a number this app decided on. A budget handset that
        // honestly advertises two points and delivers two is working exactly as sold, and failing it for
        // not being a flagship would be the app inventing a defect.
        val expected = claimedPoints ?: TARGET_FINGERS

        if (maxObserved >= expected) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = "The screen followed ${plural(maxObserved, "finger")} at once, which is " +
                    "what this phone claims to support.",
                measurements = measurements,
            )
        }

        return when (fingersDown) {
            // Nothing can be concluded yet. The buyer may simply not have put five fingers down, and
            // there is no second sensor here to vouch for them — so the app has to ask.
            FingersDown.NOT_ASKED -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "The screen followed ${plural(maxObserved, "finger")}, fewer than the " +
                    "$expected this phone claims.",
                action = "That may just be how many fingers you got down. Try again, adding one " +
                    "finger at a time and watching the count.",
                measurements = measurements,
            )

            FingersDown.FEWER -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "You did not get all $expected fingers onto the glass, so this proves " +
                    "nothing about the screen.",
                action = "Worth one more go — rest the phone on something and use both hands if it " +
                    "is easier.",
                measurements = measurements,
            )

            FingersDown.ALL_OF_THEM -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                // CAUTION rather than FAIL, and the reasons are in the list below rather than being
                // hand-waved: fingers pressed together get merged, palm rejection drops a finger near an
                // edge, and a screen protector costs points. Each is common enough that a confident
                // failure here would accuse working phones.
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "You had all $expected fingers down and the screen only followed " +
                    "$maxObserved.",
                consequence = "Games that need two thumbs and a third finger will not respond " +
                    "properly, and fast typing will drop letters — a quick typist really does have " +
                    "four fingers on the glass at overlapping moments. Everyday scrolling will feel " +
                    "fine, which is why this is easy to miss before buying.",
                action = "Take the case and screen protector off and try once more. If it still " +
                    "stops short, treat it as a worn digitiser: ask a repair shop what a screen costs " +
                    "for this model and take that off the price — or skip it if you do not game.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }
    }
}
