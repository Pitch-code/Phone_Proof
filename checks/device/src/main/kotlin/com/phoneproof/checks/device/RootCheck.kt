package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.model.plural

/**
 * What was found when looking for root and an unlocked bootloader.
 *
 * @param verifiedBootState the value of `ro.boot.verifiedbootstate`. Android Verified Boot reports
 *   `green` for a stock, sealed device, `yellow` when booting an image signed with a user key,
 *   `orange` when verification is switched off entirely — an unlocked bootloader — and `red` when
 *   verification failed. Null when the property could not be read.
 * @param readable false when none of the checks could run, which must not be reported as "clean".
 */
data class RootSignals(
    val suBinaryPaths: List<String> = emptyList(),
    val rootManagerPackages: List<String> = emptyList(),
    val verifiedBootState: String? = null,
    val testKeysBuild: Boolean = false,
    val readable: Boolean = true,
)

/**
 * Is this phone rooted, and is its bootloader still locked?
 *
 * This matters far more to a buyer than it sounds. A rooted phone is not merely "modified":
 *
 *  - **Banking and UPI apps refuse to run on it.** Someone can pay for a handset they then cannot
 *    pay *with*, and discover it only after the seller is gone.
 *  - Play Integrity fails, so Google Wallet and many company apps stop working.
 *  - The manufacturer warranty is void.
 *  - Root survives a factory reset. Anything the previous owner installed with root access —
 *    including something watching the new owner — can survive too.
 *
 * Multiple independent signals are collected because each one alone is defeatable: root managers
 * routinely hide themselves from package queries, and a hidden `su` proves nothing about the
 * bootloader. Verified-boot state is the hardest of them to fake from userspace.
 */
object RootCheck {

    const val CHECK_ID: String = "security.root"
    private const val TITLE = "Root and bootloader"

    const val STATE_GREEN = "green"
    const val STATE_YELLOW = "yellow"
    const val STATE_ORANGE = "orange"
    const val STATE_RED = "red"

    private val ROOT_CAUSES = listOf(
        "Enthusiasts root their own phones deliberately; it is not always a sign of fraud.",
        "A few manufacturers ship developer editions with the bootloader unlocked from new.",
        "Some security apps place files in paths that resemble a root install.",
    )

    private val UNLOCK_CAUSES = listOf(
        "A developer edition can ship unlocked from the factory.",
        "Relocking is sometimes possible, so a seller may be able to fix this before you pay.",
    )

    fun evaluate(signals: RootSignals): CheckResult {
        if (!signals.readable) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "None of the root checks could run on this device.",
                measurements = listOf(Measurement("Root checks", "not readable")),
            )
        }

        val state = signals.verifiedBootState?.lowercase()?.trim()
        val rooted = signals.suBinaryPaths.isNotEmpty() || signals.rootManagerPackages.isNotEmpty()
        val measurements = buildList {
            add(Measurement("su binary", if (signals.suBinaryPaths.isEmpty()) "not found" else "FOUND"))
            add(
                Measurement(
                    "Root manager app",
                    if (signals.rootManagerPackages.isEmpty()) "not found" else "FOUND",
                ),
            )
            add(Measurement("Verified boot", state ?: "not readable"))
            add(Measurement("Build tags", if (signals.testKeysBuild) "test-keys" else "release-keys"))
        }

        if (rooted) {
            val what = buildList {
                if (signals.suBinaryPaths.isNotEmpty()) {
                    add(plural(signals.suBinaryPaths.size, "su binary", "su binaries"))
                }
                if (signals.rootManagerPackages.isNotEmpty()) {
                    add(plural(signals.rootManagerPackages.size, "root manager app"))
                }
            }.joinToString(" and ")

            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "This phone is rooted — found $what.",
                consequence = "Banking and UPI apps will refuse to run, Google Wallet will not " +
                    "work, and the warranty is void. Root survives a factory reset, so anything " +
                    "the previous owner installed with it can survive too.",
                action = "Do not buy this as a daily phone unless you know exactly what root is " +
                    "and want it. Ask for official firmware to be flashed and check again.",
                measurements = measurements,
                falsePositiveCauses = ROOT_CAUSES,
            )
        }

        return when (state) {
            STATE_ORANGE -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "The bootloader is unlocked, so the system is not verified at all.",
                consequence = "Anyone with the phone in their hands can replace the operating " +
                    "system without leaving a trace. Nothing else this app measures can be fully " +
                    "trusted on an unlocked device, and banking apps will often refuse to run.",
                action = "Ask the seller to relock the bootloader and reflash official firmware, " +
                    "then run this check again. If they will not, walk away.",
                measurements = measurements,
                falsePositiveCauses = UNLOCK_CAUSES,
            )

            STATE_RED -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "Verified boot failed on this device.",
                consequence = "The phone booted software that does not match its own signature. " +
                    "That means the system has been altered or is corrupted.",
                action = "Do not buy this phone.",
                measurements = measurements,
                falsePositiveCauses = listOf(
                    "A failed or interrupted system update can leave this state temporarily.",
                ),
            )

            STATE_YELLOW -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The system is signed with a key that is not the manufacturer's.",
                consequence = "A custom operating system is installed. It may be perfectly safe, " +
                    "but it will not receive official updates and some banking apps will refuse " +
                    "to run.",
                action = "Ask what firmware is installed and why, and test a banking app before " +
                    "you pay.",
                measurements = measurements,
                falsePositiveCauses = UNLOCK_CAUSES,
            )

            STATE_GREEN -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = "Not rooted, and the bootloader is locked.",
                measurements = measurements,
            )

            else -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                // No su and no root manager, but the bootloader state is unknown. Reporting a
                // clean pass would overstate what was actually checked.
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.LOW,
                headline = "No root found, but the bootloader state could not be read.",
                consequence = "Root managers can hide themselves, and without the verified-boot " +
                    "state there is no second opinion to fall back on.",
                action = "Check Developer options for OEM unlocking, and test a banking app " +
                    "before you pay.",
                measurements = measurements,
                falsePositiveCauses = listOf(
                    "Many phones simply do not expose the verified-boot state to apps.",
                ),
            )
        }
    }
}
