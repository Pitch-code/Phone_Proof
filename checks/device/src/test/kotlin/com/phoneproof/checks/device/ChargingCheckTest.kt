package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class ChargingCheckTest {

    private fun charging(
        watts: Double = 12.0,
        percent: Int = 45,
        plug: PlugType = PlugType.AC,
        dropouts: Int = 0,
        reportsCurrent: Boolean = true,
    ): ChargeTrace {
        val voltage = 4_200
        val milliamps = (watts / (voltage / 1000.0) * 1000).toInt()
        return ChargeTrace(
            attempt = ChargeAttempt.MEASURED,
            plugType = plug,
            batteryPercent = percent,
            voltageMillivolts = voltage,
            currentMilliamps = if (reportsCurrent) milliamps else null,
            temperatureCelsius = 30.5,
            dropouts = dropouts,
            sampleSeconds = 20,
        )
    }

    // ------------------------------------------------------------------ the dropout, which is the point

    @Test
    fun a_charger_that_lets_go_outranks_the_speed() {
        // A phone that charges fast in a shop and stops overnight is worse news than one that charges
        // slowly, so the dropout has to be the headline even at a healthy wattage.
        val result = ChargingCheck.evaluate(charging(watts = 18.0, dropouts = 2))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("stopped and started 2 times")
    }

    @Test
    fun the_dropout_consequence_describes_the_morning_a_buyer_finds_out() {
        val result = ChargingCheck.evaluate(charging(dropouts = 1))

        assertThat(result.consequence).contains("flat phone")
        // Cable and lint before the phone, because both are far likelier than a broken socket.
        assertThat(result.falsePositiveCauses.first()).contains("cable")
    }

    @Test
    fun a_single_dropout_is_enough_to_report() {
        // A sound port does not drop once in twenty seconds. Requiring two would be requiring the fault to
        // be bad enough that the buyer would have found it anyway.
        assertThat(ChargingCheck.evaluate(charging(dropouts = 1)).outcome)
            .isEqualTo(CheckOutcome.CAUTION)
        assertThat(ChargingCheck.evaluate(charging(dropouts = 1)).headline).contains("1 time")
    }

    // ------------------------------------------------------------------ never measured

    @Test
    fun no_cable_is_not_a_fault_and_is_worth_going_back_for() {
        val result = ChargingCheck.evaluate(ChargeTrace(ChargeAttempt.NOT_PLUGGED))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        // This is the one check a buyer cannot do after paying, so the action pushes rather than shrugs.
        assertThat(result.action).contains("Worth going back for")
    }

    @Test
    fun connected_and_not_charging_is_the_finding_that_matters() {
        val result = ChargingCheck.evaluate(
            ChargeTrace(
                attempt = ChargeAttempt.PLUGGED_NOT_CHARGING,
                plugType = PlugType.AC,
                batteryPercent = 40,
            ),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.action).contains("different cable")
    }

    @Test
    fun a_full_battery_cannot_be_timed_and_is_not_a_fault() {
        val result = ChargingCheck.evaluate(
            ChargeTrace(
                attempt = ChargeAttempt.BATTERY_FULL,
                plugType = PlugType.AC,
                batteryPercent = 100,
            ),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.action).contains("below 80")
    }

    // ------------------------------------------------------------------ speed, reported carefully

    @Test
    fun healthy_charging_passes_and_states_the_wattage() {
        val result = ChargingCheck.evaluate(charging(watts = 15.0))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.headline).contains("15.0 W")
    }

    @Test
    fun a_trickle_is_a_caution_whatever_the_charger_is() {
        // Below the old USB 2.0 ceiling of 2.5 W the phone is drawing less than a computer socket provides,
        // which is about power arriving at all rather than about fast charging.
        val result = ChargingCheck.evaluate(charging(watts = 1.4))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("less than a computer")
        assertThat(result.action).contains("known-good charger")
    }

    @Test
    fun a_five_watt_charger_on_an_old_phone_is_not_a_fault() {
        // The commonest false accusation available here. 5 W is a perfectly ordinary charger.
        assertThat(ChargingCheck.evaluate(charging(watts = 5.0)).outcome)
            .isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun charging_over_usb_says_the_port_is_the_limit_not_the_phone() {
        val result = ChargingCheck.evaluate(charging(watts = 4.5, plug = PlugType.USB))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("caps the speed itself")
    }

    @Test
    fun a_nearly_full_battery_is_flagged_as_tapering_rather_than_slow() {
        // Every phone slows above 80 percent on purpose. Reporting that as its top speed would be wrong in
        // the direction that costs a seller money.
        val result = ChargingCheck.evaluate(charging(watts = 6.0, percent = 92))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("not its top speed")
    }

    @Test
    fun wireless_charging_is_not_compared_against_a_cable() {
        val result = ChargingCheck.evaluate(charging(watts = 5.0, plug = PlugType.WIRELESS))

        assertThat(result.headline).contains("slower than a cable")
    }

    @Test
    fun a_phone_that_will_not_report_current_still_passes_but_claims_less() {
        val result = ChargingCheck.evaluate(charging(reportsCurrent = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        // MEDIUM, because charging is confirmed and the speed genuinely is not known. A HIGH pass here would
        // claim ground the measurement never covered.
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.headline).contains("speed is unknown")
        assertThat(result.measurements.map { it.label }).doesNotContain("Power drawn")
    }

    @Test
    fun the_speed_note_puts_the_charger_before_the_phone() {
        // A buyer comparing this against the 33 W on the box needs to know the charger in their hand is most
        // of the answer.
        assertThat(ChargingCheck.SPEED_NOTE).contains("not what it is capable of")
        assertThat(ChargingCheck.SPEED_NOTE).contains("slow charger")
    }

    // ------------------------------------------------------------------ arithmetic and reporting

    @Test
    fun watts_are_volts_times_amps() {
        val trace = ChargeTrace(
            attempt = ChargeAttempt.MEASURED,
            voltageMillivolts = 4_000,
            currentMilliamps = 2_500,
        )

        assertThat(trace.watts).isWithin(0.01).of(10.0)
    }

    @Test
    fun a_phone_that_reports_no_current_reports_no_wattage_rather_than_zero() {
        // Zero watts would read as "nothing is arriving", which is a different and much worse claim than
        // "this phone does not say".
        val trace = ChargeTrace(attempt = ChargeAttempt.MEASURED, voltageMillivolts = 4_000)

        assertThat(trace.watts).isNull()
    }

    @Test
    fun the_report_always_names_what_it_was_plugged_into() {
        // Without it the wattage is uninterpretable, and a buyer arguing about charging speed needs to be
        // able to say what was on the other end of the cable.
        listOf(
            ChargeTrace(ChargeAttempt.NOT_PLUGGED),
            ChargeTrace(ChargeAttempt.PLUGGED_NOT_CHARGING, plugType = PlugType.AC),
            charging(),
        ).forEach {
            assertThat(ChargingCheck.evaluate(it).measurements.map { m -> m.label })
                .contains("Plugged into")
        }
    }

    @Test
    fun no_charging_figures_are_shown_when_nothing_was_measured() {
        val labels = ChargingCheck.evaluate(ChargeTrace(ChargeAttempt.NOT_PLUGGED))
            .measurements.map { it.label }

        assertThat(labels).doesNotContain("Power drawn")
        assertThat(labels).doesNotContain("Charger dropouts")
    }

    @Test
    fun every_outcome_tells_the_buyer_what_to_do_next() {
        listOf(
            ChargeTrace(ChargeAttempt.NOT_PLUGGED),
            ChargeTrace(ChargeAttempt.PLUGGED_NOT_CHARGING),
            ChargeTrace(ChargeAttempt.BATTERY_FULL),
            charging(dropouts = 1),
            charging(watts = 1.0),
        ).forEach { assertThat(ChargingCheck.evaluate(it).action).isNotEmpty() }
    }
}
