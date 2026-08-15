package com.phoneproof.checks.media

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Did the microphone hear the buyer speak?
 *
 * The measurement is the **rise** of the loud frames above the room's own noise floor, not an absolute
 * level. An absolute threshold cannot work: the same handset in a quiet flat and in a market lane
 * differs by twenty decibels before anyone opens their mouth, so a fixed line would fail every working
 * microphone in the second case or pass every dead one in the first.
 */
object MicrophoneCheck {

    const val CHECK_ID: String = "hardware.microphone"

    private const val TITLE = "Microphone"

    /**
     * 12 dB of rise counts as having heard something.
     *
     * Four times the amplitude. Speech at arm's length in a normal room lands 15–30 dB above the floor,
     * so 12 leaves margin for someone who mumbles, and sits well above the two or three decibels a room
     * wanders by on its own.
     */
    const val MIN_SIGNAL_OVER_FLOOR_DB: Float = 12f

    private val FALSE_POSITIVE_CAUSES = listOf(
        "Many phones mute the microphone system-wide with a privacy toggle, which silences every app.",
        "Another app holding the microphone — a call, a recorder, a voice assistant — takes it exclusively.",
        "A case or a finger over the pinhole muffles it almost completely.",
        "Some handsets have several microphones, and this tests the one the system chose.",
    )

    fun evaluate(analysis: AudioAnalysis): CheckResult {
        val measurements = buildList {
            add(Measurement("Loudest", formatDbfs(analysis.loudest)))
            add(Measurement("Room noise", formatDbfs(analysis.noiseFloor)))
            if (analysis.frameCount > 0) {
                add(Measurement("Rose by", "%.0f".format(analysis.signalOverFloorDb), "dB"))
            }
        }

        if (analysis.frameCount == 0) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "No audio was recorded.",
                action = "Allow the microphone when asked, then try again.",
                // Nothing was measured, so there is nothing to draw a consequence from.
            )
        }

        // Exact zeros, which is not the same as quiet and gets its own answer.
        //
        // A real capsule in a silent room still returns self-noise. A stream of perfect zeros means no
        // audio reached the app, and the likeliest reasons are a system mic toggle or another app holding
        // the input — neither of which is a fault in the phone being bought. So: CAUTION, and the causes
        // are listed in the order they actually occur.
        if (analysis.isDigitalSilence) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The microphone returned complete silence.",
                consequence = "Not one sample of audio arrived, which is different from a quiet room. " +
                    "If it is the microphone itself, calls will be one-sided and voice notes will be " +
                    "empty.",
                action = "Check the phone's own privacy toggle for the microphone, close anything that " +
                    "might be using it, and test again. If it stays silent, try a voice recorder app " +
                    "before you pay.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        if (analysis.signalOverFloorDb < MIN_SIGNAL_OVER_FLOOR_DB) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.MEDIUM,
                headline = "Nothing rose clearly above the background noise.",
                consequence = "The microphone is delivering audio, so it is not dead — but nothing in " +
                    "this recording stands out from the room, so it cannot be called working either.",
                action = "Hold the phone closer, speak louder, and try again. In a noisy market, step " +
                    "away from the road first.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        if (analysis.isClipping) {
            // Still a pass. Clipping proves the microphone works, loudly — it is a note about the
            // recording, not a fault in the phone, and calling it anything worse would be inventing a
            // problem out of a good result.
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = "The microphone heard you, loudly enough to distort.",
                measurements = measurements,
            )
        }

        return CheckResult(
            id = CHECK_ID,
            title = TITLE,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "The microphone heard you clearly.",
            measurements = measurements,
        )
    }
}

/**
 * A level as dBFS, the unit an audio engineer would expect: 0 is full scale and everything real is
 * negative.
 *
 * Silence is rendered as a symbol rather than the mathematically correct minus infinity, which would be
 * both alarming and useless in a table a buyer is reading.
 */
internal fun formatDbfs(level: Float): String {
    if (level <= 0f) return "silent"
    val db = 20.0 * kotlin.math.log10(level.toDouble())
    return "%.0f dBFS".format(db)
}
