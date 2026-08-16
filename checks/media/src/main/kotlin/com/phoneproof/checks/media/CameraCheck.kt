package com.phoneproof.checks.media

import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement

/** Which way a camera points, in the words a buyer uses. */
enum class CameraFacing(val label: String) {
    BACK("Rear camera"),
    FRONT("Front camera"),
    OTHER("Camera"),
}

/**
 * What came back from pointing a camera at whatever it was pointed at.
 *
 * Reduced to four numbers on purpose. The app is not judging photographic quality — it has no idea what
 * the lens was aimed at, and a blurry picture of a shop ceiling is not a fault. It is answering a much
 * narrower question that a buyer genuinely cannot answer by looking at the viewfinder for two seconds:
 * **is this sensor delivering live, varying image data at all?**
 *
 * @param meanLuma average brightness of the frames, 0 to 1.
 * @param lumaVariation how much brightness varies *within* a frame, 0 to 1. A real scene has edges; a
 *   dead sensor is a flat field.
 * @param framesIdentical whether consecutive frames were byte-for-byte the same, which a live sensor
 *   never is — even pointed at a wall, noise differs frame to frame.
 */
data class CameraFrameStats(
    val facing: CameraFacing,
    val framesReceived: Int,
    val meanLuma: Float,
    val lumaVariation: Float,
    val framesIdentical: Boolean,
    /**
     * The sensor's own resolution, or null when the phone will not say.
     *
     * Reported, never judged. See [specNote] for why a number lower than the box claims proves nothing.
     */
    val sensorMegapixels: Float? = null,
    /** The largest still an app is actually allowed to request, which is often less than the above. */
    val largestPhotoMegapixels: Float? = null,
    /** Maximum zoom ratio the platform advertises, optical and digital together where it says. */
    val maxZoom: Float? = null,
)

/**
 * Is the camera alive?
 *
 * Every negative outcome here is a CAUTION rather than a FAIL, and that is not timidity. The app cannot
 * see what the camera was pointed at. A finger over the lens, a case with a misaligned cutout, a phone
 * lying face down on a counter and a genuinely dead sensor all produce the same flat dark frame, and the
 * app has no way to tell them apart. Reporting a fault would mean accusing a working phone whenever the
 * buyer's hand was in the way — which, on a phone being handed back and forth in a shop, is often.
 */
object CameraCheck {

    const val CHECK_ID: String = "hardware.camera"

    /**
     * Below this variation, the frame is a flat field rather than a scene.
     *
     * Deliberately very low. Any real image — a wall, a ceiling, a hand — has edges, gradients and sensor
     * noise, and lands far above this. Only a sensor returning a constant, or a lens completely blocked,
     * gets under it.
     */
    const val FLAT_FIELD_VARIATION: Float = 0.01f

    /** Below this mean brightness the frame is essentially black. */
    const val DARK_LUMA: Float = 0.06f

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A finger over the lens produces exactly this, and it is the most common cause by far.",
        "A case with a misaligned camera cutout can block the lens completely.",
        "A phone lying face down, or a rear camera pointed at a dark surface.",
        "Another app holding the camera — a scanner, a video call — takes it exclusively.",
    )

    /**
     * What the camera says about itself, in a sentence that cannot be mistaken for an accusation.
     *
     * Megapixels are worth reporting and dangerous to judge. A phone advertised as 50 MP will often hand
     * third-party apps far less, because full-resolution and pixel-binned modes are commonly reserved for
     * the manufacturer's own camera app. **A lower number here is not evidence of a fake sensor**, and a
     * buyer who took it to a seller as proof of fraud would be wrong and would deserve better from this
     * app. So the number is shown with the reason it might disagree with the box, and no verdict is
     * attached to it either way.
     */
    fun specNote(stats: CameraFrameStats): String? {
        val sensor = stats.sensorMegapixels ?: return null
        return "Reports a ${format(sensor)} MP sensor to apps. If the phone was advertised with a " +
            "bigger number that is not proof of anything — full-resolution modes are usually kept for " +
            "the phone's own camera app, and no other app can reach them."
    }

    fun evaluate(stats: CameraFrameStats): CheckResult {
        val title = stats.facing.label
        val measurements = buildList {
            add(Measurement("Frames", "${stats.framesReceived}"))
            if (stats.framesReceived > 0) {
                add(Measurement("Brightness", "${(stats.meanLuma * 100).toInt()}", "%"))
                add(Measurement("Detail", "${(stats.lumaVariation * 100).toInt()}", "%"))
            }
            // Reported whether or not the sensor imaged anything: a camera that will not open still has a
            // resolution, and a buyer comparing the handset to its advert wants the number regardless.
            stats.sensorMegapixels?.let { add(Measurement("Sensor", format(it), "MP")) }
            stats.largestPhotoMegapixels?.let {
                add(Measurement("Largest photo apps can take", format(it), "MP"))
            }
            stats.maxZoom?.let { add(Measurement("Maximum zoom", "${format(it)}×")) }
        }

        if (stats.framesReceived == 0) {
            return CheckResult(
                id = "$CHECK_ID.${stats.facing.name.lowercase()}",
                title = title,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The camera did not deliver a single frame.",
                consequence = "Either the camera is dead, or something else on the phone is holding " +
                    "it. A camera that cannot open is a repair, not a setting.",
                action = "Close any camera or video app, lock and unlock the phone, and try again. " +
                    "Then open the phone's own camera app — if that fails too, it is the hardware.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        // Frozen: the sensor is being read, and it is returning the same thing every time. A live sensor
        // never does, because its own noise differs frame to frame even against a blank wall.
        if (stats.framesIdentical && stats.framesReceived > 1) {
            return CheckResult(
                id = "$CHECK_ID.${stats.facing.name.lowercase()}",
                title = title,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "Every frame was identical, which a live sensor never is.",
                consequence = "The camera is responding but not seeing. Photos would come out as a " +
                    "frozen or blank image, and this usually means a failed sensor or a loose ribbon.",
                action = "Try again, then take an actual photo with the phone's camera app and look at " +
                    "it. If the picture is frozen or blank, walk away.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        if (stats.lumaVariation < FLAT_FIELD_VARIATION) {
            val dark = stats.meanLuma < DARK_LUMA
            return CheckResult(
                id = "$CHECK_ID.${stats.facing.name.lowercase()}",
                title = title,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = if (dark) {
                    "The camera returned a black frame with no detail in it."
                } else {
                    "The camera returned a flat frame with no detail in it."
                },
                consequence = "A working camera pointed at anything at all produces edges and " +
                    "texture. A flat field means either the lens is covered or the sensor is not " +
                    "imaging.",
                action = "Move your fingers clear of both lenses, point the phone at something with " +
                    "detail in it, and run this again. Check the case is not over the cutout.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        return CheckResult(
            id = "$CHECK_ID.${stats.facing.name.lowercase()}",
            title = title,
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "Live image, with detail in it.",
            // Deliberately says what has and has not been established. The app checked that the sensor
            // is imaging; it has no idea whether the picture is any good, and a buyer who reads a PASS
            // as "the camera takes nice photos" has been misled by omission.
            consequence = null,
            action = "Still worth taking one photo and one video with the phone's own camera app. " +
                "This proves the sensor works, not that the pictures are sharp.",
            measurements = measurements,
        )
    }
}

/**
 * Does the torch light up?
 *
 * Measure-then-ask, the same shape as the speaker, and for the same reason: the phone cannot see its own
 * flash. What is measurable is whether the camera reports a flash unit and whether the platform accepted
 * the request to switch it on. Whether photons left the phone is a question only the person holding it
 * can answer, so the app asks — and records the answer as theirs.
 */
object TorchCheck {

    const val CHECK_ID: String = "hardware.torch"

    private const val TITLE = "Flashlight"

    private val FALSE_POSITIVE_CAUSES = listOf(
        "A flash that has overheated is disabled by the phone until it cools, which looks identical.",
        "Very low battery disables the torch on many handsets.",
        "A finger or a case over the flash hides it completely.",
    )

    /**
     * @param flashAvailable whether the camera reports a flash unit at all.
     * @param accepted whether the platform accepted the request to turn it on.
     * @param lit what the buyer said, once asked.
     */
    fun evaluate(
        flashAvailable: Boolean,
        accepted: Boolean,
        lit: HeardTone = HeardTone.NOT_ASKED,
    ): CheckResult {
        val measurements = listOf(
            Measurement("Flash unit", if (flashAvailable) "reported" else "not reported"),
        )

        if (!flashAvailable) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "This phone reports no flash to test.",
                action = "If you can see a flash next to the rear lens, try the torch from the phone's " +
                    "own quick settings instead.",
                measurements = measurements,
            )
        }

        if (!accepted) {
            return CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.CAUTION,
                confidence = Confidence.MEDIUM,
                headline = "The phone refused to switch the torch on.",
                consequence = "The flash is declared but would not turn on, which points at the flash " +
                    "hardware, an overheated phone, or a very low battery.",
                action = "Let the phone cool, charge it above twenty percent, and try the torch from " +
                    "quick settings.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )
        }

        return when (lit) {
            HeardTone.YES -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.PASS,
                // MEDIUM, because this is the buyer's eyes rather than a measurement, and the headline
                // says so. The phone cannot see its own flash.
                confidence = Confidence.MEDIUM,
                headline = "You saw it light up.",
                measurements = measurements,
            )

            HeardTone.NO -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.FAIL,
                confidence = Confidence.MEDIUM,
                headline = "The phone switched the torch on and nothing lit up.",
                consequence = "The flash is almost certainly dead. Photos in low light will be poor " +
                    "and there is no torch, which is one of the things a phone gets used for daily.",
                action = "Check nothing is covering the flash and try once more from quick settings. " +
                    "A flash module is a real repair cost — use it to negotiate.",
                measurements = measurements,
                falsePositiveCauses = FALSE_POSITIVE_CAUSES,
            )

            HeardTone.NOT_ASKED -> CheckResult(
                id = CHECK_ID,
                title = TITLE,
                outcome = CheckOutcome.UNKNOWN,
                confidence = Confidence.HIGH,
                headline = "The torch is on. Look at the back of the phone.",
                action = "Say whether it lit up.",
                measurements = measurements,
            )
        }
    }
}

/** One decimal, and Locale.ROOT so a build machine cannot change what the screenshots contain. */
private fun format(value: Float): String = String.format(java.util.Locale.ROOT, "%.1f", value)
