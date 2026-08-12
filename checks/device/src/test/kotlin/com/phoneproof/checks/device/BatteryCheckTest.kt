package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class BatteryCheckTest {

    private fun facts(
        cycleCount: Int? = 120,
        health: BatteryHealth = BatteryHealth.GOOD,
        chargePercent: Int? = 80,
        chargeCounterMicroAh: Long? = 4_000_000,
        temperatureC: Float? = 30f,
        voltageMv: Int? = 4_100,
        technology: String? = "Li-ion",
        present: Boolean = true,
        charging: Boolean = false,
    ) = BatteryFacts(
        cycleCount = cycleCount,
        health = health,
        chargePercent = chargePercent,
        chargeCounterMicroAh = chargeCounterMicroAh,
        temperatureC = temperatureC,
        voltageMv = voltageMv,
        technology = technology,
        present = present,
        charging = charging,
    )

    // ---------------------------------------------------------------- faults

    @Test
    fun `a battery the platform calls dead fails`() {
        val result = BatteryCheck.evaluate(facts(health = BatteryHealth.DEAD))

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.action).isNotEmpty()
        assertThat(result.falsePositiveCauses).isNotEmpty()
    }

    @Test
    fun `an unspecified failure also fails`() {
        assertThat(BatteryCheck.evaluate(facts(health = BatteryHealth.UNSPECIFIED_FAILURE)).outcome)
            .isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `over voltage is a caution, not a failure`() {
        // Points at the charging circuit rather than the cell, and can be caused by the charger
        // attached at the time.
        val result = BatteryCheck.evaluate(facts(health = BatteryHealth.OVER_VOLTAGE))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `overheating is a caution and says how to retest`() {
        val result = BatteryCheck.evaluate(facts(health = BatteryHealth.OVERHEAT))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.action).contains("ten minutes")
    }

    // ------------------------------------------------------------------ wear

    @Test
    fun `a lightly used battery passes with full confidence`() {
        val result = BatteryCheck.evaluate(facts(cycleCount = 120))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `mid life wear passes but lowers confidence and says so`() {
        val result = BatteryCheck.evaluate(facts(cycleCount = 500))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.consequence).isNotNull()
    }

    @Test
    fun `heavy wear is a caution with price advice, never a failure`() {
        // A worn battery is not a broken one. Someone buying cheap may be happy to replace it, and
        // calling it FAIL would cost the seller money over ordinary ageing.
        val result = BatteryCheck.evaluate(facts(cycleCount = 950))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
        assertThat(result.action).contains("price")
    }

    @Test
    fun `the wear thresholds are inclusive at their boundaries`() {
        assertThat(BatteryCheck.evaluate(facts(cycleCount = 399)).confidence)
            .isEqualTo(Confidence.HIGH)
        assertThat(BatteryCheck.evaluate(facts(cycleCount = 400)).confidence)
            .isEqualTo(Confidence.MEDIUM)
        assertThat(BatteryCheck.evaluate(facts(cycleCount = 799)).outcome)
            .isEqualTo(CheckOutcome.PASS)
        assertThat(BatteryCheck.evaluate(facts(cycleCount = 800)).outcome)
            .isEqualTo(CheckOutcome.CAUTION)
    }

    // --------------------------------------------------------------- unknown

    @Test
    fun `a phone that will not report cycles says so instead of guessing`() {
        val result = BatteryCheck.evaluate(facts(cycleCount = null))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("does not report")
    }

    @Test
    fun `a good health reading alone is not treated as a pass`() {
        // Android reports GOOD on badly worn cells, so GOOD without a cycle count is not evidence
        // that a battery has life left. This is the trap that would let the app flatter every phone.
        val result = BatteryCheck.evaluate(
            facts(cycleCount = null, health = BatteryHealth.GOOD),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `no battery present is unknown rather than a fault`() {
        val result = BatteryCheck.evaluate(facts(present = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `a fault is reported even when cycles are unavailable`() {
        // The order matters: a dead battery must not be downgraded to "cannot tell" just because
        // the phone is also silent about cycle count.
        val result = BatteryCheck.evaluate(
            facts(cycleCount = null, health = BatteryHealth.DEAD),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    // ----------------------------------------------------------- temperature

    @Test
    fun `hot while idle is a low confidence caution`() {
        val result = BatteryCheck.evaluate(facts(temperatureC = 45f, charging = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `hot while charging is not held against the phone`() {
        // Every phone warms up on a charger. Flagging that would fire on a healthy handset the
        // seller happened to top up before the viewing.
        val result = BatteryCheck.evaluate(facts(temperatureC = 45f, charging = true))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `a cold battery is not a caution on its own`() {
        val result = BatteryCheck.evaluate(facts(health = BatteryHealth.COLD, temperatureC = 4f))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    // ---------------------------------------------------------- measurements

    @Test
    fun `the report never claims a battery health percentage`() {
        // The central honesty guarantee of this check. Android exposes no design capacity, so a
        // percentage of original capacity cannot be computed and must never appear.
        val result = BatteryCheck.evaluate(facts())
        val text = (
            result.measurements.joinToString(" ") { "${it.label} ${it.display}" } +
                " " + result.headline + " " + result.consequence.orEmpty()
            ).lowercase()

        assertThat(text).doesNotContain("health %")
        assertThat(text).doesNotContain("state of health")
        assertThat(result.measurements.map { it.label }).doesNotContain("Battery health")
    }

    @Test
    fun `cycle count is always reported, even when absent`() {
        val present = BatteryCheck.evaluate(facts(cycleCount = 300))
        val absent = BatteryCheck.evaluate(facts(cycleCount = null))

        assertThat(present.measurements.first { it.label == "Charge cycles" }.display)
            .isEqualTo("300")
        assertThat(absent.measurements.first { it.label == "Charge cycles" }.display)
            .isEqualTo("not reported")
    }

    @Test
    fun `a measured full charge capacity is shown when the level makes it meaningful`() {
        // 4,000,000 µAh at 80% implies a 5,000 mAh cell.
        val result = BatteryCheck.evaluate(
            facts(chargeCounterMicroAh = 4_000_000, chargePercent = 80),
        )

        val measured = result.measurements.first { it.label == "Full charge, measured" }
        assertThat(measured.display).isEqualTo("5000 mAh")
    }

    @Test
    fun `the capacity estimate is withheld at extreme charge levels`() {
        // Fuel gauges are least accurate near empty and near full, and dividing by a small number
        // amplifies the error. Withholding beats publishing a wrong capacity.
        val nearlyEmpty = BatteryCheck.evaluate(
            facts(chargeCounterMicroAh = 200_000, chargePercent = 4),
        )
        val nearlyFull = BatteryCheck.evaluate(
            facts(chargeCounterMicroAh = 4_900_000, chargePercent = 99),
        )

        assertThat(nearlyEmpty.measurements.map { it.label }).doesNotContain("Full charge, measured")
        assertThat(nearlyFull.measurements.map { it.label }).doesNotContain("Full charge, measured")
    }

    @Test
    fun `temperature is shown to one decimal place`() {
        val result = BatteryCheck.evaluate(facts(temperatureC = 31.27f))

        assertThat(result.measurements.first { it.label == "Temperature" }.display)
            .isEqualTo("31.3 °C")
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        assertThat(BatteryCheck.CHECK_ID).isEqualTo("hardware.battery")
    }

    @Test
    fun `the category namespace matches the hardware group`() {
        // The card's colour is derived from this prefix, so a typo here would silently retint it.
        assertThat(BatteryCheck.CHECK_ID.substringBefore('.')).isEqualTo("hardware")
    }
}
