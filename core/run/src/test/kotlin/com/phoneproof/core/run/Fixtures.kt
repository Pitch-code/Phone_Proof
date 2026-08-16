package com.phoneproof.core.run

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence

/**
 * Builders for results, because [CheckResult] refuses to be constructed carelessly: anything negative
 * must carry a consequence, an action and its own false-positive causes. Spelling that out in thirty
 * tests would bury what each one is actually asserting.
 */
internal fun pass(id: String, title: String = id): CheckResult = CheckResult(
    id = id,
    title = title,
    outcome = CheckOutcome.PASS,
    confidence = Confidence.HIGH,
    headline = "Fine",
)

internal fun unknown(id: String, title: String = id): CheckResult = CheckResult(
    id = id,
    title = title,
    outcome = CheckOutcome.UNKNOWN,
    confidence = Confidence.LOW,
    headline = "Android will not say",
)

internal fun fail(
    id: String,
    title: String = id,
    confidence: Confidence = Confidence.HIGH,
    action: String? = "Get money off",
): CheckResult = CheckResult(
    id = id,
    title = title,
    outcome = CheckOutcome.FAIL,
    confidence = confidence,
    headline = "Broken",
    consequence = "You will notice this daily",
    action = action,
    falsePositiveCauses = listOf("A case over the sensor"),
)

internal fun caution(
    id: String,
    title: String = id,
    confidence: Confidence = Confidence.MEDIUM,
    action: String? = "Ask about it",
): CheckResult = CheckResult(
    id = id,
    title = title,
    outcome = CheckOutcome.CAUTION,
    confidence = confidence,
    headline = "Worth a look",
    consequence = "It may get worse",
    action = action,
    falsePositiveCauses = listOf("A dirty screen"),
)

/** A session mid-run, with each entry of [results] recorded against its step. */
internal fun runWith(
    results: Map<String, List<CheckResult>> = emptyMap(),
    skipped: List<String> = emptyList(),
    done: List<String> = emptyList(),
): RunState {
    val session = RunSession(now = { 1_000L })
    session.start()
    results.forEach { (step, r) -> session.record(step, r) }
    done.forEach(session::markDone)
    skipped.forEach(session::skip)
    return session.state.value
}

/** Every essential step measured and clean, as the baseline a test then spoils on purpose. */
internal fun allEssentialsPassing(): Map<String, List<CheckResult>> =
    RunPlan.steps.filter { it.essential }.associate { step ->
        step.id to listOf(pass("${step.id}.ok", step.title))
    }
