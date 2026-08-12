package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * What the seller said the phone is.
 *
 * Typed in by the buyer, because there is no honest way to look it up. A device catalogue was
 * considered and rejected: the licensable ones forbid commercial use, and a rival app was observed
 * reporting a current flagship as having 3 GB of RAM from exactly that kind of stale table. Asking
 * is not a limitation here — the claim is the thing being tested, and only the seller made it.
 */
data class ClaimedSpecs(
    /** As advertised, in the decimal gigabytes phones are sold in: 64, 128, 256. */
    val storageGb: Int? = null,
    /** As advertised: 4, 6, 8, 12. Marketed RAM is binary, unlike storage. */
    val ramGb: Int? = null,
    /** Free text, e.g. "realme P4 5G". Shown for comparison, never auto-judged. */
    val modelName: String? = null,
) {
    val hasAnything: Boolean
        get() = storageGb != null || ramGb != null || !modelName.isNullOrBlank()
}

/**
 * Claimed against measured.
 *
 * The fraud this exists to catch is simple and common: a phone sold as 128 GB that holds 32, or as
 * 8 GB of RAM when it has 4. Both are arithmetic, and arithmetic needs no lookup table.
 *
 * The whole difficulty is knowing what a *legitimate* shortfall looks like, because every honest
 * phone reports less than the number on its box:
 *
 *  - **Storage is marketed in decimal gigabytes** (1 GB = 1,000,000,000 bytes) and then loses the
 *    system partition on top, so a genuine 128 GB phone reports about 109 GB usable — 85%.
 *  - **RAM is marketed in binary gibibytes** (1 GB = 1,073,741,824 bytes) and the kernel reserves a
 *    slice that is never reported at all, so a genuine 8 GB phone reports about 7.4 GB — 92% of the
 *    binary figure, but 99% of the decimal one.
 *
 * Using one divisor for both is the obvious mistake and it fails in the worst direction: it would
 * accuse an honest 8 GB phone of having short RAM. The two units are kept separate here for that
 * reason, and there is a test for it.
 */
object ClaimedSpecsCheck {

    const val CHECK_ID: String = "hardware.claimed_specs"
    private const val TITLE = "Claimed against measured"

    private const val DECIMAL_GB = 1_000_000_000.0
    private const val BINARY_GB = 1_073_741_824.0

    /**
     * Least usable storage an honest phone reports, as a fraction of its marketed decimal size.
     *
     * Matches [StorageCheck.MIN_PLAUSIBLE_FRACTION] on purpose: two different thresholds for the
     * same physical question would eventually contradict each other in one report.
     */
    const val MIN_STORAGE_FRACTION: Double = StorageCheck.MIN_PLAUSIBLE_FRACTION

    /**
     * Least RAM an honest phone reports, as a fraction of its marketed binary size.
     *
     * Lower than the storage threshold because the kernel's reservation is proportionally larger and
     * varies more between chipsets. Erring low matters: accusing an honest phone is worse than
     * missing a marginal case, and a genuinely halved RAM claim sits nowhere near 0.75.
     */
    const val MIN_RAM_FRACTION: Double = 0.75

    private val CAUSES = listOf(
        "The figures are what you typed in. If the seller said something different, the comparison changes.",
        "Every phone reports less than the number on the box: the system partition takes storage, and the kernel reserves memory.",
        "Some regional variants genuinely ship with unusual capacities.",
    )

    fun evaluate(claims: ClaimedSpecs, facts: DeviceFacts): CheckResult {
        if (!claims.hasAnything) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "Nothing was claimed, so there is nothing to compare.",
                measurements = emptyList(),
            )
        }

        val measurements = mutableListOf<Measurement>()
        val shortfalls = mutableListOf<String>()

        // ---- storage, decimal
        val measuredStorageGb = facts.totalStorageBytes?.let { it / DECIMAL_GB }
        claims.storageGb?.let { claimed ->
            measurements += Measurement("Storage claimed", "$claimed", "GB")
            if (measuredStorageGb == null) {
                measurements += Measurement("Storage measured", "not readable")
            } else {
                measurements += Measurement("Storage measured", format(measuredStorageGb), "GB")
                if (measuredStorageGb / claimed < MIN_STORAGE_FRACTION) {
                    shortfalls += "storage"
                }
            }
        }

        // ---- RAM, binary
        val measuredRamGb = facts.totalRamBytes?.let { it / BINARY_GB }
        claims.ramGb?.let { claimed ->
            measurements += Measurement("RAM claimed", "$claimed", "GB")
            if (measuredRamGb == null) {
                measurements += Measurement("RAM measured", "not readable")
            } else {
                measurements += Measurement("RAM measured", format(measuredRamGb), "GB")
                if (measuredRamGb / claimed < MIN_RAM_FRACTION) {
                    shortfalls += "memory"
                }
            }
        }

        // ---- model, shown but never judged
        claims.modelName?.takeIf { it.isNotBlank() }?.let { claimed ->
            measurements += Measurement("Model claimed", claimed.trim())
            measurements += Measurement("Phone reports", "${facts.manufacturer} ${facts.model}".trim())
        }

        if (shortfalls.isNotEmpty()) {
            val what = shortfalls.joinToString(" and ")
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "This phone has less $what than you were told.",
                consequence = "The gap is far bigger than the normal difference between the number " +
                    "on the box and what a phone reports. Either the claim is wrong or the part has " +
                    "been swapped for a smaller one, and downgraded chips tend to fail once filled.",
                action = "Show the seller these numbers and ask them to explain. Do not pay the " +
                    "price of the phone you were promised.",
                measurements = measurements,
                falsePositiveCauses = CAUSES,
            )
        }

        // A phone with more than claimed is not a fraud against the buyer, so it is not a failure —
        // but it is worth saying, because it usually means the seller does not know what they have.
        val generousStorage = claims.storageGb != null && measuredStorageGb != null &&
            measuredStorageGb / claims.storageGb > 1.05

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.MEDIUM,
            headline = if (generousStorage) {
                "The phone reports more storage than you were told."
            } else {
                "What the phone reports matches what you were told."
            },
            consequence = if (claims.modelName.isNullOrBlank()) {
                null
            } else {
                // Said on every pass that involves a model name, because the mismatch is normal and
                // a buyer who does not know that will think they have caught something.
                "Model names rarely match exactly. Phones report an internal code — RMX5110 rather " +
                    "than realme P4 5G — so a difference there is not evidence of anything."
            },
            action = if (claims.modelName.isNullOrBlank()) null else "Compare the two model lines yourself.",
            measurements = measurements,
            falsePositiveCauses = if (claims.modelName.isNullOrBlank()) emptyList() else CAUSES,
        )
    }

    private fun format(value: Double): String =
        if (value >= 100) value.toInt().toString() else String.format("%.1f", value)
}
