package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import kotlin.math.roundToInt

/**
 * What the display actually is, as opposed to what the listing says.
 *
 * Refresh rate is the common lie in a resale listing: a phone sold as "120 Hz" that only ever
 * reports 60. The platform enumerates the modes it genuinely supports, so this needs no catalogue
 * — and a mismatch between the highest supported rate and the rate currently in use is worth
 * saying out loud, because it usually means a power-saving setting is quietly capping the screen.
 */
object DisplayCheck {

    const val CHECK_ID: String = "hardware.display"
    private const val TITLE = "Display"

    /** Rates above this are marketed as "high refresh". */
    const val HIGH_REFRESH_THRESHOLD_HZ: Float = 89f

    fun evaluate(facts: DeviceFacts): CheckResult {
        val width = facts.widthPx
        val height = facts.heightPx
        val maxHz = facts.maxRefreshRateHz
        val currentHz = facts.currentRefreshRateHz

        if (width == null || height == null || maxHz == null) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "Display details could not be read on this device.",
                measurements = listOf(Measurement("Display", "not readable")),
            )
        }

        val measurements = buildList {
            add(Measurement("Resolution", "$width x $height", "px"))
            facts.densityDpi?.let { add(Measurement("Density", "$it", "dpi")) }
            add(Measurement("Highest supported", "${maxHz.roundToInt()}", "Hz"))
            currentHz?.let { add(Measurement("Running at", "${it.roundToInt()}", "Hz")) }
            if (facts.supportedRefreshRatesHz.size > 1) {
                add(
                    Measurement(
                        "Modes",
                        facts.supportedRefreshRatesHz.sorted()
                            .joinToString("/") { it.roundToInt().toString() },
                        "Hz",
                    ),
                )
            }
        }

        // Capped screen: supported high rate, running low. Worth a nudge, not an accusation —
        // it is a setting, not a fault, and the buyer can fix it in a minute.
        if (currentHz != null &&
            maxHz >= HIGH_REFRESH_THRESHOLD_HZ &&
            currentHz < maxHz - 5f
        ) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.HIGH,
                headline = "The screen supports ${maxHz.roundToInt()} Hz but is running at " +
                    "${currentHz.roundToInt()} Hz.",
                consequence = "You are not getting the smoothness the phone is capable of. This is " +
                    "usually a battery-saver or a display setting rather than a fault.",
                action = "Check Settings, Display, Refresh rate — then judge the screen with it on.",
                measurements = measurements,
                falsePositiveCauses = listOf(
                    "Battery saver caps the refresh rate automatically.",
                    "Android lowers the rate for static content to save power, which is normal.",
                ),
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = if (maxHz >= HIGH_REFRESH_THRESHOLD_HZ) {
                "${width} x ${height}, high refresh at ${maxHz.roundToInt()} Hz."
            } else {
                "${width} x ${height} at ${maxHz.roundToInt()} Hz."
            },
            measurements = measurements,
        )
    }
}
