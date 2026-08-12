package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.model.plural

/** What the person holding the phone says they saw during the pattern test. */
enum class ScreenFinding {
    /** Nothing wrong. Only trustworthy if they actually looked at every pattern. */
    NOTHING,

    /** Dots or specks that stay in one place: dead, stuck or hot pixels. */
    SMALL_DOTS,

    /** Faint patches, ghost images or uneven colour: burn-in, or a failing panel. */
    LARGE_PATCHES,
}

/**
 * Dead pixels and burn-in.
 *
 * The one check in this app whose evidence does not come from the platform, because it cannot: an
 * app has no way to see the screen it is drawing on. There is no API for panel defects, and the
 * camera faces the wrong way. What the app can do is drive the panel through patterns that make
 * defects obvious to a human eye — a dead pixel is invisible on a photograph of a home screen and
 * unmistakable on full-screen white — and then record what the person saw.
 *
 * That makes this a *reported* result, not a measured one, and it says so in the report. Confidence
 * is never HIGH, and the false-positive causes name the honest failure modes: dust, a screen
 * protector, and a buyer glancing rather than looking.
 *
 * The count of patterns actually viewed is part of the verdict. Someone who taps through two of six
 * and says "looked fine" has not tested the screen, and calling that a PASS would be the app
 * inventing a clean bill of health out of an abandoned test.
 */
object ScreenDefectCheck {

    const val CHECK_ID: String = "screen.defects"
    private const val TITLE = "Dead pixels and burn-in"

    private val REPORTED_CAUSES = listOf(
        "This result is what you saw, not something the app measured — no app can inspect the screen it draws on.",
        "Dust or a smudge on the glass looks exactly like a dead pixel. Wipe the screen and run it again.",
        "A screen protector with trapped air or scratches can mimic both faults.",
    )

    /**
     * @param patternsViewed how many full-screen patterns the person actually looked at.
     * @param patternsTotal how many the test offers.
     */
    fun evaluate(
        finding: ScreenFinding,
        patternsViewed: Int,
        patternsTotal: Int,
    ): CheckResult {
        require(patternsTotal > 0) { "patternsTotal must be positive, was $patternsTotal" }
        val viewed = patternsViewed.coerceIn(0, patternsTotal)
        val complete = viewed >= patternsTotal

        val measurements = listOf(
            Measurement("Patterns viewed", "$viewed / $patternsTotal"),
            Measurement("Result", finding.label),
            // Named in the report itself, so a shared report cannot be mistaken for a measurement.
            Measurement("Source", "what you saw"),
        )

        if (viewed == 0) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "The screen patterns were not viewed.",
                measurements = measurements,
            )
        }

        return when (finding) {
            // Seeing a defect is positive evidence and counts even from a partial run — you cannot
            // un-see a stuck pixel. Only the clean answer depends on having looked at everything.
            ScreenFinding.LARGE_PATCHES -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.MEDIUM,
                headline = "You saw patches or ghosting on the screen.",
                consequence = "On an OLED phone that is burn-in, and it is permanent — it will " +
                    "show on every pale screen you ever use, and it only gets worse. Replacing " +
                    "the panel is usually the most expensive repair a phone has.",
                action = "Price a screen replacement for this model, take that off the asking " +
                    "price, or walk away.",
                measurements = measurements,
                falsePositiveCauses = REPORTED_CAUSES,
            )

            ScreenFinding.SMALL_DOTS -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                // Deliberately not a FAIL. A single stuck pixel is a blemish, not a broken phone,
                // and the app cannot count them or judge where they are — that is the buyer's call.
                headline = "You saw dots or specks that stayed in one place.",
                consequence = "Those are dead or stuck pixels. They do not spread, but they do " +
                    "not heal either, and one in the middle of the screen is far more annoying " +
                    "than one in a corner.",
                action = "Look again on white and on black, decide whether you could live with " +
                    "it, and use it to bring the price down.",
                measurements = measurements,
                falsePositiveCauses = REPORTED_CAUSES,
            )

            ScreenFinding.NOTHING -> if (complete) {
                CheckResult(
                    id = CHECK_ID,
                    title = TITLE,
                    outcome = CheckOutcome.PASS,
                    // Never HIGH. The evidence is a person's glance in a shop, not a measurement.
                    confidence = Confidence.MEDIUM,
                    headline = "No dead pixels or burn-in were visible on any pattern.",
                    measurements = measurements,
                )
            } else {
                CheckResult(
                    id = CHECK_ID,
                    title = TITLE,
                    outcome = CheckOutcome.UNKNOWN,
                    confidence = Confidence.HIGH,
                    headline = "Only ${plural(viewed, "pattern")} of $patternsTotal " +
                        "${if (viewed == 1) "was" else "were"} viewed, so the screen was not " +
                        "fully checked.",
                    measurements = measurements,
                )
            }
        }
    }
}

private val ScreenFinding.label: String
    get() = when (this) {
        ScreenFinding.NOTHING -> "nothing seen"
        ScreenFinding.SMALL_DOTS -> "dots or specks"
        ScreenFinding.LARGE_PATCHES -> "patches or ghosting"
    }
