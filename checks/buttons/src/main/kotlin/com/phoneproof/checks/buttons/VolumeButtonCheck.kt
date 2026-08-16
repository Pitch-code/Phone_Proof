package com.phoneproof.checks.buttons

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.model.plural

/** What one button did while the test was open. */
data class ButtonObservation(
    /** How many times it went down. */
    val presses: Int = 0,
    /** How many times it came back up. Fewer than [presses] means it is still down. */
    val releases: Int = 0,
    /** The longest it stayed down, in milliseconds. The signal for a physically stuck key. */
    val longestHoldMillis: Long = 0L,
) {
    val everPressed: Boolean get() = presses > 0
    val stillDown: Boolean get() = presses > releases
}

/** Whether the buyer says they actually pressed both buttons. */
enum class PressedBoth {
    NOT_ASKED,
    YES,
    NO,
}

/**
 * Do the volume buttons work — and is either of them jammed?
 *
 * ## Two faults, and the second one is the expensive one
 *
 * A dead volume button is an irritation. A **stuck** volume button is a phone that misbehaves on its own:
 * volume-down held during a boot puts many handsets into recovery, volume-down with power takes endless
 * screenshots, and a phone that does either by itself looks possessed. It is a common fault on a
 * second-hand handset — buttons wear, and grit and dropped-phone damage both jam them — and it is close to
 * invisible in a shop, because nobody presses the volume keys during a demonstration.
 *
 * ## Each button vouches for the app
 *
 * The trap here is symmetrical with the one in the sensor test. If the app has failed to hook the volume
 * keys at all, **both buttons look dead**, and reporting that as two broken buttons would blame a phone for
 * a bug in this software.
 *
 * So a silence only means something when the *other* button was heard. One button registering proves the
 * app receives volume keys, which is what makes the other one's silence evidence. If neither is heard,
 * nothing is concluded — that is this app's problem, not the handset's.
 */
object VolumeButtonCheck {

    const val CHECK_ID: String = "hardware.volume_buttons"

    private const val TITLE = "Volume buttons"

    /**
     * Held this long without coming back up, and the key is treated as jammed rather than pressed.
     *
     * Four seconds. A buyer asked for a press does not hold one for four seconds, and a key that is
     * physically stuck never comes up at all — so the gap between the two behaviours is enormous and the
     * exact threshold barely matters. Erring long, because "your button is jammed" is a strong claim.
     */
    const val STUCK_HOLD_MILLIS: Long = 4_000L

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A case or cover pressing on a button holds it down, or stops it moving at all.",
        "It is easy to press the same button twice while meaning to press both.",
        "Some accessibility services and screen recorders take the volume keys before any app sees them.",
        "A phone with side keys very close together makes the wrong one easy to hit.",
    )

    fun evaluate(
        up: ButtonObservation,
        down: ButtonObservation,
        pressedBoth: PressedBoth = PressedBoth.NOT_ASKED,
    ): CheckResult {
        val measurements = listOf(
            Measurement("Volume up", describe(up)),
            Measurement("Volume down", describe(down)),
        )

        // A jammed key outranks everything else, including a missing press on the other button. It is the
        // more serious fault and it explains the phone's behaviour on its own.
        val stuck = listOfNotNull(
            "up".takeIf { up.isStuck() },
            "down".takeIf { down.isStuck() },
        )
        if (stuck.isNotEmpty()) {
            val which = stuck.joinToString(" and ") { "volume $it" }
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The $which ${nounForButtons(stuck.size)} reporting as held down and " +
                    "never released.",
                consequence = "A jammed volume key is not just an annoyance. Volume-down held during " +
                    "a restart puts many phones into recovery mode, and volume-down with the power " +
                    "key takes a screenshot — so the phone will seem to do these things by itself.",
                action = "Take any case off and press the key a few times to see if it frees up. If " +
                    "it stays jammed, it is a repair — worth 1,000 off, and worth checking the phone " +
                    "still restarts normally before you pay.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        if (up.everPressed && down.everPressed) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = "Both volume buttons registered and released cleanly.",
                measurements = measurements,
            )
        }

        // Neither button was heard. That is at least as likely to be this app failing to receive the keys
        // as it is two dead buttons, and there is nothing here to tell those apart.
        if (!up.everPressed && !down.everPressed) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "Neither volume button reached the app, which may be the app's fault " +
                    "rather than the phone's.",
                action = "Press each button once with this screen open. If nothing happens at all, " +
                    "check the volume changes elsewhere in the phone before blaming the buttons.",
                measurements = measurements,
            )
        }

        val silent = if (up.everPressed) "down" else "up"
        val heard = if (up.everPressed) "up" else "down"

        return when (pressedBoth) {
            PressedBoth.NOT_ASKED -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "Volume $heard registered. Volume $silent has not been pressed yet.",
                action = "Press the volume $silent button while this screen is open.",
                measurements = measurements,
            )

            PressedBoth.NO -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "Volume $silent was never pressed, so nothing is known about it.",
                action = "Worth going back and pressing it — a dead volume key is cheap to find " +
                    "now and irritating to live with.",
                measurements = measurements,
            )

            PressedBoth.YES -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                // CAUTION rather than FAIL. The other button proves the app receives volume keys, which
                // makes this silence real evidence — but pressing the same button twice by mistake is
                // easy, and so is a case holding one of them.
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Volume $heard worked and volume $silent never registered, though you " +
                    "pressed it.",
                consequence = "You would lose one direction of volume control everywhere — including " +
                    "silencing a ringing phone in a hurry, which is when the button matters most. " +
                    "Volume $silent is also part of several shortcuts, such as taking a screenshot " +
                    "and reaching recovery mode.",
                action = "Take the case off and try once more. If it stays silent it is a worn " +
                    "button — a repair rather than a setting, so get 800 off or look at another phone.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }
    }

    private fun ButtonObservation.isStuck(): Boolean =
        stillDown && longestHoldMillis >= STUCK_HOLD_MILLIS

    private fun describe(observation: ButtonObservation): String = when {
        observation.isStuck() -> "held down"
        !observation.everPressed -> "not pressed"
        observation.stillDown -> "still down"
        else -> plural(observation.presses, "press", "presses")
    }

    private fun nounForButtons(count: Int): String = if (count == 1) "button is" else "buttons are"
}
