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
        // Counted over reachable cells, matching the live readout. Reporting 509 / 512 and 99.4%
        // when 509 was every cell anyone could reach told the buyer they had missed three tiles and
        // made a flawless screen look imperfect — the exact impression the "Not testable" row below
        // exists to prevent. The reserved cells are disclosed on their own line instead.
        val percent = (coverage.testableCoverageRatio * 100f)
        val untestable = coverage.untestedReservedCells.size
        val measurements = buildList {
            add(
                Measurement(
                    "Cells covered",
                    "${coverage.testableTouchedCount} / ${coverage.testableCellCount}",
                ),
            )
            add(Measurement("Coverage", String.format("%.1f", percent), "%"))
            // Disclosed on every outcome, including FAIL and PASS, so the report always states the
            // limits of what was actually measured rather than only when it flatters the phone.
            if (untestable > 0) {
                add(Measurement("Not testable", "$untestable", "cells"))
            }
        }

        // Judged on reachable cells only. Against the raw ratio, a phone with wide gesture strips
        // could never reach the threshold however carefully the tester swiped.
        if (coverage.testableCoverageRatio < MIN_COVERAGE_TO_JUDGE) {
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

        // testableDeadZones, not deadZones: a gap the platform caused is not evidence about the
        // digitiser, and counting it was what made a perfect screen report CAUTION.
        val zones = coverage.testableDeadZones()
        val realDefects = zones.filter { it.size >= DEAD_ZONE_MIN_CELLS }

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

        if (zones.isNotEmpty()) {
            val skipped = zones.sumOf { it.size }
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

        // A pass, but an honest one. Confidence drops to MEDIUM when part of the screen was never
        // readable, because a defect could be hiding in exactly the strip the app could not see.
        // Claiming HIGH here would be the same overstatement the Confidence type exists to prevent.
        if (untestable > 0) {
            return CheckResult(
                id = CHECK_ID,
                title = "Touch response",
                outcome = CheckOutcome.PASS,
                confidence = Confidence.MEDIUM,
                headline = "Every part of the screen the app can read responded.",
                consequence = "${plural(untestable, "cell")} sit under a strip Android keeps for " +
                    "its own swipes, so no app can test them. Nothing suggests a fault there.",
                action = "Check those edges by hand: type a message, and pull the notification " +
                    "shade down from the top.",
                measurements = measurements,
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
