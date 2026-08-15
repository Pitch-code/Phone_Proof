package com.phoneproof.checks.touch

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.model.plural

/**
 * Turns raw coverage into a report row.
 *
 * The ordering of the decisions is the whole point. Incomplete coverage is reported as
 * UNKNOWN, never as a failure — if the tester simply has not swiped everywhere yet, telling
 * them the screen is broken would be a lie that either kills a good deal or, worse, teaches
 * them to ignore the app.
 */
object TouchCoverageEvaluator {

    /** Below this, the test has not gathered enough data to say anything at all. */
    const val MIN_COVERAGE_TO_JUDGE: Float = 0.90f

    /**
     * Smallest contiguous block of unresponsive cells treated as a real defect.
     * Four cells on the default 16x32 grid is roughly a fingertip's worth of screen —
     * large enough that a careful tester would not have skipped it by accident.
     */
    const val DEAD_ZONE_MIN_CELLS: Int = 4

    const val CHECK_ID: String = "screen.touch_coverage"

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A screen protector with a trapped air bubble can block touch in one spot.",
        "Wet, oily or very dry fingers register unreliably.",
        "A thick case can shadow the extreme edges of the screen.",
        "Holding the phone by the edge can trigger palm rejection.",
    )

    fun evaluate(coverage: TouchCoverage): CheckResult {
        // Every cell, in both halves of the fraction. The edges are part of the test now, so there is
        // no second denominator and no "Not testable" row — the product owner's call, and it removes
        // a line that told the buyer part of their screen was none of the app's business.
        val percent = (coverage.coverageRatio * 100f)
        val measurements = listOf(
            Measurement("Cells covered", "${coverage.touchedCount} / ${coverage.cellCount}"),
            Measurement("Coverage", String.format("%.1f", percent), "%"),
        )

        if (coverage.coverageRatio < MIN_COVERAGE_TO_JUDGE) {
            val needed = (MIN_COVERAGE_TO_JUDGE * 100).toInt()
            return CheckResult(
                id = CHECK_ID,
                title = "Touch response",
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "Not enough of the screen has been covered yet.",
                measurements = measurements +
                    Measurement("Needed to judge", "$needed", "%"),
                // No consequence or action: there is nothing to act on, only more swiping
                // to do, and the UI prompts for that directly.
            )
        }

        // Every gap, then split by whether it can be attributed to the phone at all.
        //
        // A gap lying wholly inside the strips Android may have intercepted is not evidence about the
        // digitiser and never becomes a FAIL or a CAUTION, however large it is. That is not leniency:
        // the app genuinely cannot distinguish a dead strip from a swipe the system swallowed, and
        // guessing in either direction would be a fabrication. It is reported as unattributable, with
        // an instruction to sweep again.
        //
        // "Wholly" is the load-bearing word. A patch straddling an edge has cells the system could not
        // have taken, so it is judged on those and can still fail.
        val zones = coverage.deadZones()
        val (unattributable, attributable) = zones.partition { coverage.isEntirelySystemGesture(it) }
        val realDefects = attributable.filter { it.size >= DEAD_ZONE_MIN_CELLS }

        if (realDefects.isNotEmpty()) {
            val worst = realDefects.first()
            val region = worst.region(coverage.spec).label
            val others = realDefects.size - 1
            val extra = if (others > 0) " Plus ${plural(others, "more area")}." else ""

            return CheckResult(
                id = CHECK_ID,
                title = "Touch response",
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "A patch near the $region never responded.$extra",
                consequence = "The screen is physically unresponsive there. You will fight it " +
                    "every time you type, and it usually gets worse, not better.",
                action = "Retest that spot to be sure. If it repeats, a screen replacement " +
                    "costs real money — use it to negotiate hard, or walk away.",
                measurements = measurements + listOf(
                    Measurement("Dead areas", "${realDefects.size}"),
                    Measurement("Largest area", "${worst.size}", "cells"),
                ),
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        if (attributable.isNotEmpty()) {
            val skipped = attributable.sumOf { it.size }
            return CheckResult(
                id = CHECK_ID,
                title = "Touch response",
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "${plural(skipped, "small spot")} missed, scattered rather than grouped.",
                consequence = "Scattered gaps usually mean the finger skipped, not that the " +
                    "screen is faulty — but it cannot be ruled out without another pass.",
                action = "Swipe slowly over the highlighted spots once more.",
                measurements = measurements +
                    Measurement("Missed spots", "$skipped", "cells"),
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        // Everything left is in a strip the system may have taken. UNKNOWN, not PASS and not CAUTION:
        // the honest answer is that this part was not measured, and UNKNOWN is a first-class answer in
        // this app precisely so a gap in the evidence never has to be dressed up as a result.
        //
        // Deliberately not a PASS with a disclaimer, which is what this used to be. A buyer reads the
        // badge and not the small print, and a green PASS covering cells nobody managed to touch is the
        // overstatement the whole check exists to avoid.
        if (unattributable.isNotEmpty()) {
            val missed = unattributable.sumOf { it.size }
            return CheckResult(
                id = CHECK_ID,
                title = "Touch response",
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.MEDIUM,
                headline = "Android took ${plural(missed, "swipe")} at the very edge for itself.",
                consequence = "Those cells sit exactly where the system's own gestures live — the " +
                    "shade at the top, the home swipe at the bottom — so the app cannot tell a dead " +
                    "strip from a swipe the phone answered instead of passing on. This is not a " +
                    "finding about the screen either way.",
                action = "Sweep the very top and bottom edges again, slowly and starting just " +
                    "inside the screen. If the same cells stay dark after two tries, treat them as " +
                    "untested and check them by hand: type a message, and pull the shade down.",
                measurements = measurements +
                    Measurement("Unattributed", "$missed", "cells"),
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = "Touch response",
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "Every part of the screen responded.",
            measurements = measurements,
        )
    }
}
