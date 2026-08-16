package com.phoneproof.checks.device

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.model.plural
import java.util.Locale

/** What the phone is plugged into, as Android reports it. */
enum class PlugType {
    NONE,

    /** A mains charger. The only case where a wattage figure means much. */
    AC,

    /** A computer or a low-power port, which is capped by the port and not by the phone. */
    USB,

    WIRELESS,

    /** Plugged into something Android will not name. */
    OTHER,
}

enum class ChargeAttempt {
    /** No cable was ever connected, so there was nothing to measure. */
    NOT_PLUGGED,

    /** Connected, and the phone is not charging. The finding that matters most. */
    PLUGGED_NOT_CHARGING,

    /** Already full. Charging cannot be timed against a battery with nowhere to put the energy. */
    BATTERY_FULL,

    MEASURED,
}

data class ChargeTrace(
    val attempt: ChargeAttempt,
    val plugType: PlugType = PlugType.NONE,
    val batteryPercent: Int = 0,
    val voltageMillivolts: Int = 0,
    /**
     * Charging current in milliamps, as an absolute value, or null when the phone will not report it.
     *
     * Absolute deliberately. The sign of `BATTERY_PROPERTY_CURRENT_NOW` is not standardised — some
     * manufacturers report charging as positive and some as negative — so the direction is taken from the
     * charging status instead, which is reliable, and the magnitude from here.
     */
    val currentMilliamps: Int? = null,
    val temperatureCelsius: Double? = null,
    /**
     * How many times the charger vanished while the cable was left alone.
     *
     * The most valuable number in this check. See [ChargingCheck].
     */
    val dropouts: Int = 0,
    val sampleSeconds: Int = 0,
) {
    /** Watts, or null when the phone does not report current. */
    val watts: Double?
        get() = currentMilliamps?.let { (voltageMillivolts / 1000.0) * (it / 1000.0) }

    /** Above this the charger tapers on purpose, so a low figure says nothing about the hardware. */
    val nearlyFull: Boolean get() = batteryPercent >= 80
}

/**
 * Does it charge, how fast, and does it keep charging?
 *
 * ## The third question is the one nobody asks
 *
 * A loose charging port is one of the commonest faults on a used phone and one of the easiest to miss. It
 * charges when you plug it in, so it passes every inspection — and then it charges only at a certain angle,
 * or stops overnight, or needs the cable wiggled. A buyer discovers it the first morning they wake to a flat
 * phone.
 *
 * It is also genuinely measurable, and this is the only check here that needs no judgement at all: watch the
 * plugged state while the cable is left untouched, and count the times it disappears. A sound port never
 * drops. **One dropout in twenty seconds is a fault**, and it is the finding this check exists for even
 * though the buyer came to it asking about speed.
 *
 * ## Why the wattage is reported far more carefully than it is judged
 *
 * Charging power depends on the charger, the cable, the battery level, the temperature, and what the phone is
 * doing. A 33 W phone on a 5 W charger draws 5 W and there is nothing wrong with it. Above roughly 80 percent
 * every phone tapers on purpose. So a low figure is mostly a fact about the charger in the buyer's hand, and
 * the check says which kind Android thinks it is rather than pretending the number is a verdict.
 *
 * The exception is a genuine trickle. Below about 2.5 W the phone is drawing less than a laptop's USB socket
 * provides, which is the signature of a damaged port, a failing charge controller or a counterfeit cable —
 * and that is worth a caution whatever the charger is.
 */
object ChargingCheck {

    const val CHECK_ID: String = "hardware.charging"

    private const val TITLE = "Charging"

    /**
     * Below this, the phone is drawing less than any real charger offers.
     *
     * 2.5 W is the old USB 2.0 ceiling — 5 V at 500 mA — so anything under it is below what a computer's port
     * provides. Not a threshold about fast charging at all; a threshold about whether power is arriving.
     */
    const val TRICKLE_WATTS: Double = 2.5

    private val PORT_FALSE_POSITIVES = listOf(
        "A worn or cheap cable causes exactly this, and is far more likely than a broken phone.",
        "Lint in the socket is extremely common and takes seconds to clear.",
        "A loose wall socket or a charger that runs hot can cut out on its own.",
        "Moving the phone during the test pulls on the cable and looks identical to a loose port.",
    )

    private val SPEED_FALSE_POSITIVES = listOf(
        "The charger and cable decide most of this — a fast phone on a slow charger draws slow.",
        "Every phone tapers deliberately above about 80 percent.",
        "A hot phone, or one in the sun, slows its own charging to protect the battery.",
        "Charging while the screen is on and testing things is slower than charging idle.",
    )

    fun evaluate(trace: ChargeTrace): CheckResult {
        val measurements = buildList {
            add(Measurement("Plugged into", plugLabel(trace.plugType)))
            add(Measurement("Battery", "${trace.batteryPercent}", "%"))
            if (trace.attempt == ChargeAttempt.MEASURED) {
                trace.watts?.let { add(Measurement("Power drawn", format(it), "W")) }
                trace.currentMilliamps?.let { add(Measurement("Current", "$it", "mA")) }
                add(Measurement("Voltage", format(trace.voltageMillivolts / 1000.0), "V"))
                add(Measurement("Watched for", "${trace.sampleSeconds}", "s"))
                add(Measurement("Charger dropouts", "${trace.dropouts}"))
            }
            trace.temperatureCelsius?.let { add(Measurement("Battery temperature", format(it), "°C")) }
        }

        when (trace.attempt) {
            ChargeAttempt.NOT_PLUGGED -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "No charger was connected, so charging was not tested.",
                // Worth pressing on, because this is the one check on the list a buyer cannot do later. Once
                // the money has changed hands, a bad port is their problem.
                action = "Worth going back for. Borrow the seller's charger, plug it in and run this " +
                    "again — a loose charging port is one of the commonest faults on a used phone and " +
                    "the hardest to spot afterwards.",
                measurements = measurements,
            )

            ChargeAttempt.PLUGGED_NOT_CHARGING -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The phone is connected to a charger and is not charging.",
                consequence = "If this is the phone rather than the cable, it is either the socket or " +
                    "the charging controller on the board. A socket is a cheap repair; a controller is " +
                    "usually not worth doing.",
                action = "Try a different cable and charger first — that is the likeliest answer by " +
                    "far. If it still will not charge, walk away.",
                measurements = measurements,
                falsePositiveCauses = PORT_FALSE_POSITIVES,
            )

            ChargeAttempt.BATTERY_FULL -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "The battery is already full, so there is no charging speed to measure.",
                action = "Nothing to worry about. If you want the speed, run the phone down below 80 " +
                    "percent first — above that every phone slows down on purpose.",
                measurements = measurements,
            )

            ChargeAttempt.MEASURED -> Unit
        }

        // A port that lets go is the most serious thing this check can find, and it outranks the speed. A
        // phone that charges fast in a shop and stops overnight is worse than one that charges slowly.
        if (trace.dropouts > 0) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Charging stopped and started ${plural(trace.dropouts, "time")} while the " +
                    "cable was left alone.",
                consequence = "This is a loose socket, and it is the fault people discover on the " +
                    "first morning they wake up to a flat phone. It charges perfectly while you are " +
                    "watching it and gives up once you stop.",
                action = "Check the socket for lint and try another cable. If it keeps dropping, get " +
                    "the price of a port repair off — around 1,200 — or walk away.",
                measurements = measurements,
                falsePositiveCauses = PORT_FALSE_POSITIVES,
            )
        }

        val watts = trace.watts

        if (watts == null) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                // MEDIUM rather than HIGH: charging is confirmed and the speed is genuinely unknown, so the
                // pass covers less ground than a normal one and should not claim otherwise.
                confidence = Confidence.MEDIUM,
                headline = "Charging steadily with no dropouts. This phone does not report how much " +
                    "power it is drawing, so the speed is unknown.",
                measurements = measurements,
            )
        }

        if (watts < TRICKLE_WATTS) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Only ${format(watts)} W is reaching the battery — less than a computer's " +
                    "USB socket provides.",
                consequence = "At this rate a full charge takes most of a day. It usually means a " +
                    "damaged socket, a failing charge controller or a counterfeit cable rather than a " +
                    "slow charger.",
                action = "Try a known-good charger and cable before deciding — that is the likeliest " +
                    "cause. If it stays this slow on a charger you trust, the phone needs a repair.",
                measurements = measurements,
                falsePositiveCauses = SPEED_FALSE_POSITIVES,
            )
        }

        val taper = if (trace.nearlyFull) {
            " The battery is above 80 percent, where every phone slows down deliberately, so this is " +
                "not its top speed."
        } else {
            ""
        }
        val chargerCaveat = when (trace.plugType) {
            PlugType.USB ->
                " Plugged into a USB port, which caps the speed itself — the phone may well be capable " +
                    "of much more."
            PlugType.WIRELESS -> " Charging wirelessly, which is slower than a cable on every phone."
            else -> ""
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "Drawing ${format(watts)} W steadily, with no dropouts in " +
                "${trace.sampleSeconds} seconds.$chargerCaveat$taper",
            measurements = measurements,
        )
    }

    /**
     * The sentence that keeps the wattage honest, for the screen to show beside any speed figure.
     *
     * Separate from the verdict because it is true whatever the verdict says. A buyer comparing this number
     * against the "33 W" on the box needs to know the charger in their hand is most of the answer.
     */
    const val SPEED_NOTE: String =
        "The wattage is what the phone drew from this charger and this cable, not what it is capable " +
            "of. A fast phone on a slow charger looks slow. If the number matters to you, test it " +
            "again with the charger you actually intend to use."

    private fun plugLabel(type: PlugType): String = when (type) {
        PlugType.NONE -> "nothing"
        PlugType.AC -> "a mains charger"
        PlugType.USB -> "a USB port"
        PlugType.WIRELESS -> "a wireless pad"
        PlugType.OTHER -> "something unrecognised"
    }

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
}
