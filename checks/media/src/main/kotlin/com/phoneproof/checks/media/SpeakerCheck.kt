package com.phoneproof.checks.media

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/** What the buyer said when the app could not tell for itself. */
enum class HeardTone {
    /** Not asked yet, or asked and not answered. */
    NOT_ASKED,
    YES,
    NO,
}

/**
 * Does the speaker actually produce sound?
 *
 * Measure, then ask — in that order, and only asking when the measuring genuinely failed.
 *
 * The app plays a 1 kHz tone and looks for it in a simultaneous recording. If the microphone hears the
 * tone, the speaker works, and that is a fact about the hardware rather than an opinion. The catch is
 * where this app is used: a market lane at midday can bury a phone speaker, and a failure to detect the
 * tone then says nothing about the speaker.
 *
 * So an inconclusive measurement is never reported as a fault. It becomes a question — *did you hear
 * it?* — and the buyer's ear settles it. Their answer is recorded as what it is: their answer, at
 * MEDIUM confidence, never dressed up as something the app measured.
 */
object SpeakerCheck {

    const val CHECK_ID: String = "hardware.speaker"

    private const val TITLE = "Speaker"

    /**
     * Above this share of the recording's energy at 1 kHz, the tone was heard.
     *
     * 0.25 is deliberately far below the 1.0 a clean tone produces. The recording contains the room as
     * well as the tone, the microphone is not pointed at the speaker, and the phone's own body is in the
     * way — so requiring anything close to a pure tone would report a working speaker as silent in every
     * room that is not a studio.
     */
    const val TONE_DETECTED_RATIO: Float = 0.25f

    /**
     * Below this, the room was quiet enough that failing to hear the tone means something.
     *
     * The distinction that makes "measure, then ask" honest. In a quiet room a missing tone is evidence
     * about the speaker; in a loud one it is evidence about the room, and the app must not confuse the
     * two just because it wanted an answer.
     */
    const val QUIET_ROOM_FLOOR: Float = 0.02f

    private val FALSE_POSITIVE_CAUSES = listOf(
        "Background noise in a shop or on a street can drown a phone speaker completely.",
        "Media volume left low or muted by the previous owner silences the test tone.",
        "A hand, a case or a table surface over the speaker grille blocks most of the sound.",
        "Some phones route this kind of tone to the earpiece rather than the loudspeaker.",
        "A blocked microphone makes a working speaker undetectable, because the two are tested together.",
    )

    /**
     * @param toneRatio how much of the recording's energy sat at the test frequency, from [ToneDetector].
     * @param roomFloor the recording's noise floor, used only to decide whether a miss means anything.
     * @param heard what the buyer said, when the measurement could not decide.
     */
    fun evaluate(
        toneRatio: Float,
        roomFloor: Float,
        heard: HeardTone = HeardTone.NOT_ASKED,
    ): CheckResult {
        val measurements = listOf(
            Measurement("Tone detected", "%.0f".format(toneRatio * 100f), "%"),
            Measurement("Room noise", formatDbfs(roomFloor)),
        )

        // Measured, and conclusive. Nothing is asked, because there is nothing left to ask.
        if (toneRatio >= TONE_DETECTED_RATIO) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = "The microphone picked up the test tone from the speaker.",
                measurements = measurements,
            )
        }

        val roomWasQuiet = roomFloor < QUIET_ROOM_FLOOR

        // The buyer has answered. Their ear outranks a measurement that failed, in both directions.
        when (heard) {
            HeardTone.YES -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                // MEDIUM, not HIGH, and the headline says whose finding it is. A buyer's "yes" is good
                // evidence and it is not a measurement, and the report has to keep those apart — a
                // saved report read a week later must not look like the app confirmed this itself.
                confidence = Confidence.MEDIUM,
                headline = "You heard the tone. The app could not detect it over the noise.",
                measurements = measurements,
            )

            HeardTone.NO -> return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                // HIGH is justified here and nowhere else in this check: the app failed to hear the tone
                // and so did the person holding the phone. Two independent misses on the same tone is
                // the strongest evidence this test can produce.
                confidence = Confidence.HIGH,
                headline = "Neither you nor the app heard the test tone.",
                consequence = "The loudspeaker is very likely dead or blocked. You will miss calls, " +
                    "alarms and notifications, and speakerphone will be unusable.",
                action = "Turn the media volume all the way up and test again. Play a video too. If " +
                    "there is still nothing, a speaker replacement is a real cost — negotiate hard or " +
                    "walk away.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )

            HeardTone.NOT_ASKED -> Unit
        }

        // Not measured, and not yet asked. This is the branch that must never accuse.
        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.UNKNOWN,
            confidence = Confidence.MEDIUM,
            headline = if (roomWasQuiet) {
                "The test tone was not detected, in a quiet room."
            } else {
                "Too much background noise to detect the test tone."
            },
            consequence = if (roomWasQuiet) {
                "The room was quiet enough that the tone should have been picked up, so this may be " +
                    "the speaker — but the microphone and the volume setting are both in the way of " +
                    "that conclusion."
            } else {
                "In this much noise a working speaker is undetectable, so nothing can be concluded " +
                    "about it either way."
            },
            action = "Turn the media volume up, keep your hand off the grille, and answer whether you " +
                "heard the tone.",
            measurements = measurements,
            falsePositiveCauses = FALSE_POSITIVE_CAUSES,
        )
    }
}
