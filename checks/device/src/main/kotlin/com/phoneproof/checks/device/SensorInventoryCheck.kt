package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Which sensors this phone says it has, and nothing more than that.
 *
 * ## What this check cannot do
 *
 * It asks the platform for the sensor list and reports it. That is a real question with a real
 * commercial answer — plenty of budget phones ship without a gyroscope — but it is only ever a
 * question about the parts list. **Every sensor on a water-damaged handset is still on the list.** A
 * dead-but-present sensor sails through here, which for a while made the pass headline the most
 * misleading sentence in the app: "all the sensors a phone should have are present" reads as "the
 * sensors are fine".
 *
 * So the wording is now scoped to what is actually known, and the live test — `checks:sensors`, which
 * watches the readings while the buyer tilts and covers the phone — is named as the thing that answers
 * the other half. This check stays because it needs no gesture and returns instantly during the
 * automatic scan, and because absence and deadness are genuinely different findings with different
 * consequences.
 *
 * The gyroscope is the one that matters commercially. Large numbers of budget phones ship without
 * a real one and simulate rotation from the accelerometer, which is why some games feel wrong on
 * them and why AR features silently do nothing. It is invisible in a spec sheet argument and
 * trivially checkable here — and a buyer who plays games has a concrete reason to walk away or
 * negotiate.
 *
 * A missing proximity sensor is the other one worth flagging: without it the screen stays awake
 * against your ear during calls.
 */
object SensorInventoryCheck {

    const val CHECK_ID: String = "hardware.sensors"
    private const val TITLE = "Sensors"

    fun evaluate(facts: DeviceFacts): CheckResult {
        if (!facts.sensorsReadable) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "The sensor list could not be read on this device.",
                measurements = listOf(Measurement("Sensors", "not readable")),
            )
        }

        val measurements = listOf(
            Measurement("Total sensors", "${facts.sensors.size}"),
            Measurement("Gyroscope", yesNo(facts.hasGyroscope)),
            Measurement("Accelerometer", yesNo(facts.hasAccelerometer)),
            Measurement("Compass", yesNo(facts.hasMagnetometer)),
            Measurement("Proximity", yesNo(facts.hasProximity)),
            Measurement("Light", yesNo(facts.hasLight)),
        )

        val missing = buildList {
            if (!facts.hasGyroscope) add("gyroscope")
            if (!facts.hasProximity) add("proximity sensor")
            if (!facts.hasAccelerometer) add("accelerometer")
        }

        // No accelerometer at all is not a missing feature, it is a broken or faked phone.
        if (!facts.hasAccelerometer) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "No accelerometer. Every phone has one.",
                consequence = "Screen rotation and step counting cannot work, and a phone missing " +
                    "the most basic sensor of all is either faulty or not what it claims to be.",
                action = "Do not buy this without an explanation you can verify.",
                measurements = measurements,
                falsePositiveCauses = listOf(
                    "A permission-restricted or heavily customised build can hide the sensor list.",
                ),
            )
        }

        if (missing.isNotEmpty()) {
            val names = missing.joinToString(" and ")
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.HIGH,
                headline = "No $names.",
                consequence = if (!facts.hasGyroscope) {
                    "Without a gyroscope, motion controls in games and any AR feature will not " +
                        "work properly no matter how good the rest of the phone is. Many budget " +
                        "models simply omit it."
                } else {
                    "Without a proximity sensor the screen will not switch off against your ear " +
                        "during calls, so you will hang up with your cheek."
                },
                action = "If that matters to you, use it to negotiate — or choose another model.",
                measurements = measurements,
                falsePositiveCauses = listOf(
                    "Some phones expose an equivalent sensor under a non-standard type.",
                    "A custom ROM can under-report the sensor list.",
                ),
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            // Says "listed", not "working", and then says so again in plain words. The phone is being
            // taken at its word here, and a buyer must not read a green tick on a parts list as a
            // verdict on whether any of those parts still respond.
            headline = "Every sensor a phone should have is listed. Whether they still work is a " +
                "separate question — the sensor test answers that one.",
            measurements = measurements,
        )
    }

    private fun yesNo(present: Boolean) = if (present) "yes" else "MISSING"
}
