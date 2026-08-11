package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Is this the software the manufacturer shipped, and is the phone what it says it is?
 *
 * Two measurable signals, neither needing a spec catalogue:
 *
 *  - **Build tags.** A retail Android build is signed with `release-keys`. `test-keys` means the
 *    build was signed with publicly known keys — a custom ROM, a rooted build, or a counterfeit.
 *  - **Fingerprint consistency.** `Build.FINGERPRINT` is assembled by the build system from the
 *    brand and device name. When someone edits `Build.MODEL` to make a cheap phone look like a
 *    flagship, the fingerprint usually still carries the original identity. The mismatch is the
 *    tell, and it is exactly how a cloned handset gives itself away.
 */
object BuildIntegrityCheck {

    const val CHECK_ID: String = "software.build_integrity"
    private const val TITLE = "Genuine software"

    // Ordered most-relevant-first, and kept separate per outcome. The card shows only the first
    // cause, so a shared list meant the fingerprint-mismatch caution explained itself with a
    // test-keys excuse that had nothing to do with it — an answer that does not fit the question
    // is worse than no answer, because it invites the buyer to dismiss a real finding.
    private val TEST_KEYS_CAUSES = listOf(
        "Custom ROM users install test-keys builds deliberately.",
        "Engineering and developer units legitimately carry non-retail build tags.",
    )

    private val FINGERPRINT_CAUSES = listOf(
        "A few manufacturers ship unusual fingerprints on regional or carrier variants.",
        "Some refurbishers reflash a generic build that does not match the handset name.",
    )

    fun evaluate(facts: DeviceFacts): CheckResult {
        val tags = facts.buildTags?.trim().orEmpty()
        val fingerprint = facts.fingerprint?.trim().orEmpty()

        val testKeys = tags.contains("test-keys", ignoreCase = true)
        val fingerprintKnown = fingerprint.isNotBlank()
        val brandMatches = !fingerprintKnown ||
            fingerprint.contains(facts.brand, ignoreCase = true)
        val deviceMatches = !fingerprintKnown ||
            fingerprint.contains(facts.device, ignoreCase = true)

        val measurements = listOf(
            Measurement("Reported as", "${facts.manufacturer} ${facts.model}"),
            Measurement("Internal name", facts.device),
            Measurement("Chipset", facts.socModel ?: facts.hardware),
            Measurement("Build tags", tags.ifBlank { "not reported" }),
        )

        if (testKeys) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "This is not manufacturer software — the build is signed with test keys.",
                consequence = "The system has been replaced or modified. It will not get official " +
                    "updates, banking and payment apps will refuse to run, and anything else this " +
                    "app measures could have been faked by whoever built it.",
                action = "Do not buy this phone unless the seller can reflash official firmware " +
                    "in front of you.",
                measurements = measurements,
                falsePositiveCauses = TEST_KEYS_CAUSES,
            )
        }

        if (fingerprintKnown && (!brandMatches || !deviceMatches)) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The phone's identity does not match its own build record.",
                consequence = "It claims to be a ${facts.manufacturer} ${facts.model}, but the " +
                    "build fingerprint says something else. This is what a cloned phone looks like " +
                    "when someone has edited the model name to pass a spec check.",
                action = "Compare the model on the box and the bill, and check the price against " +
                    "the model the fingerprint names before paying.",
                measurements = measurements + Measurement("Fingerprint", shorten(fingerprint)),
                falsePositiveCauses = FINGERPRINT_CAUSES,
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = if (fingerprintKnown) Confidence.HIGH else Confidence.MEDIUM,
            headline = if (fingerprintKnown) {
                "Official software, and the phone's identity is self-consistent."
            } else {
                "Official software. The build fingerprint was not readable to cross-check identity."
            },
            measurements = measurements,
        )
    }

    /** Fingerprints run long; keep the display pasteable. */
    private fun shorten(value: String): String =
        if (value.length <= 34) value else value.take(31) + "…"
}
