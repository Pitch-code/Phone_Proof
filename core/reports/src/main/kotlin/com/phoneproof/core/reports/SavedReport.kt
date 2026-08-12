package com.phoneproof.core.reports

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import kotlinx.serialization.Serializable

/**
 * One completed inspection, kept on the device.
 *
 * The app was called PhoneProof while producing no proof anyone could keep: results lived in a
 * ViewModel and vanished when the app closed. A buyer in a shop needs to show the seller a result,
 * and to compare the phone in their hand against the one they looked at yesterday.
 *
 * [deviceLabel] and [androidLabel] are stored rather than re-read, because a saved report describes
 * the phone it was taken on — which is usually not the phone it is later read on.
 */
@Serializable
data class SavedReport(
    /** Sortable and filename-safe. See [ReportStore.newId]. */
    val id: String,
    val createdAtEpochMs: Long,
    /** "realme RMX5110". */
    val deviceLabel: String,
    /** "Android 16 (API 36)". */
    val androidLabel: String,
    val results: List<CheckResult>,
) {
    val problemCount: Int
        get() = results.count {
            it.outcome == CheckOutcome.FAIL || it.outcome == CheckOutcome.CAUTION
        }

    val passCount: Int get() = results.count { it.outcome == CheckOutcome.PASS }

    val unknownCount: Int get() = results.count { it.outcome == CheckOutcome.UNKNOWN }

    /**
     * The most serious thing found, which is what the list row leads with.
     *
     * UNKNOWN deliberately outranks PASS: "we could not tell" must not be summarised as a clean
     * bill of health, or a report full of things the app could not measure would look like a phone
     * with nothing wrong with it.
     */
    val worstOutcome: CheckOutcome
        get() = when {
            results.any { it.outcome == CheckOutcome.FAIL } -> CheckOutcome.FAIL
            results.any { it.outcome == CheckOutcome.CAUTION } -> CheckOutcome.CAUTION
            results.any { it.outcome == CheckOutcome.UNKNOWN } -> CheckOutcome.UNKNOWN
            else -> CheckOutcome.PASS
        }
}
