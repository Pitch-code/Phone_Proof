package com.phoneproof.core.run

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult

enum class RunStepStatus {
    PENDING,

    /** Produced at least one result, or was ticked off by hand if it produces none. */
    DONE,

    /** The buyer chose to move on. Counted and shown, never quietly forgotten. */
    SKIPPED,
}

/**
 * Everything known about the inspection currently in progress.
 *
 * Results are kept per step rather than in one flat list so that revisiting a step replaces its
 * findings instead of appending them. Without that, a buyer who re-ran the touch test after wiping a
 * greasy screen would end up with both the failure and the pass in the same report, and the report
 * would show a fault the phone does not have.
 */
data class RunState(
    val active: Boolean = false,
    val startedAtEpochMs: Long = 0L,
    val steps: List<RunStep> = RunPlan.steps,
    val statuses: Map<String, RunStepStatus> = emptyMap(),
    val results: Map<String, List<CheckResult>> = emptyMap(),
) {
    fun statusOf(id: String): RunStepStatus = statuses[id] ?: RunStepStatus.PENDING

    fun resultsOf(id: String): List<CheckResult> = results[id].orEmpty()

    /**
     * The most serious thing a finished step found, or null if it measured nothing.
     *
     * Lets the checklist show at a glance which steps found trouble, so a buyer scrolling back does
     * not have to reopen five screens to remember where the problem was.
     *
     * UNKNOWN outranks PASS for the same reason it does in a saved report: a step that could not
     * measure half of what it tried must not wear the same badge as one that came back clean.
     */
    fun worstOutcomeOf(id: String): CheckOutcome? {
        val stepResults = resultsOf(id)
        return when {
            stepResults.isEmpty() -> null
            stepResults.any { it.outcome == CheckOutcome.FAIL } -> CheckOutcome.FAIL
            stepResults.any { it.outcome == CheckOutcome.CAUTION } -> CheckOutcome.CAUTION
            stepResults.any { it.outcome == CheckOutcome.UNKNOWN } -> CheckOutcome.UNKNOWN
            else -> CheckOutcome.PASS
        }
    }

    val doneCount: Int get() = steps.count { statusOf(it.id) == RunStepStatus.DONE }

    val skippedCount: Int get() = steps.count { statusOf(it.id) == RunStepStatus.SKIPPED }

    /** Steps the buyer has dealt with one way or the other. Drives "4 of 8". */
    val settledCount: Int get() = doneCount + skippedCount

    val isFinished: Boolean get() = settledCount == steps.size

    /** 0f to 1f, for the progress bar. */
    val progress: Float
        get() = if (steps.isEmpty()) 0f else settledCount.toFloat() / steps.size

    /** The step the big button goes to: the first one not yet dealt with. */
    val nextStep: RunStep?
        get() = steps.firstOrNull { statusOf(it.id) == RunStepStatus.PENDING }

    /**
     * Seconds still to go, so the run can say "about 4 minutes left" rather than only how far it has
     * come. Skipped steps are gone from the estimate; pending ones are not, even if the buyer intends
     * to skip them too.
     */
    val remainingSeconds: Int
        get() = steps.filter { statusOf(it.id) == RunStepStatus.PENDING }.sumOf { it.typicalSeconds }

    /**
     * Every finding, in run order.
     *
     * Ordered by the plan rather than by when the buyer happened to visit each step, so a report
     * reads the same way every time regardless of how much the buyer jumped around.
     */
    val allResults: List<CheckResult> get() = steps.flatMap { resultsOf(it.id) }

    /** Essential steps that were skipped or never reached. Gates a clean verdict. */
    val unmeasuredEssentials: List<RunStep>
        get() = steps.filter { it.essential && statusOf(it.id) != RunStepStatus.DONE }
}
