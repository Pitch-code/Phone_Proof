package com.phoneproof.core.run

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.nounFor
import com.phoneproof.core.model.plural

/**
 * The one sentence the whole app exists to produce.
 *
 * Ordered by what it costs to be wrong. [WALK_AWAY] is the only grade that tells someone to abandon a
 * purchase, so it is reserved for faults that money cannot fix.
 */
enum class RunGrade {
    /** Everything essential was measured and nothing was found. */
    LOOKS_GOOD,

    /** Nothing found, but not enough was measured to mean anything by it. */
    INCOMPLETE,

    /** Real faults, all of them the kind a lower price makes up for. */
    NEGOTIATE,

    /** A fault no discount covers, or so many faults that the next one is a matter of time. */
    WALK_AWAY,
}

/** A fault and what to say to the seller about it, ready to read off the screen. */
data class TalkingPoint(
    val finding: String,
    val sayThis: String,
)

/**
 * What the run adds up to.
 *
 * The counts are kept alongside the grade because the grade on its own is the one thing in this app a
 * buyer might quote to a seller, and it has to be defensible on the spot: "three things failed, two
 * could not be tested, here they are".
 */
data class RunVerdict(
    val grade: RunGrade,
    val headline: String,
    val detail: String,
    /** FAILs then CAUTIONs, most confident first. */
    val problems: List<CheckResult>,
    val couldNotTell: List<CheckResult>,
    val passed: List<CheckResult>,
    val skipped: List<RunStep>,
    val unmeasuredEssentials: List<RunStep>,
    val talkingPoints: List<TalkingPoint>,
) {
    val problemCount: Int get() = problems.size
    val passCount: Int get() = passed.size
    val unknownCount: Int get() = couldNotTell.size

    companion object {

        /**
         * Faults that a discount does not solve, so finding one ends the inspection.
         *
         * Every other fault in this app is a price negotiation — a worn battery, a dead pixel, a
         * scratchy speaker all have a rupee value. These three do not:
         *
         *  - **Remote lock.** A lender who still controls the handset can brick it weeks after the
         *    buyer has paid. There is no price at which that is a good deal.
         *  - **Root.** Once the software has been modified, every other reading in the report becomes
         *    a claim made by software the seller could have altered. It does not just add a fault, it
         *    invalidates the rest of the report.
         *  - **Build integrity.** The handset is not reporting itself honestly, which means the buyer
         *    cannot know what they are holding.
         *
         * The IMEI checksum is deliberately absent: it is only ever a CAUTION, because a
         * mistyped digit must never be able to accuse a seller of handling a stolen phone.
         */
        private val DEAL_BREAKERS: Map<String, String> = mapOf(
            "security.device_admin_lock" to
                "someone else can still lock this phone remotely, even after you have paid for it",
            "security.root" to
                "the system software has been modified, so nothing else in this report can be " +
                "trusted — including the parts that passed",
            "software.build_integrity" to
                "this handset is not reporting itself honestly, so there is no telling what it " +
                "actually is",
        )

        /**
         * More than this many outright failures and the phone stops being a negotiation.
         *
         * Two faults is a used phone. Three separate systems failing is a handset that has been
         * dropped in water or dropped repeatedly, and the fourth fault is simply one that has not
         * surfaced yet — it will surface after the money has changed hands.
         */
        private const val TOO_MANY_FAULTS = 3

        fun of(state: RunState): RunVerdict {
            val results = state.allResults

            val problems = results
                .filter { it.outcome == CheckOutcome.FAIL || it.outcome == CheckOutcome.CAUTION }
                .sortedWith(
                    compareBy(
                        { if (it.outcome == CheckOutcome.FAIL) 0 else 1 },
                        {
                            when (it.confidence) {
                                Confidence.HIGH -> 0
                                Confidence.MEDIUM -> 1
                                Confidence.LOW -> 2
                            }
                        },
                    ),
                )

            val fails = problems.filter { it.outcome == CheckOutcome.FAIL }
            val dealBreakers = fails.filter { DEAL_BREAKERS.containsKey(it.id) }
            val skipped = state.steps.filter { state.statusOf(it.id) == RunStepStatus.SKIPPED }
            val unmeasured = state.unmeasuredEssentials

            val grade = when {
                dealBreakers.isNotEmpty() -> RunGrade.WALK_AWAY
                fails.size >= TOO_MANY_FAULTS -> RunGrade.WALK_AWAY
                problems.isNotEmpty() -> RunGrade.NEGOTIATE
                unmeasured.isNotEmpty() -> RunGrade.INCOMPLETE
                else -> RunGrade.LOOKS_GOOD
            }

            return RunVerdict(
                grade = grade,
                headline = headlineFor(grade),
                detail = detailFor(
                    grade = grade,
                    dealBreakers = dealBreakers,
                    failCount = fails.size,
                    problemCount = problems.size,
                    unknownCount = results.count { it.outcome == CheckOutcome.UNKNOWN },
                    unmeasured = unmeasured,
                ),
                problems = problems,
                couldNotTell = results.filter { it.outcome == CheckOutcome.UNKNOWN },
                passed = results.filter { it.outcome == CheckOutcome.PASS },
                skipped = skipped,
                unmeasuredEssentials = unmeasured,
                // CheckResult already refuses to exist as a FAIL or CAUTION without an action, so in
                // practice every problem yields a line here — asserted by a test. Filtering rather
                // than asserting anyway, because the verdict is the last screen that should be
                // capable of crashing: a buyer is reading it with the seller waiting.
                talkingPoints = problems.mapNotNull { result ->
                    result.action
                        ?.takeIf { it.isNotBlank() }
                        ?.let { TalkingPoint(finding = result.title, sayThis = it) }
                },
            )
        }

        private fun headlineFor(grade: RunGrade): String = when (grade) {
            // Not "Do not buy this phone". The app is not standing next to the buyer and does not know
            // what they are being asked to pay, or that they need a phone tonight. It reports what it
            // found and how strongly it feels about it; the decision stays with the person paying.
            RunGrade.WALK_AWAY -> "Walk away from this one"
            RunGrade.NEGOTIATE -> "Worth having, but not at the asking price"
            RunGrade.INCOMPLETE -> "Not enough tested to say"
            RunGrade.LOOKS_GOOD -> "Nothing wrong found"
        }

        private fun detailFor(
            grade: RunGrade,
            dealBreakers: List<CheckResult>,
            failCount: Int,
            problemCount: Int,
            unknownCount: Int,
            unmeasured: List<RunStep>,
        ): String = when (grade) {
            RunGrade.WALK_AWAY -> if (dealBreakers.isNotEmpty()) {
                val reasons = dealBreakers.mapNotNull { DEAL_BREAKERS[it.id] }
                "This is not about the price: " + reasons.joinToString("; and ") + "."
            } else {
                "${plural(failCount, "separate fault")} in one handset. A used phone has one or " +
                    "two; this many means it has been through something, and the next fault is " +
                    "one that simply has not shown itself yet."
            }

            RunGrade.NEGOTIATE ->
                "${plural(problemCount, "thing")} to raise with the seller. Every one of them has " +
                    "a price, so use them — the phone is worth buying for less than they are asking."

            // Reachable only when nothing was found, because a fault outranks a coverage complaint —
            // see the grading order above. So this can open with "nothing has failed" unconditionally.
            RunGrade.INCOMPLETE -> {
                val names = unmeasured.joinToString(", ") { it.title.lowercase() }
                "Nothing has failed so far, but the ${nounFor(unmeasured.size, "test")} that " +
                    "would have told you the most " +
                    "${nounFor(unmeasured.size, "was", "were")} not run: $names. " +
                    "A phone is not clean because nobody looked."
            }

            RunGrade.LOOKS_GOOD -> {
                val caveat = if (unknownCount > 0) {
                    " ${plural(unknownCount, "check")} could not be measured on this phone at " +
                        "all — those are listed below, and they are gaps rather than good news."
                } else {
                    ""
                }
                "Every essential test ran and none of them found a fault.$caveat Nothing here " +
                    "covers what only your own eyes can see: a twisted frame, a re-glued screen, " +
                    "or water damage."
            }
        }
    }
}
