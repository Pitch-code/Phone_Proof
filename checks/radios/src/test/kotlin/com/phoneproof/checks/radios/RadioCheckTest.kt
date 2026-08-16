package com.phoneproof.checks.radios

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class RadioCheckTest {

    private fun wifi(
        present: Boolean = true,
        stateReadable: Boolean = true,
        enabled: Boolean = true,
        enableAttempted: Boolean = false,
        associated: Boolean = true,
        internetWorking: Boolean = true,
        signalDbm: Int? = -52,
    ) = RadioObservation(
        kind = RadioKind.WIFI,
        present = present,
        stateReadable = stateReadable,
        enabled = enabled,
        enableAttempted = enableAttempted,
        associated = associated,
        internetWorking = internetWorking,
        signalDbm = signalDbm,
    )

    private fun bluetooth(
        present: Boolean = true,
        stateReadable: Boolean = true,
        enabled: Boolean = true,
        enableAttempted: Boolean = false,
    ) = RadioObservation(
        kind = RadioKind.BLUETOOTH,
        present = present,
        stateReadable = stateReadable,
        enabled = enabled,
        enableAttempted = enableAttempted,
    )

    // -------------------------------------------------------- association is the proof, not the scan

    @Test
    fun joining_a_network_passes_at_high_confidence() {
        // The whole design rests on this: a phone that got an address has demonstrably transmitted and
        // received, with no location permission spent to find out.
        val result = RadioCheck.evaluate(wifi())

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.headline).contains("negotiated an address")
    }

    @Test
    fun a_joined_network_with_no_internet_still_passes_because_that_is_the_shops_broadband() {
        // The failure mode to avoid: blaming the phone for a router with an expired bill. Association has
        // already happened, so the radio is proved regardless of what the internet does afterwards.
        val result = RadioCheck.evaluate(wifi(internetWorking = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("usually the network rather than the phone")
    }

    @Test
    fun signal_strength_is_reported_when_the_platform_gives_it_and_omitted_when_it_does_not() {
        // getSignalStrength() is API 29+, so half the supported range returns nothing here. Absent must
        // mean absent rather than a zero that reads as terrible reception.
        assertThat(RadioCheck.evaluate(wifi(signalDbm = -67)).headline).contains("-67 dBm")

        val withoutSignal = RadioCheck.evaluate(wifi(signalDbm = null))
        assertThat(withoutSignal.headline).doesNotContain("dBm")
        assertThat(withoutSignal.measurements.map { it.label }).doesNotContain("Signal")
        assertThat(withoutSignal.outcome).isEqualTo(CheckOutcome.PASS)
    }

    // -------------------------------------------------------- the silences that must not accuse the phone

    @Test
    fun wifi_on_but_unjoined_is_unknown_because_a_shop_may_have_no_network() {
        val result = RadioCheck.evaluate(wifi(associated = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.action).contains("Join any network")
    }

    @Test
    fun a_radio_nobody_switched_on_is_unknown_for_both_kinds() {
        // Reporting a switched-off radio as broken would be the single easiest way to make this app lie.
        for (observation in listOf(wifi(enabled = false), bluetooth(enabled = false))) {
            val result = RadioCheck.evaluate(observation)

            assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
            assertThat(result.headline).contains("says nothing about the radio")
        }
    }

    @Test
    fun an_unreadable_state_blames_the_app_and_not_the_phone() {
        val result = RadioCheck.evaluate(bluetooth(stateReadable = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
        assertThat(result.headline).contains("would not tell the app")
    }

    @Test
    fun absent_hardware_is_a_high_confidence_unknown_rather_than_a_failure() {
        // The phone answered the question; the answer is just strange. That is knowledge, not a broken test.
        val result = RadioCheck.evaluate(wifi(present = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.measurements.first().display).isEqualTo("not reported")
    }

    // -------------------------------------------------------- the one negative finding available

    @Test
    fun a_radio_asked_to_switch_on_that_stays_off_is_a_caution_for_both_kinds() {
        for (observation in listOf(
            wifi(enabled = false, enableAttempted = true),
            bluetooth(enabled = false, enableAttempted = true),
        )) {
            val result = RadioCheck.evaluate(observation)

            assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
            assertThat(result.headline).contains("asked to switch on and is still off")
            // The model demands these of any negative outcome; assert them so the reason is visible here
            // rather than only as an init-block crash.
            assertThat(result.consequence).isNotEmpty()
            assertThat(result.action).isNotEmpty()
            assertThat(result.falsePositiveCauses).isNotEmpty()
        }
    }

    @Test
    fun the_refusal_caution_never_reaches_high_confidence() {
        // A phone mid-way through switching on, and a buyer who dismissed the prompt, look identical from
        // here. Claiming certainty would cost someone a sale over a two-second race.
        val result = RadioCheck.evaluate(bluetooth(enabled = false, enableAttempted = true))

        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun each_kinds_false_positives_mention_that_kinds_own_escape_hatches() {
        val wifiCauses = RadioCheck.evaluate(wifi(enabled = false, enableAttempted = true))
            .falsePositiveCauses.joinToString(" ")
        val bluetoothCauses = RadioCheck.evaluate(bluetooth(enabled = false, enableAttempted = true))
            .falsePositiveCauses.joinToString(" ")

        // A hotspot conflict is a Wi-Fi-only excuse; a dismissed pairing prompt is a Bluetooth one. Sharing
        // one list between the radios would put the wrong excuse in front of the buyer.
        assertThat(wifiCauses).contains("hotspot")
        assertThat(bluetoothCauses).contains("prompt")
        assertThat(wifiCauses).isNotEqualTo(bluetoothCauses)
    }

    @Test
    fun attempting_to_enable_changes_nothing_once_the_radio_is_actually_on() {
        // enableAttempted only matters while the radio is off; it must not leak into the healthy verdicts.
        assertThat(RadioCheck.evaluate(wifi(enableAttempted = true)))
            .isEqualTo(RadioCheck.evaluate(wifi(enableAttempted = false)))
        assertThat(RadioCheck.evaluate(bluetooth(enableAttempted = true)))
            .isEqualTo(RadioCheck.evaluate(bluetooth(enableAttempted = false)))
    }

    // -------------------------------------------------------- bluetooth claims only what it can

    @Test
    fun bluetooth_switched_on_passes_but_only_at_medium_confidence() {
        // A dead controller does not initialise, so this is real evidence — but it is not a pairing, and the
        // confidence has to carry that difference since the tick alone will not.
        val result = RadioCheck.evaluate(bluetooth())

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.headline).contains("Pairing something")
    }

    @Test
    fun bluetooth_never_reports_wifi_only_fields() {
        // Guards a copy-paste future: association and signal belong to Wi-Fi and would be nonsense here.
        val labels = RadioCheck.evaluate(bluetooth()).measurements.map { it.label }

        assertThat(labels).containsNoneOf("Joined a network", "Internet", "Signal")
    }

    // -------------------------------------------------------- wiring

    @Test
    fun the_two_radios_report_under_distinct_stable_ids() {
        // Two results from one screen; equal ids would silently collapse them in the saved report.
        assertThat(RadioCheck.checkId(RadioKind.WIFI)).isEqualTo("hardware.wifi")
        assertThat(RadioCheck.checkId(RadioKind.BLUETOOTH)).isEqualTo("hardware.bluetooth")
        assertThat(RadioCheck.evaluate(wifi()).id).isEqualTo(RadioCheck.WIFI_CHECK_ID)
        assertThat(RadioCheck.evaluate(bluetooth()).id).isEqualTo(RadioCheck.BLUETOOTH_CHECK_ID)
    }

    @Test
    fun every_reachable_state_produces_a_result_the_model_accepts() {
        // CheckResult validates in its init block, so this sweep is what proves no combination of flags can
        // crash the screen. Cheap, and it has caught missing actions on negative outcomes before.
        val kinds = listOf(RadioKind.WIFI, RadioKind.BLUETOOTH)
        val flags = listOf(true, false)
        var built = 0
        for (kind in kinds) {
            for (present in flags) {
                for (readable in flags) {
                    for (enabled in flags) {
                        for (attempted in flags) {
                            for (associated in flags) {
                                for (internet in flags) {
                                    for (signal in listOf(null, -40)) {
                                        val result = RadioCheck.evaluate(
                                            RadioObservation(
                                                kind = kind,
                                                present = present,
                                                stateReadable = readable,
                                                enabled = enabled,
                                                enableAttempted = attempted,
                                                associated = associated,
                                                internetWorking = internet,
                                                signalDbm = signal,
                                            ),
                                        )
                                        assertThat(result.headline).isNotEmpty()
                                        assertThat(result.title).isNotEmpty()
                                        built++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(built).isEqualTo(2 * 2 * 2 * 2 * 2 * 2 * 2 * 2)
    }

    @Test
    fun the_notes_explain_the_permission_tradeoff_the_buyer_would_otherwise_wonder_about() {
        assertThat(RadioCheck.NO_SCAN_NOTE).contains("location")
        assertThat(RadioCheck.BLUETOOTH_LIMIT_NOTE).contains("pair")
    }
}
