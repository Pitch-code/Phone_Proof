package com.phoneproof.checks.sensors

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import java.util.Locale

/**
 * Turns sensor findings into report rows.
 *
 * One row per sensor rather than a single "Sensors" verdict, because a dead gyroscope and a dead
 * proximity sensor are different faults with different consequences and different prices. Rolled into
 * one row they would produce a single vague line to argue with; separately they each land in the
 * verdict's list of what to say to the seller, with their own number attached.
 */
object SensorCheck {

    /** `hardware.sensor.gyroscope`, so every one of these lands under the hardware heading. */
    fun idFor(kind: SensorKind): String = "hardware.sensor.${kind.name.lowercase()}"

    fun results(findings: List<SensorFinding>): List<CheckResult> = findings.map(::result)

    private fun result(finding: SensorFinding): CheckResult {
        val kind = finding.kind
        val s = finding.stats
        val measurements = measurementsFor(kind, s)

        return when (finding.state) {
            SensorState.ALIVE -> CheckResult(
                id = idFor(kind),
                title = title(kind),
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = aliveHeadline(kind, s),
                measurements = measurements,
            )

            SensorState.UNAVAILABLE -> CheckResult(
                id = idFor(kind),
                title = title(kind),
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "This app could not connect to the ${plainName(kind)}. That is a gap in " +
                    "the report rather than anything known about the phone.",
                action = "Close the app, open it again and re-run this test.",
                measurements = measurements,
            )

            SensorState.NOT_EXERCISED -> CheckResult(
                id = idFor(kind),
                title = title(kind),
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = notExercisedHeadline(kind, s),
                // An action even though nothing is wrong: the buyer can fix this one themselves in
                // five seconds, and a "can't tell" they could have prevented is worth saying so.
                action = when (kind) {
                    SensorKind.PROXIMITY, SensorKind.LIGHT ->
                        "Run it again and hold your palm flat over the top of the screen."
                    else -> "Run it again and tilt the phone right over, then turn it back."
                },
                measurements = measurements,
            )

            SensorState.SILENT -> problem(
                kind = kind,
                measurements = measurements,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "The ${plainName(kind)} accepted the connection and then reported " +
                    "nothing at all.",
            )

            SensorState.DEAD -> problem(
                kind = kind,
                measurements = measurements,
                // The light sensor is the one exception, and it is a real one: on a lot of phones it
                // sits under the display panel rather than in the earpiece slot, so a palm placed
                // where the screen asked can genuinely miss it. Everything else in this list is where
                // the hand was.
                outcome = if (kind == SensorKind.LIGHT) CheckOutcome.CAUTION else CheckOutcome.FAIL,
                confidence = if (kind == SensorKind.LIGHT) Confidence.MEDIUM else Confidence.HIGH,
                headline = deadHeadline(kind),
            )

            SensorState.STUCK -> problem(
                kind = kind,
                measurements = measurements,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.HIGH,
                headline = "The ${plainName(kind)} sent the same reading " +
                    "${s.count} times while the phone was being moved.",
            )

            SensorState.IMPLAUSIBLE -> problem(
                kind = kind,
                measurements = measurements,
                // Never a failure. A magnetic case or a metal shelf will do this to a working compass,
                // and being wrong here means telling someone their phone is broken over a fridge magnet.
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = implausibleHeadline(kind, s),
            )
        }
    }

    private fun problem(
        kind: SensorKind,
        measurements: List<Measurement>,
        outcome: CheckOutcome,
        confidence: Confidence,
        headline: String,
    ) = CheckResult(
        id = idFor(kind),
        title = title(kind),
        outcome = outcome,
        confidence = confidence,
        headline = headline,
        consequence = consequence(kind),
        action = action(kind),
        measurements = measurements,
        falsePositiveCauses = falsePositiveCauses(kind),
    )

    // ---------------------------------------------------------------- what it means to a buyer

    private fun title(kind: SensorKind): String = when (kind) {
        SensorKind.ACCELEROMETER -> "Accelerometer"
        SensorKind.GYROSCOPE -> "Gyroscope"
        SensorKind.MAGNETOMETER -> "Compass"
        SensorKind.PROXIMITY -> "Proximity sensor"
        SensorKind.LIGHT -> "Light sensor"
    }

    private fun plainName(kind: SensorKind): String = when (kind) {
        SensorKind.ACCELEROMETER -> "accelerometer"
        SensorKind.GYROSCOPE -> "gyroscope"
        SensorKind.MAGNETOMETER -> "compass"
        SensorKind.PROXIMITY -> "proximity sensor"
        SensorKind.LIGHT -> "light sensor"
    }

    private fun consequence(kind: SensorKind): String = when (kind) {
        SensorKind.ACCELEROMETER ->
            "The screen will not turn when you turn the phone, step counting will not work, and " +
                "nothing that depends on how the handset is being held will behave properly. This " +
                "is the sensor every other one is built around."
        SensorKind.GYROSCOPE ->
            "Racing and shooting games that steer by tilting will not respond, anything using AR " +
                "will refuse to start, and video will be shakier than it should be because " +
                "stabilisation leans on this sensor. It is soldered to the board, so it is not an " +
                "economic repair."
        SensorKind.MAGNETOMETER ->
            "Maps will show you on the right road pointing the wrong way, which is exactly when a " +
                "compass matters — walking out of a station deciding which direction to set off in."
        SensorKind.PROXIMITY ->
            "The screen will stay on against your face during calls, so you will mute yourself " +
                "and hang up with your cheek, and every long call will burn battery lighting up " +
                "the inside of your ear."
        SensorKind.LIGHT ->
            "Automatic brightness will not work, so you will be reaching for the slider every time " +
                "you walk indoors or out, and the screen will sit at full brightness in the dark."
    }

    private fun action(kind: SensorKind): String = when (kind) {
        SensorKind.ACCELEROMETER ->
            "Treat this as a broken phone, not a discount. Something has gone wrong at the board " +
                "level, and it is rarely the only thing."
        SensorKind.GYROSCOPE ->
            "If you play games or use AR, walk away — it cannot be fixed for a sensible price. If " +
                "you do not, treat it as permanent, price it into what you offer, and know what " +
                "you are buying."
        SensorKind.MAGNETOMETER ->
            "Try Google Maps on the phone before you decide. If it still spins, treat it as a fault " +
                "worth haggling over."
        SensorKind.PROXIMITY ->
            "Make a real call before you pay. If the screen stays lit against your ear, get 1,500 " +
                "off — the part is cheap but the screen has to come off to reach it."
        SensorKind.LIGHT ->
            "Check whether automatic brightness responds in the Settings app before you decide, " +
                "then treat it as a fault worth haggling over if it does not."
    }

    /**
     * How each verdict could be wrong, in the buyer's own terms.
     *
     * Not boilerplate. Every entry here is a thing that has to be ruled out before an accusation is
     * fair, and the first line of the proximity list is the specific mistake this whole module was
     * written to avoid making.
     */
    private fun falsePositiveCauses(kind: SensorKind): List<String> = when (kind) {
        SensorKind.ACCELEROMETER -> listOf(
            "A heavily modified build can feed apps invented sensor values",
            "The phone was resting on something that was itself moving, such as a bus seat",
        )
        SensorKind.GYROSCOPE -> listOf(
            "Some budget phones simulate a gyroscope from the accelerometer and report it as real",
            "A very slow, smooth turn can stay under the threshold this test looks for",
            "Battery-saver modes on some phones throttle sensor delivery to a crawl",
        )
        SensorKind.MAGNETOMETER -> listOf(
            "A magnetic case, a pop-socket or a car mount magnet swamps the reading",
            "Metal shop shelving and speakers distort the field for a foot around them",
            "A compass that has never been calibrated reads oddly until it is figure-of-eighted",
        )
        SensorKind.PROXIMITY -> listOf(
            "A palm placed lower than the earpiece never reaches the sensor",
            "A thick screen protector or a case lip over the sensor slot",
            "Some phones use an ultrasonic sensor through the earpiece, which a flat palm can miss",
        )
        SensorKind.LIGHT -> listOf(
            "On many phones this sensor sits under the display, not in the earpiece slot, so a " +
                "palm over the top of the screen may not have covered it",
            "A very dim room leaves too little light for covering it to change anything",
            "A screen protector that is not cut correctly over the sensor window",
        )
    }

    // ---------------------------------------------------------------- headlines and numbers

    private fun aliveHeadline(kind: SensorKind, s: TraceStats): String = when (kind) {
        SensorKind.ACCELEROMETER ->
            "Tracked the tilt, and reads gravity as ${oneDecimal(s.magnitudeMean)} m/s² — the " +
                "real value is 9.8."
        SensorKind.GYROSCOPE ->
            "Measured the turn, peaking at ${oneDecimal(s.magnitudeMax)} rad/s."
        SensorKind.MAGNETOMETER ->
            "Reads a field of ${oneDecimal(s.magnitudeMean)} µT, which is the Earth's."
        SensorKind.PROXIMITY -> "Noticed your palm and let go again."
        SensorKind.LIGHT ->
            "Followed the light from ${oneDecimal(s.magnitudeMin)} to " +
                "${oneDecimal(s.magnitudeMax)} lux."
    }

    private fun deadHeadline(kind: SensorKind): String = when (kind) {
        // Each of these names its witness, because that is the entire basis for the accusation and the
        // buyer is about to repeat it to the person selling them the phone.
        SensorKind.ACCELEROMETER -> "Reported no movement at all while the phone was being tilted."
        SensorKind.GYROSCOPE ->
            "The phone was turned through a wide angle — the accelerometer followed it the whole " +
                "way — and the gyroscope reported no rotation."
        SensorKind.MAGNETOMETER -> "Reported no field while the phone was turning."
        SensorKind.PROXIMITY ->
            "The light sensor went dark under your palm and the proximity sensor never noticed it."
        SensorKind.LIGHT ->
            "The proximity sensor felt your palm and the light sensor saw no change in brightness."
    }

    private fun implausibleHeadline(kind: SensorKind, s: TraceStats): String = when (kind) {
        SensorKind.ACCELEROMETER ->
            "Reports gravity as ${oneDecimal(s.magnitudeMean)} m/s². On this planet it is 9.8."
        SensorKind.MAGNETOMETER ->
            "Reads ${oneDecimal(s.magnitudeMean)} µT. The Earth's field is 25 to 65."
        // Kept total so a future sensor cannot reach this branch with no sentence to show.
        else -> "Reported a value outside anything physically sensible."
    }

    /**
     * One sentence per sensor, and not one shared sentence.
     *
     * The first version used the same line for the accelerometer and the gyroscope, and the render put
     * the two cards one above the other reading word for word the same — which makes an honest result
     * look like an unfilled template. Each of these now says what specifically did not happen, and the
     * accelerometer's admits what it *does* know, because gravity proved its calibration even though
     * nobody tilted it.
     */
    private fun notExercisedHeadline(kind: SensorKind, s: TraceStats): String = when (kind) {
        SensorKind.ACCELEROMETER ->
            "It reads gravity correctly at ${oneDecimal(s.magnitudeMean)} m/s², so it is calibrated " +
                "— but the phone was not tilted far enough to see whether it follows movement."
        SensorKind.GYROSCOPE ->
            "The phone was never turned, so there was no rotation for this sensor to have missed."
        SensorKind.MAGNETOMETER ->
            "The compass reported nothing worth judging, and the phone was not turned enough to " +
                "press it."
        SensorKind.PROXIMITY ->
            "Your hand never reached the sensor, so it had nothing to detect. The light sensor " +
                "agrees it stayed light up there."
        SensorKind.LIGHT ->
            "The top of the screen never went dark, so this sensor had no change to follow."
    }

    private fun measurementsFor(kind: SensorKind, s: TraceStats): List<Measurement> = buildList {
        add(Measurement("Readings", "${s.count}"))
        when (kind) {
            SensorKind.ACCELEROMETER -> {
                add(Measurement("Gravity measured", oneDecimal(s.magnitudeMean), "m/s²"))
                add(Measurement("Gravity expected", "9.8", "m/s²"))
                add(Measurement("Largest tilt", oneDecimal(s.largestAxisSpan), "m/s²"))
            }
            SensorKind.GYROSCOPE -> {
                add(Measurement("Fastest turn", oneDecimal(s.magnitudeMax), "rad/s"))
            }
            SensorKind.MAGNETOMETER -> {
                add(Measurement("Field strength", oneDecimal(s.magnitudeMean), "µT"))
                add(Measurement("Earth's field", "25–65", "µT"))
            }
            SensorKind.PROXIMITY -> {
                add(Measurement("Range seen", oneDecimal(s.largestAxisSpan), "cm"))
            }
            SensorKind.LIGHT -> {
                add(Measurement("Darkest", oneDecimal(s.magnitudeMin), "lux"))
                add(Measurement("Brightest", oneDecimal(s.magnitudeMax), "lux"))
            }
        }
        // Named "different readings" rather than "distinct": it is the number that exposes a latched
        // driver, and it has to mean something to a buyer reading the report months later.
        add(Measurement("Different readings", "${s.distinctValues}"))
    }

    /**
     * Locale.ROOT deliberately.
     *
     * A decimal comma is correct for a lot of the world, but these strings are compared in tests and
     * rendered into committed screenshots, so a build machine's locale must not be able to change
     * them. Numbers a buyer reads in their own format is a job for the presentation layer, once this
     * app has a presentation layer that knows about locales at all.
     */
    private fun oneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
}
