package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * How long ago this phone last received a security update.
 *
 * This is the cheapest hard fact in the whole app and it answers your friend's "don't buy phones
 * more than two years old" advice with a date rather than a rule of thumb. A handset that stopped
 * getting patches is permanently exposed, and no amount of careful use fixes it.
 */
object SecurityPatchCheck {

    const val CHECK_ID: String = "software.security_patch"
    private const val TITLE = "Security updates"

    /** Beyond this the phone is behind but still plausibly supported. */
    const val STALE_MONTHS: Int = 6

    /** Beyond this it has almost certainly been dropped by the manufacturer. */
    const val ABANDONED_MONTHS: Int = 18

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A phone that has been offline or factory reset may simply not have installed a waiting update.",
        "Some manufacturers ship security fixes without moving the patch date forward.",
    )

    /**
     * @param todayEpochDay the current date as days since the epoch, injected so the verdict is
     *   deterministic in tests rather than depending on when they happen to run.
     */
    fun evaluate(facts: DeviceFacts, todayEpochDay: Long): CheckResult {
        val patch = facts.securityPatch?.trim()
        val patchEpochDay = patch?.let(::parsePatchDate)

        if (patch.isNullOrBlank() || patchEpochDay == null) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "This build does not report a security patch date.",
                measurements = listOf(
                    Measurement("Patch date", "not reported"),
                    Measurement("Android", "${facts.androidRelease} (API ${facts.sdkInt})"),
                ),
            )
        }

        val ageDays = todayEpochDay - patchEpochDay
        val ageMonths = (ageDays / 30.44).toInt()
        val measurements = listOf(
            Measurement("Patch date", patch),
            Measurement("Age", "$ageMonths", "months"),
            Measurement("Android", "${facts.androidRelease} (API ${facts.sdkInt})"),
        )

        // A future-dated patch means the clock is wrong or the build is fabricated. Either way the
        // honest answer is that the number cannot be trusted, not that the phone is well updated.
        if (ageDays < -2) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The patch date is in the future, which should not happen.",
                consequence = "Either the phone's clock is wrong or the build has been altered. " +
                    "A tampered build can hide anything else this app measures.",
                action = "Check the date and time settings, then run this again. If it stays in " +
                    "the future, treat the whole phone as suspect.",
                measurements = measurements,
                falsePositiveCauses = listOf("A wrong system clock alone explains this.") +
                    FALSE_POSITIVE_CAUSES,
            )
        }

        return when {
            ageMonths >= ABANDONED_MONTHS -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "No security update for $ageMonths months.",
                consequence = "This phone has almost certainly been dropped by its manufacturer. " +
                    "Known security holes will never be fixed, and banking apps may start " +
                    "refusing to run on it.",
                action = "Treat this as an old phone and price it that way, or choose a newer model.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )

            ageMonths >= STALE_MONTHS -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Last security update was $ageMonths months ago.",
                consequence = "It is behind, though probably still supported. Support may end soon.",
                action = "Connect it to wifi and check for a system update before you pay.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )

            else -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = if (ageMonths <= 0) {
                    "Security updates are current."
                } else {
                    "Security updates are current, last one $ageMonths month(s) ago."
                },
                measurements = measurements,
            )
        }
    }

    /** Parses "YYYY-MM-DD" to days since epoch. Returns null on anything unexpected. */
    internal fun parsePatchDate(value: String): Long? {
        val parts = value.split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
        return toEpochDay(year, month, day)
    }

    /** Proleptic Gregorian days-since-1970. Kept local so this module stays pure Kotlin. */
    private fun toEpochDay(year: Int, month: Int, day: Int): Long {
        var total = 365L * year
        total += if (year >= 0) {
            (year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400
        } else {
            -(year / -4 - year / -100 + year / -400)
        }
        total += (367 * month - 362) / 12
        total += day - 1
        if (month > 2) {
            total--
            val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
            if (!leap) total--
        }
        return total - 719528L
    }
}
