package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Is the storage the size it is sold as?
 *
 * Fake or downgraded storage chips are a real fraud in an unorganised market: a phone advertised
 * as 128 GB that actually holds 32 GB. The chip reports whatever its controller claims, so no
 * lookup table helps — but arithmetic does. Marketed capacities come in known powers of two, and
 * real usable space always lands in a predictable band below the marketed figure once the system
 * partition and filesystem overhead are accounted for.
 *
 * A capacity that sits far below every standard tier is the signal.
 */
object StorageCheck {

    const val CHECK_ID: String = "hardware.storage"
    private const val TITLE = "Storage"

    private const val GB = 1_000_000_000.0

    /** Capacities phones are actually sold in. */
    private val MARKETED_TIERS_GB = listOf(8, 16, 32, 64, 128, 256, 512, 1024)

    /**
     * Real usable space is typically 88–95% of the marketed figure. Below this, the reported total
     * is too small for the tier it claims to belong to.
     */
    const val MIN_PLAUSIBLE_FRACTION: Double = 0.82

    /** Below this the phone is unusable in practice regardless of honesty. */
    const val LOW_FREE_SPACE_GB: Double = 2.0

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A heavy manufacturer skin can occupy several gigabytes of the system partition.",
        "Some regional variants genuinely ship with unusual capacities.",
        "Android reports usable space, which is always less than the number on the box.",
    )

    fun evaluate(facts: DeviceFacts): CheckResult {
        val total = facts.totalStorageBytes
        val free = facts.freeStorageBytes

        if (total == null || total <= 0L) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "Storage size could not be read on this device.",
                measurements = listOf(Measurement("Total", "not readable")),
            )
        }

        val totalGb = total / GB
        val tier = MARKETED_TIERS_GB.firstOrNull { it >= totalGb } ?: MARKETED_TIERS_GB.last()
        val fraction = totalGb / tier
        val measurements = buildList {
            add(Measurement("Usable total", format(totalGb), "GB"))
            add(Measurement("Sold as", "$tier", "GB"))
            free?.let { add(Measurement("Free now", format(it / GB), "GB")) }
        }

        if (fraction < MIN_PLAUSIBLE_FRACTION) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Usable storage is ${format(totalGb)} GB, low for a ${tier} GB phone.",
                consequence = "Either a lot of space is taken by the system, or the storage chip " +
                    "is smaller than advertised. Downgraded chips are a known fraud, and they " +
                    "usually fail once you actually fill them.",
                action = "Ask what capacity it was sold as. Copy a few gigabytes of video onto it " +
                    "and play it back before you pay.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        if (free != null && free / GB < LOW_FREE_SPACE_GB) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.HIGH,
                headline = "Only ${format(free / GB)} GB free.",
                consequence = "There is not enough free space to test the phone properly, and it " +
                    "may be hiding the seller's data rather than being genuinely full.",
                action = "Have the seller factory reset it in front of you, then check again.",
                measurements = measurements,
                falsePositiveCauses = listOf("The seller's own photos and apps explain this entirely."),
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.MEDIUM,
            headline = "${format(totalGb)} GB usable, consistent with a ${tier} GB phone.",
            measurements = measurements,
        )
    }

    private fun format(value: Double): String =
        if (value >= 100) value.toInt().toString() else String.format("%.1f", value)
}
