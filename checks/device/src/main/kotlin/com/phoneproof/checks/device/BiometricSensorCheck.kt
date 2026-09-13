package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Whether the fingerprint reader or face unlock works.
 *
 * A dead fingerprint sensor is a common, expensive fault on a used phone — a cracked-open repair, water,
 * or a cheap replacement screen with the reader under it often kills it — and nobody checks it in a shop,
 * because unlocking needs an enrolled finger and the buyer's is not on the phone.
 *
 * ## What can and cannot be known without the owner's finger
 *
 * The app runs on the seller's phone, so it cannot authenticate — that would need the seller's fingerprint.
 * What it *can* read, with no permission and no prompt, is the state of the biometric subsystem, and that
 * answers most of the question honestly:
 *
 *  - **Something is enrolled and the phone says it is ready.** A biometric cannot be enrolled on a dead
 *    sensor, and "available now" means the subsystem is up — so the reader is working. The only thing more
 *    certain is the owner putting a finger on it, which is a fair thing to ask them to do.
 *  - **The hardware is there but nothing is enrolled.** Common — plenty of people unlock with a PIN. It
 *    cannot be proven here, so this says so and asks for a finger to be added rather than guessing.
 *  - **The hardware is there but the phone will not use it.** Sometimes a temporary lockout, sometimes a
 *    failing sensor — a caution, never a condemnation, because the two look identical from here.
 *  - **No biometric hardware at all.** Either the model never had one or a fitted sensor is so faulty the
 *    phone cannot see it. This check cannot tell those apart and does not pretend to.
 *
 * The verdict is therefore a pure function of one enum, which is exactly why it is testable without a
 * device: the Android-specific part is reading the enum, done by BiometricProbe in `core:device`.
 */
object BiometricSensorCheck {

    /**
     * The biometric subsystem's state, as the platform reports it — one value per meaningful outcome of
     * `BiometricManager.canAuthenticate`, so the mapping from Android's result codes lives at the edge and
     * the verdict logic stays pure.
     */
    enum class BiometricAvailability {
        /** Something is enrolled and the phone reports it usable now. */
        READY,

        /** Hardware present, but no fingerprint or face is set up. */
        NOT_ENROLLED,

        /** Hardware present, but the phone will not use it right now. */
        UNAVAILABLE,

        /** A security update is required before biometrics can be used. */
        UPDATE_REQUIRED,

        /** No fingerprint or face hardware the phone can see. */
        NO_HARDWARE,

        /** The state could not be determined. */
        UNKNOWN_STATUS,
    }

    const val CHECK_ID: String = "hardware.biometric"
    private const val TITLE = "Fingerprint & face unlock"

    fun evaluate(availability: BiometricAvailability): CheckResult = when (availability) {
        BiometricAvailability.READY -> CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            // Not HIGH: the phone says the reader is ready and something is enrolled on it, which it could
            // not be if the sensor were dead — strong, but short of a live scan by the owner.
            confidence = Confidence.MEDIUM,
            headline = "Fingerprint or face unlock is set up and the phone reports it ready.",
            consequence = "A biometric can only be enrolled on a working reader, and the phone says it is " +
                "available right now — so the sensor is functioning. The owner putting a finger on it is " +
                "the only thing more certain.",
            measurements = listOf(Measurement("Fingerprint / face", "set up, ready")),
        )

        BiometricAvailability.NOT_ENROLLED -> CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.UNKNOWN,
            confidence = Confidence.HIGH,
            headline = "The hardware is there, but no fingerprint or face is set up.",
            consequence = "The phone will not let a biometric be tested until one is enrolled, so whether " +
                "the reader works cannot be proven right now.",
            action = "Ask the seller to add a fingerprint — Settings, then Security — and run the scan " +
                "again, or unlock it together to see it work.",
            measurements = listOf(Measurement("Fingerprint / face", "present, none set up")),
        )

        BiometricAvailability.UNAVAILABLE -> CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.CAUTION,
            confidence = Confidence.MEDIUM,
            headline = "The fingerprint or face hardware is present but the phone will not use it now.",
            consequence = "This is sometimes temporary — too many failed attempts locks it for a while — " +
                "but it can also mean a failing reader you would fight every time you unlock the phone.",
            action = "Lock and unlock the phone, wait a minute, and scan again. If it stays unavailable, " +
                "treat it as a fault: get it quoted and take that off, or walk away.",
            measurements = listOf(Measurement("Fingerprint / face", "present, unavailable")),
            falsePositiveCauses = listOf(
                "A temporary lockout after several failed unlock attempts, which clears on its own.",
                "The reader being briefly busy just after the phone has booted.",
                "A pending security update the phone needs before it will use biometrics.",
            ),
        )

        BiometricAvailability.UPDATE_REQUIRED -> CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.CAUTION,
            confidence = Confidence.MEDIUM,
            headline = "Biometrics need a security update before they can be used.",
            consequence = "The phone is behind on updates, and fingerprint or face unlock stays switched " +
                "off until it catches up.",
            action = "Update the phone — Settings, then System, then software update — and scan again to " +
                "confirm the reader comes back.",
            measurements = listOf(Measurement("Fingerprint / face", "needs a security update")),
            falsePositiveCauses = listOf(
                "The phone is simply out of date; updating usually restores biometrics.",
                "A custom or unofficial build can report this with no real update to apply.",
            ),
        )

        BiometricAvailability.NO_HARDWARE -> CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.UNKNOWN,
            confidence = Confidence.HIGH,
            headline = "No fingerprint or face unlock detected on this phone.",
            consequence = "Either this model never had one, or a fitted reader is faulty enough that the " +
                "phone cannot see it — and this check cannot tell those two apart.",
            action = "Check whether this model is meant to have a fingerprint reader. If it is, the phone " +
                "not seeing it points to a fault; if it never had one, this is normal.",
            measurements = listOf(Measurement("Fingerprint / face", "none detected")),
        )

        BiometricAvailability.UNKNOWN_STATUS -> CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.UNKNOWN,
            confidence = Confidence.HIGH,
            headline = "The fingerprint and face unlock state could not be read on this phone.",
            measurements = listOf(Measurement("Fingerprint / face", "not readable")),
        )
    }
}
