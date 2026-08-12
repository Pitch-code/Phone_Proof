package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.model.plural

/**
 * What is left of the battery.
 *
 * The thing buyers care most about on a used phone and the thing sellers most reliably gloss over.
 * It is also where competing apps are least honest: Android exposes no state-of-health and no
 * design capacity, so a "battery health 87%" badge is either read from a per-model table or made
 * up. See [BatteryFacts] for what the platform genuinely provides.
 *
 * So this check reports two real things — the cycle count the fuel gauge keeps, and a full-charge
 * capacity derived from the charge counter — and refuses to convert them into a percentage of
 * original capacity. Where the phone will not report cycles, the answer is UNKNOWN with something
 * useful to do instead, rather than a number that looks measured.
 *
 * Wear is never a FAIL. A battery with 900 cycles is worn, not broken, and a buyer who wants a
 * cheap phone may be perfectly happy to replace it. FAIL is reserved for a cell the platform itself
 * calls dead or failed.
 */
object BatteryCheck {

    const val CHECK_ID: String = "hardware.battery"
    private const val TITLE = "Battery"

    /** Below this, wear is negligible on any generation of cell. */
    const val LIGHT_WEAR_CYCLES: Int = 400

    /**
     * Beyond this, nearly every phone cell has dropped below about 80% of its original capacity.
     *
     * Manufacturers rate cells to retain roughly 80% for somewhere between 500 and 1000 full
     * cycles depending on generation, so 800 is chosen to sit past the optimistic end of that
     * range. Erring high matters more than erring low here: calling a healthy battery worn costs
     * the seller money on a guess.
     */
    const val HEAVY_WEAR_CYCLES: Int = 800

    /** Hot enough to be worth noticing when the phone is doing nothing. */
    const val WARM_IDLE_C: Float = 43f

    private val GAUGE_CAUSES = listOf(
        "Cycle count comes from the phone's own fuel gauge, which some manufacturers reset after a battery replacement.",
        "A recently replaced battery can report the old cell's history, or none at all.",
    )

    private val TEMPERATURE_CAUSES = listOf(
        "A phone warms up while charging, in sunlight, or straight after heavy use.",
        "Temperature is read at one instant and says nothing about the cell on its own.",
    )

    fun evaluate(facts: BatteryFacts): CheckResult {
        val measurements = measurementsFor(facts)

        if (!facts.present) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "No battery was reported by this device.",
                measurements = measurements,
            )
        }

        // The platform calling the cell dead or failed is the only battery signal strong enough to
        // be a FAIL, because it is a fault rather than wear.
        if (facts.health == BatteryHealth.DEAD ||
            facts.health == BatteryHealth.UNSPECIFIED_FAILURE
        ) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "Android reports this battery as failed.",
                consequence = "Expect it to die without warning, or not to charge at all. A " +
                    "replacement is the only fix, and on a glued phone that is not a cheap one.",
                action = "Get a replacement battery quoted before you agree a price, or walk away.",
                measurements = measurements,
                falsePositiveCauses = GAUGE_CAUSES,
            )
        }

        if (facts.health == BatteryHealth.OVER_VOLTAGE) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Android reports the battery voltage as too high.",
                consequence = "This usually points at the charging circuit rather than the cell, " +
                    "and it can get worse.",
                action = "Charge it in front of the seller and watch whether it heats up or stops.",
                measurements = measurements,
                falsePositiveCauses = listOf(
                    "A non-standard charger connected at the time of the reading can cause this.",
                ) + GAUGE_CAUSES,
            )
        }

        if (facts.health == BatteryHealth.OVERHEAT) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Android reports the battery as overheating right now.",
                consequence = "A cell that runs hot ages faster, and heat is how a swollen " +
                    "battery announces itself.",
                action = "Let the phone rest for ten minutes and test again. Press gently on the " +
                    "back and screen edges to feel for swelling.",
                measurements = measurements,
                falsePositiveCauses = TEMPERATURE_CAUSES,
            )
        }

        val cycles = facts.cycleCount

        if (cycles == null) {
            // The common case on older or tighter-lipped phones. Saying so and handing over a
            // manual method is worth more than a fabricated percentage.
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "This phone does not report how many charge cycles it has been through.",
                measurements = measurements,
            )
        }

        val hot = facts.temperatureC != null &&
            facts.temperatureC >= WARM_IDLE_C &&
            !facts.charging

        if (hot) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.LOW,
                headline = "The battery is at ${formatTemperature(facts.temperatureC!!)} while " +
                    "the phone is idle.",
                consequence = "Something is working hard in the background, or the cell is " +
                    "struggling. Either way it will drain faster than it should.",
                action = "Let it sit for ten minutes and run this again before judging it.",
                measurements = measurements,
                falsePositiveCauses = TEMPERATURE_CAUSES,
            )
        }

        return when {
            cycles >= HEAVY_WEAR_CYCLES -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.HIGH,
                headline = "This battery has been through ${plural(cycles, "charge cycle")}.",
                consequence = "Past about $HEAVY_WEAR_CYCLES cycles most batteries hold " +
                    "noticeably less than when new. Expect to charge it more than once a day.",
                action = "Budget for a replacement battery and take that off the price.",
                measurements = measurements,
                falsePositiveCauses = GAUGE_CAUSES,
            )

            cycles >= LIGHT_WEAR_CYCLES -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.MEDIUM,
                headline = "Normal wear for its age: ${plural(cycles, "charge cycle")}.",
                consequence = "It is past halfway through a typical battery's life, but nothing " +
                    "here suggests a fault.",
                action = "Watch the charge level while you inspect the rest of the phone.",
                measurements = measurements,
            )

            else -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = "Little wear: only ${plural(cycles, "charge cycle")}.",
                measurements = measurements,
            )
        }
    }

    private fun measurementsFor(facts: BatteryFacts): List<Measurement> = buildList {
        add(Measurement("Charge cycles", facts.cycleCount?.toString() ?: "not reported"))

        facts.chargePercent?.let { add(Measurement("Charge now", "$it", "%")) }

        // Shown only when the level makes it meaningful, and labelled as measured rather than
        // quoted, so it is never mistaken for the manufacturer's rating.
        if (facts.estimateTrustworthy) {
            add(Measurement("Full charge, measured", "${facts.estimatedFullChargeMah}", "mAh"))
        }

        facts.temperatureC?.let { add(Measurement("Temperature", formatTemperature(it))) }
        facts.voltageMv?.let { add(Measurement("Voltage", "$it", "mV")) }
        facts.technology?.takeIf { it.isNotBlank() }?.let { add(Measurement("Type", it)) }
        add(Measurement("Android's own verdict", facts.health.label))
    }

    /** One decimal place. A battery reported to five decimals invites false precision. */
    internal fun formatTemperature(celsius: Float): String =
        "${String.format("%.1f", celsius)} °C"
}

private val BatteryHealth.label: String
    get() = when (this) {
        BatteryHealth.GOOD -> "good"
        BatteryHealth.OVERHEAT -> "overheating"
        BatteryHealth.DEAD -> "dead"
        BatteryHealth.OVER_VOLTAGE -> "over voltage"
        BatteryHealth.COLD -> "cold"
        BatteryHealth.UNSPECIFIED_FAILURE -> "failed"
        BatteryHealth.UNKNOWN -> "not reported"
    }
