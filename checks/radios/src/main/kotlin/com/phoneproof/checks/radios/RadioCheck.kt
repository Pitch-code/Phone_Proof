package com.phoneproof.checks.radios

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

enum class RadioKind {
    WIFI,
    BLUETOOTH,
}

/**
 * What one radio was found to be doing.
 *
 * @param present whether the phone claims the hardware at all.
 * @param stateReadable false when the platform refused to say, which is the app's problem and not the phone's.
 * @param enabled whether the radio is switched on. For Bluetooth this is most of the finding: see [RadioCheck].
 * @param enableAttempted whether the buyer actually asked this radio to switch on during the test. This is
 *   what separates "nobody tried" from "it was asked and would not", and only the second is evidence.
 * @param associated Wi-Fi only — joined to a network. This is the proof that the radio works.
 * @param internetWorking whether traffic actually passed. A bonus, never the point.
 * @param signalDbm signal strength where the platform reports it without asking for location.
 */
data class RadioObservation(
    val kind: RadioKind,
    val present: Boolean,
    val stateReadable: Boolean = true,
    val enabled: Boolean = false,
    val enableAttempted: Boolean = false,
    val associated: Boolean = false,
    val internetWorking: Boolean = false,
    val signalDbm: Int? = null,
)

/**
 * Do the Wi-Fi and Bluetooth radios work?
 *
 * ## What counts as proof, and why it is not a scan
 *
 * The obvious test is to scan for networks and count them. It is also the one that costs the buyer a
 * **location permission**, because Android treats a list of nearby Wi-Fi networks as location data — and it
 * is right to. Asking a stranger for their location in order to test a phone would cost this app more trust
 * than the check is worth, and a shop with no Wi-Fi would still produce an empty list from a perfect radio.
 *
 * So the evidence is association instead. **A phone joined to a Wi-Fi network has proved its radio works** —
 * it transmitted, received, negotiated a handshake and got an address. That is a far stronger fact than a scan
 * result and it needs no permission at all. Whether the internet then works is a fact about the shop's
 * broadband, so it is reported and never judged.
 *
 * For Bluetooth the proof is that it **turned on**. A dead Bluetooth controller does not initialise: the
 * toggle flips back, or the state sticks at turning-on. So a radio reporting itself enabled has answered its
 * driver, which is most of what can be established without pairing something — and pairing needs the scan
 * permission this check is avoiding. The copy says so rather than implying more.
 *
 * ## The silences that are not evidence, and the one that is
 *
 * A radio switched off says nothing about the hardware, and neither does a shop with no network to join.
 * Both are reported as "cannot tell" with the thing the buyer should do next, because a phone-testing app
 * that calls a switched-off radio broken is worse than useless.
 *
 * The exception is a radio that was **asked** to switch on and did not, which is what a controller failing to
 * initialise looks like from the outside. That earns a CAUTION rather than a FAIL, because a phone part-way
 * through turning on and a buyer who dismissed the prompt look the same from here.
 */
object RadioCheck {

    const val WIFI_CHECK_ID: String = "hardware.wifi"
    const val BLUETOOTH_CHECK_ID: String = "hardware.bluetooth"

    fun checkId(kind: RadioKind): String = when (kind) {
        RadioKind.WIFI -> WIFI_CHECK_ID
        RadioKind.BLUETOOTH -> BLUETOOTH_CHECK_ID
    }

    private fun title(kind: RadioKind): String = when (kind) {
        RadioKind.WIFI -> "Wi-Fi"
        RadioKind.BLUETOOTH -> "Bluetooth"
    }

    /**
     * Reasons a radio can refuse to switch on without being faulty. Attached to the CAUTION above, which is
     * the only negative outcome this check can reach.
     */
    private val WIFI_FALSE_POSITIVES = listOf(
        "Airplane mode and aggressive battery savers hold the radio off without saying so.",
        "Some phones cannot run Wi-Fi and a hotspot at once, and turn one off to start the other.",
        "A phone part-way through switching on still reports itself off for a second or two.",
        "If the switch was never actually flipped, this is not the phone's doing.",
    )

    private val BLUETOOTH_FALSE_POSITIVES = listOf(
        "Airplane mode and aggressive battery savers hold Bluetooth off on their own.",
        "Some phones take several seconds to finish switching on and report off in the meantime.",
        "Dismissing the system prompt leaves it off, which is a choice rather than a fault.",
    )

    private fun falsePositives(kind: RadioKind): List<String> = when (kind) {
        RadioKind.WIFI -> WIFI_FALSE_POSITIVES
        RadioKind.BLUETOOTH -> BLUETOOTH_FALSE_POSITIVES
    }

    fun evaluate(observation: RadioObservation): CheckResult {
        val kind = observation.kind
        val id = checkId(kind)
        val name = title(kind)

        val measurements = buildList {
            add(Measurement("Hardware", if (observation.present) "present" else "not reported"))
            if (observation.present && observation.stateReadable) {
                add(Measurement("Switched on", if (observation.enabled) "yes" else "no"))
            }
            if (kind == RadioKind.WIFI && observation.enabled) {
                add(
                    Measurement(
                        "Joined a network",
                        if (observation.associated) "yes" else "not yet",
                    ),
                )
                if (observation.associated) {
                    add(
                        Measurement(
                            "Internet",
                            if (observation.internetWorking) "working" else "not passing traffic",
                        ),
                    )
                }
                observation.signalDbm?.let { add(Measurement("Signal", "$it", "dBm")) }
            }
        }

        if (!observation.present) {
            return CheckResult(
                id = id,
                title = name,
                outcome = CheckOutcome.UNKNOWN,
                // HIGH: the phone was asked what hardware it has and it answered. A fact, not a failure to
                // measure — though on a phone it is a strange one.
                confidence = Confidence.HIGH,
                headline = "This phone reports no $name hardware at all.",
                action = "Very unusual on a phone. Check $name appears in the phone's own settings " +
                    "before you go any further.",
                measurements = measurements,
            )
        }

        if (!observation.stateReadable) {
            return CheckResult(
                id = id,
                title = name,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "The phone would not tell the app whether $name is on, so it was not tested.",
                action = "Open the phone's own settings and check $name switches on there.",
                measurements = measurements,
            )
        }

        if (!observation.enabled) {
            // The one negative finding available here: it was asked, and it did not come up.
            if (observation.enableAttempted) {
                return CheckResult(
                    id = id,
                    title = name,
                    outcome = CheckOutcome.CAUTION,
                    // MEDIUM, never HIGH. A phone still finishing its switch-on, and a buyer who dismissed
                    // the prompt, are indistinguishable from a controller that will not start.
                    confidence = Confidence.MEDIUM,
                    headline = "$name was asked to switch on and is still off.",
                    consequence = when (kind) {
                        RadioKind.WIFI ->
                            "If the radio will not start, this phone is on mobile data for everything — " +
                                "no home Wi-Fi, and every update eats your data."
                        RadioKind.BLUETOOTH ->
                            "If the chip will not start, earbuds, speakers, smartwatches and car audio " +
                                "will not connect at all."
                    },
                    action = "Try the switch once more in the phone's own settings and watch it. If it " +
                        "flips straight back off, treat it as a fault and get the price down or walk away.",
                    measurements = measurements,
                    falsePositiveCauses = falsePositives(kind),
                )
            }

            return CheckResult(
                id = id,
                title = name,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "$name is switched off, which says nothing about the radio.",
                action = when (kind) {
                    RadioKind.WIFI ->
                        "Turn Wi-Fi on and join any network — joining one is what proves the radio works."
                    RadioKind.BLUETOOTH ->
                        "Turn Bluetooth on. If it refuses to switch on, or flips straight back off, " +
                            "that is worth knowing."
                },
                measurements = measurements,
            )
        }

        if (kind == RadioKind.BLUETOOTH) {
            return CheckResult(
                id = id,
                title = name,
                outcome = CheckOutcome.PASS,
                // MEDIUM on purpose. Turning on proves the controller answered its driver, which a dead chip
                // does not do — but it is not the same as having paired something, and the headline says so
                // rather than letting a green tick imply a full test.
                confidence = Confidence.MEDIUM,
                headline = "Bluetooth switched on, so the chip started and answered. Pairing something " +
                    "is the only way to prove the rest.",
                measurements = measurements,
            )
        }

        if (!observation.associated) {
            return CheckResult(
                id = id,
                title = name,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "Wi-Fi is on but has not joined a network, so the radio is still unproven.",
                action = "Join any network — the shop's, or a phone hotspot. Getting an address is " +
                    "what proves the radio can transmit and receive.",
                measurements = measurements,
                // No false-positive list: nothing is being alleged, so there is nothing to walk back.
            )
        }

        val internetNote = if (observation.internetWorking) {
            " Traffic is passing, so the connection works end to end."
        } else {
            // Said carefully. Association already proved the radio; no internet is a fact about the shop.
            " No traffic is passing, which is usually the network rather than the phone — the radio has " +
                "already proved itself by joining."
        }
        val signalNote = observation.signalDbm?.let { " Signal $it dBm." } ?: ""

        return CheckResult(
            id = id,
            title = name,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "Joined a Wi-Fi network, which means the radio transmitted, received and " +
                "negotiated an address.$signalNote$internetNote",
            measurements = measurements,
        )
    }

    /**
     * Why there is no scan, for the screen to say once.
     *
     * Worth stating plainly, because a buyer who expects a list of networks will read its absence as the app
     * doing less than it could.
     */
    const val NO_SCAN_NOTE: String =
        "Other apps test this by scanning for nearby networks. Android treats that list as location " +
            "data, so it costs a location permission — and a shop with no Wi-Fi would give an empty " +
            "list from a perfect radio anyway. Joining one network proves far more and asks for nothing."

    /** Kept beside the Bluetooth result, since a green tick there covers less ground than usual. */
    const val BLUETOOTH_LIMIT_NOTE: String =
        "Switching on proves the Bluetooth chip starts and responds, which a dead one does not. It is " +
            "not the same as having paired something — that needs a scanning permission this app does " +
            "not ask for. If you rely on earbuds or a car, pair them before you pay."
}
