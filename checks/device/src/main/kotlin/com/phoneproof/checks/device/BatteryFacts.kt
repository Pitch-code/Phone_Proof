package com.phoneproof.checks.device

/**
 * The platform's own coarse verdict on the cell, mirroring `BatteryManager.BATTERY_HEALTH_*`.
 *
 * Re-declared rather than imported so this module stays free of Android types. Treat it as a
 * smoke alarm, not a health percentage: on a badly worn but functioning battery Android still
 * reports [GOOD], so GOOD on its own is not evidence that a battery has life left in it.
 */
enum class BatteryHealth {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    COLD,
    UNSPECIFIED_FAILURE,
    UNKNOWN,
}

/**
 * What Android will actually tell an ordinary app about the battery.
 *
 * This list is short for a reason, and the reason is worth writing down because it dictates what
 * this check can honestly claim. Checked against the android-36 and android-37.1 SDKs: there is no
 * public state-of-health, no design capacity, and no manufacturing date. `PowerProfile` reflection
 * would appear to supply design capacity and is a known anti-pattern that returns a build-time
 * constant rather than a measurement — it is not used here.
 *
 * The consequence is that **a "battery health %" cannot be computed**, because the original
 * capacity to divide by is not available. Every app showing one is either reading a per-model table
 * or inventing it. This check reports cycle count and a measured full-charge capacity instead, and
 * says plainly that the percentage is unavailable.
 */
data class BatteryFacts(
    /**
     * `BatteryManager.EXTRA_CYCLE_COUNT`, added in API 34. Null when the phone does not report it,
     * which is common: it depends on the OEM's fuel gauge, not just the Android version.
     */
    val cycleCount: Int? = null,
    val health: BatteryHealth = BatteryHealth.UNKNOWN,
    /** Charge right now, 0..100, from EXTRA_LEVEL scaled by EXTRA_SCALE. */
    val chargePercent: Int? = null,
    /** `BATTERY_PROPERTY_CHARGE_COUNTER`: charge remaining in microamp-hours. */
    val chargeCounterMicroAh: Long? = null,
    /** Already converted from EXTRA_TEMPERATURE's tenths of a degree. */
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    /** EXTRA_TECHNOLOGY, e.g. "Li-ion". */
    val technology: String? = null,
    val present: Boolean = true,
    val charging: Boolean = false,
) {
    /**
     * Capacity at full charge in mAh, derived from the charge counter and the current level.
     *
     * A real derivation from two measured values, not a lookup. It is only meaningful while the
     * level is in a sane band — see [estimateTrustworthy] — because fuel gauges are least accurate
     * at the extremes and the division amplifies that error.
     */
    val estimatedFullChargeMah: Int?
        get() {
            val counter = chargeCounterMicroAh ?: return null
            val percent = chargePercent ?: return null
            if (counter <= 0 || percent < MIN_PERCENT_FOR_ESTIMATE) return null
            return ((counter / 1000.0) * (100.0 / percent)).toInt()
        }

    /**
     * Whether [estimatedFullChargeMah] is worth showing to a buyer.
     *
     * Near empty the division blows up small errors; near full, chargers taper and the gauge
     * saturates. Outside this band the number is reported as unavailable rather than dressed up.
     */
    val estimateTrustworthy: Boolean
        get() {
            val percent = chargePercent ?: return false
            return estimatedFullChargeMah != null && percent in TRUSTWORTHY_PERCENT_RANGE
        }

    companion object {
        internal const val MIN_PERCENT_FOR_ESTIMATE = 5
        internal val TRUSTWORTHY_PERCENT_RANGE = 20..95
    }
}
