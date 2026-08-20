package com.phoneproof.checks.media

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/**
 * Whether the tone actually went to the earpiece.
 *
 * This is the whole basis of the test and the reason it is safe to run. The earpiece and the loudspeaker
 * are different transducers, and on most phones asking for one and getting the other is a routing
 * decision the platform makes without telling anybody. If the app plays a tone, the platform quietly
 * sends it to the loudspeaker, and the buyer says "yes I heard it", then **a completely dead earpiece
 * passes** — and the buyer discovers it on their first call after paying.
 *
 * So nothing is concluded, and nothing is even asked, until the platform confirms where the sound went.
 */
enum class EarpieceRouting {
    /**
     * The phone reports no earpiece at all.
     *
     * Real, and not a fault: tablets and a few speakerphone-only handsets have none. Reported as a fact
     * rather than a failure.
     */
    ABSENT,

    /**
     * The platform would not route to the earpiece, or would not say where the sound went.
     *
     * Nothing was tested. Crucially the buyer is *not* asked whether they heard anything, because a "yes"
     * about a tone that came out of the loudspeaker would pass a dead earpiece.
     */
    REFUSED,

    /** The platform confirmed the earpiece as the output. Now the test means something. */
    CONFIRMED,
}

/**
 * Does the earpiece work — the speaker you hold against your ear?
 *
 * Separate from [SpeakerCheck] because they are separate parts. A phone can have a flawless loudspeaker
 * and a dead earpiece, and nothing in a shop reveals it: ringtones, videos and speakerphone all use the
 * loudspeaker. The buyer finds out on their first real call, after the money has changed hands. It is one
 * of the most common faults on a used handset and one of the least likely to be caught before paying.
 *
 * Measured the same way as the loudspeaker — play 1 kHz, listen for it — but with the odds much worse,
 * and the copy and thresholds reflect that rather than pretending otherwise:
 *
 *  - The earpiece is *designed* to be inaudible at arm's length. It is quiet on purpose.
 *  - It sits at the top of the phone and the primary microphone is usually at the bottom, so the sound
 *    has the length of the handset to travel.
 *
 * So a miss here says even less about the hardware than a miss on the loudspeaker does, and the buyer's
 * ear matters even more — which is convenient, because they are holding the thing against their ear.
 */
object EarpieceCheck {

    const val CHECK_ID: String = "hardware.earpiece"

    private const val TITLE = "Earpiece"

    /**
     * Lower than the loudspeaker's bar, and deliberately so.
     *
     * [SpeakerCheck.TONE_DETECTED_RATIO] is 0.25 for a transducer built to fill a room, firing across a
     * few centimetres into the microphone. The earpiece is built to be heard by one ear pressed against
     * it, from the opposite end of the phone. Holding it to the same standard would report a perfectly
     * good earpiece as silent on every handset.
     *
     * Still two orders of magnitude above what broadband room noise produces at a single frequency, so
     * this buys sensitivity without buying false positives.
     */
    const val TONE_DETECTED_RATIO: Float = 0.10f

    private val FALSE_POSITIVE_CAUSES = listOf(
        "The earpiece is quiet by design and the microphone is at the other end of the phone, so a " +
            "working one is often too faint to measure.",
        "Some phones report the earpiece as the output and still play through the loudspeaker.",
        "A finger or a case over the earpiece slot, or a screen protector covering it.",
        "In-call volume left low by the previous owner, which is a separate setting from media volume.",
        "A blocked microphone makes any working speaker undetectable, since the two are tested together.",
    )

    /**
     * @param routing where the platform says the tone went. Nothing is concluded without [CONFIRMED].
     * @param toneRatio share of the recording's energy at the test frequency.
     * @param roomFloor the recording's noise floor, for the report rather than for the verdict.
     * @param heard what the buyer said, when measuring could not decide.
     */
    fun evaluate(
        routing: EarpieceRouting,
        toneRatio: Float = 0f,
        roomFloor: Float = 0f,
        heard: HeardTone = HeardTone.NOT_ASKED,
    ): CheckResult {
        if (routing == EarpieceRouting.ABSENT) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                // HIGH: this is not a failure to measure, it is a measurement. The phone was asked what
                // outputs it has and it answered.
                confidence = Confidence.HIGH,
                headline = "This phone reports no earpiece, so there is nothing here to test.",
                action = "Normal for a tablet. On a phone, check that calls work through the top of " +
                    "the handset before you pay.",
                measurements = listOf(Measurement("Earpiece", "not present")),
            )
        }

        if (routing == EarpieceRouting.REFUSED) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "This phone would not let the app send sound to the earpiece, so it was " +
                    "not tested.",
                // No question asked, and that is the point. Whatever the buyer heard came out of
                // somewhere the app cannot name, and a "yes" about the loudspeaker would pass a dead
                // earpiece.
                action = "Make a real call, or ask the seller to. It is the only way left to check " +
                    "this one, and it is worth doing — a dead earpiece is invisible until you do.",
                measurements = listOf(Measurement("Routing", "refused")),
            )
        }

        val measurements = listOf(
            Measurement("Tone detected", "%.0f".format(toneRatio * 100f), "%"),
            Measurement("Room noise", formatDbfs(roomFloor)),
            Measurement("Routing", "earpiece confirmed"),
        )

        if (toneRatio >= TONE_DETECTED_RATIO) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                confidence = Confidence.HIGH,
                headline = "The microphone picked up the tone from the earpiece.",
                measurements = measurements,
            )
        }

        return when (heard) {
            HeardTone.YES -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                // MEDIUM, and the headline says whose finding it is. A report read a week later must not
                // look as though the app confirmed this itself.
                confidence = Confidence.MEDIUM,
                headline = "You heard it. The app could not — an earpiece is usually too quiet to " +
                    "measure from the other end of the phone.",
                measurements = measurements,
            )

            HeardTone.NO -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                // HIGH is earned here: the platform confirmed the earpiece was the output, the app heard
                // nothing, and neither did the ear pressed against it.
                confidence = Confidence.HIGH,
                headline = "Nothing came out of the earpiece — not for you, and not for the app.",
                consequence = "You would not be able to hear anyone on an ordinary call. Every call " +
                    "would have to go on speakerphone or headphones, which in public means everyone " +
                    "around you hears it too.",
                action = "Make a real call before you pay, to be certain. Replacing an earpiece means " +
                    "opening the phone. Get that repair quoted locally and take it off the price — or " +
                    "walk away.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )

            HeardTone.NOT_ASKED -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.LOW,
                headline = "The app could not hear the tone, which for an earpiece proves nothing.",
                action = "Hold the phone to your ear and run it again — your ear is the better " +
                    "instrument for this one.",
                measurements = measurements,
            )
        }
    }
}
