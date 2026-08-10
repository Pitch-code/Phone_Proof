package com.phoneproof.core.model

/**
 * The verdict for a single check.
 *
 * [UNKNOWN] is a first-class outcome, not a failure of the app. Plenty of things genuinely
 * cannot be determined on Android — battery state of health is privileged, IMEI is unreadable
 * since Android 10 — and saying so is more useful to a buyer than inventing a number.
 */
enum class CheckOutcome {
    PASS,
    CAUTION,
    FAIL,
    UNKNOWN,
}

/**
 * How much the app is willing to stand behind [CheckResult.outcome].
 *
 * This exists because of a real failure mode in a competing app: it reported a working
 * proximity sensor as broken during a trade-in, and the wrong answer cost the seller money.
 * A check that cries wolf is worse than no check.
 */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
}

/** A single measured value, rendered in tabular monospace so digits never shift. */
data class Measurement(
    val label: String,
    val value: String,
    val unit: String? = null,
) {
    val display: String get() = if (unit == null) value else "$value $unit"
}

/**
 * One row of the inspection report.
 *
 * The `init` block enforces the product's two hard rules structurally rather than by
 * convention, so a future check physically cannot ship in a state that misleads a buyer:
 *
 *  1. Never a bare FAIL. Anything negative must explain the consequence, say what to do, and
 *     admit what could make the reading wrong.
 *  2. An estimate must never masquerade as a measurement, so low confidence and a FAIL cannot
 *     be combined — that has to be reported as CAUTION instead.
 */
data class CheckResult(
    val id: String,
    val title: String,
    val outcome: CheckOutcome,
    val confidence: Confidence,
    /** Plain language, no jargon. "A strip along the bottom-right never responded." */
    val headline: String,
    /** What it means in real life. "You will fight this every time you type." */
    val consequence: String? = null,
    /** What the buyer should do. "Get 2,000 off, or walk away." */
    val action: String? = null,
    val measurements: List<Measurement> = emptyList(),
    val retestable: Boolean = true,
    /** Honest reasons this result might be wrong. Shown next to every negative outcome. */
    val falsePositiveCauses: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "CheckResult.id must not be blank" }
        require(headline.isNotBlank()) { "CheckResult.headline must not be blank for '$id'" }

        if (outcome == CheckOutcome.FAIL || outcome == CheckOutcome.CAUTION) {
            require(!consequence.isNullOrBlank()) {
                "'$id' is $outcome but has no consequence. A buyer cannot act on a bare verdict."
            }
            require(!action.isNullOrBlank()) {
                "'$id' is $outcome but has no action. Tell the buyer what to do about it."
            }
            require(falsePositiveCauses.isNotEmpty()) {
                "'$id' is $outcome but lists no false-positive causes. Every negative result " +
                    "must admit how it could be wrong."
            }
        }

        require(!(outcome == CheckOutcome.FAIL && confidence == Confidence.LOW)) {
            "'$id' cannot be a LOW confidence FAIL. Report it as CAUTION — a shaky negative " +
                "costs the buyer the deal or the seller the price."
        }
    }
}
