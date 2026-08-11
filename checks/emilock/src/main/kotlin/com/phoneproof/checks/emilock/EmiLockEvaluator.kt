package com.phoneproof.checks.emilock

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Decides what administrative control over a phone means for someone buying it.
 *
 * This is the highest-value automated check in the app, because it catches the failure that costs
 * the buyer the entire purchase price rather than a negotiation. Phones sold on instalment plans in
 * India are controlled through Android's device-owner and device-administrator mechanisms: that is
 * how a lender bricks a handset remotely when payments stop. The buyer pays, the original borrower
 * defaults weeks later, and the phone locks with the new owner holding it.
 *
 * Deliberately no curated list of finance-lock app names. The signal is *measured* — the platform is
 * asked what holds control — and whatever is found is reported by name. A package list would go
 * stale, would miss anything new, and would be exactly the kind of lookup table that made a rival
 * app report a new flagship as having 3 GB of RAM.
 */
object EmiLockEvaluator {

    const val CHECK_ID: String = "security.device_admin_lock"
    private const val TITLE = "Remote lock control"

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A company-issued phone is managed on purpose, and the management app is legitimate.",
        "Some antivirus, parental-control and find-my-phone apps register as device administrators.",
        "A factory reset performed in front of you removes almost all of these.",
    )

    fun evaluate(snapshot: DeviceAdminSnapshot): CheckResult {
        if (snapshot.queryFailed) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "This phone would not answer when asked who controls it.",
                measurements = listOf(Measurement("Admin apps found", "not readable")),
            )
        }

        val owners = snapshot.deviceOwners
        if (owners.isNotEmpty()) {
            val names = owners.joinToString(", ") { it.displayName }
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "An app owns this device outright: $names.",
                consequence = "A device owner can lock, wipe or restrict this phone remotely, and " +
                    "it cannot be removed by settings or by a normal factory reset. This is how " +
                    "phones bought on instalments are locked when payments stop — weeks after the " +
                    "sale, with you holding it.",
                action = "Do not pay. Ask the seller to prove the instalments are fully cleared " +
                    "and to have the lock released, or walk away.",
                measurements = measurements(snapshot),
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        val profileOwners = snapshot.profileOwners
        if (profileOwners.isNotEmpty()) {
            val names = profileOwners.joinToString(", ") { it.displayName }
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "A work profile is still managed by $names.",
                consequence = "Someone else's organisation still controls part of this phone and " +
                    "can wipe that profile or enforce policies on it.",
                action = "Insist on a full factory reset in front of you, then run this check " +
                    "again before any money changes hands.",
                measurements = measurements(snapshot),
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        val plain = snapshot.plainAdmins
        if (plain.isNotEmpty()) {
            val names = plain.joinToString(", ") { it.displayName }
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                // Deliberately CAUTION, not FAIL. A plain administrator registration is genuinely
                // ambiguous: it is how finance locks work, and also how antivirus and find-my-phone
                // apps work. Calling it a failure would kill honest deals, and a check that cries
                // wolf stops being believed.
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "${plain.size} app(s) can control this phone remotely: $names.",
                consequence = "A device administrator can lock the screen or wipe the phone. It " +
                    "might be an antivirus or a find-my-phone app — or it might be a lender's lock.",
                action = "Have the seller factory reset the phone in front of you, then run this " +
                    "check again. If the app comes back after a reset, walk away.",
                measurements = measurements(snapshot),
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "Nothing has remote control over this phone.",
            measurements = measurements(snapshot),
        )
    }

    private fun measurements(snapshot: DeviceAdminSnapshot): List<Measurement> = listOf(
        Measurement("Admin apps found", "${snapshot.admins.size}"),
        Measurement("Device owners", "${snapshot.deviceOwners.size}"),
        Measurement("Profile owners", "${snapshot.profileOwners.size}"),
    )
}
