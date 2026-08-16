package com.phoneproof.core.run

import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The one place a guided run accumulates.
 *
 * Every test in this app lives behind its own navigation route and keeps its findings in its own
 * ViewModel, which is why there was no guided run: nothing could see more than one result at a time.
 * Rather than teach each feature about the run — eight modules gaining a dependency on this one, and
 * eight chances to forget — the features stayed as they were and the navigation layer hands their
 * results here. A feature still does not know whether it is being run on its own or as part of a run.
 *
 * Deliberately in memory only. A run is a single conversation in a single shop, and reviving a
 * half-finished one two days later would invite the worst possible bug in this product: findings from
 * one handset shown against another. What survives is the [com.phoneproof.core.reports.SavedReport]
 * written at the end, which records the phone it was taken on.
 */
class RunSession(
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    /**
     * Begin. Always from nothing, never resuming.
     *
     * A buyer in a shop looks at two or three phones in a row. Carrying anything over from the last
     * one is how a report ends up describing a handset that is already back in the cabinet.
     */
    fun start() {
        _state.value = RunState(active = true, startedAtEpochMs = now())
    }

    /**
     * Take the findings of a step.
     *
     * Ignored unless a run is in progress, so opening a single check from Home leaves no trace in a
     * run the buyer has not started. Also ignored when [results] is empty: the navigation layer
     * forwards a feature's state every time it changes, and every feature starts out with nothing
     * measured — treating that first empty emission as a completed step would tick off all eight
     * before the buyer had touched anything.
     */
    fun record(stepId: String, results: List<CheckResult>) {
        if (results.isEmpty()) return
        update(stepId) { current ->
            current.copy(
                statuses = current.statuses + (stepId to RunStepStatus.DONE),
                // Replaces rather than merges, so re-running a step after wiping the screen does not
                // leave the old failure in the report next to the new pass.
                results = current.results + (stepId to results),
            )
        }
    }

    /** Tick off a step that measures nothing, such as the walkthrough the buyer does by eye. */
    fun markDone(stepId: String) {
        update(stepId) { current ->
            current.copy(statuses = current.statuses + (stepId to RunStepStatus.DONE))
        }
    }

    /**
     * Move on without doing it.
     *
     * Kept as its own status rather than just leaving the step pending, because the verdict has to be
     * able to say "you skipped three things" out loud. A run that silently treated skipped and
     * unreached steps alike would let a buyer walk away believing a phone had passed tests nobody ran.
     */
    fun skip(stepId: String) {
        update(stepId) { current ->
            current.copy(statuses = current.statuses + (stepId to RunStepStatus.SKIPPED))
        }
    }

    /**
     * Throw the run away, after saving it or on abandoning it.
     *
     * There is deliberately no "un-skip" counterpart. Revisiting a skipped step and actually doing it
     * records a result, which promotes it to done on its own — so a separate way to put a step back to
     * pending would only ever be used to make the run look less complete than it is.
     */
    fun reset() {
        _state.value = RunState()
    }

    /**
     * Applies [change] only for a step that is really in the plan and only while a run is live.
     *
     * The step id doubles as a navigation route, so a typo anywhere in the wiring would otherwise
     * create a ninth step that no screen can reach and that the run can never complete.
     */
    private inline fun update(stepId: String, change: (RunState) -> RunState) {
        if (RunPlan.step(stepId) == null) return
        _state.update { current -> if (current.active) change(current) else current }
    }
}
