package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Which sensors this phone actually has.
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
            headline = "All the sensors a phone should have are present.",
            measurements = measurements,
        )
    }

    private fun yesNo(present: Boolean) = if (present) "yes" else "MISSING"
}
