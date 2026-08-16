package com.phoneproof.checks.media

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class CameraCheckTest {

    private fun stats(
        facing: CameraFacing = CameraFacing.BACK,
        frames: Int = 8,
        luma: Float = 0.4f,
        variation: Float = 0.2f,
        identical: Boolean = false,
    ) = CameraFrameStats(facing, frames, luma, variation, identical)

    @Test
    fun `a live image with detail in it passes`() {
        val result = CameraCheck.evaluate(stats())

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `a pass says what it has not established`() {
        // The omission that would mislead: a buyer reading PASS as "the camera takes good photos". The
        // app has no idea what the lens was pointed at and cannot judge a picture.
        val result = CameraCheck.evaluate(stats())

        assertThat(result.action).contains("not that the pictures are sharp")
    }

    @Test
    fun `no frames at all is a CAUTION, never a FAIL`() {
        val result = CameraCheck.evaluate(stats(frames = 0))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
        // Another app holding the camera is a real and common cause, and it is not a fault in the phone.
        assertThat(result.action).contains("Close any camera")
    }

    @Test
    fun `a flat black frame is a CAUTION and blames a finger first`() {
        val result = CameraCheck.evaluate(stats(luma = 0.01f, variation = 0.001f))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("black frame")
        // Order matters in a list read while deciding whether to walk away. A finger over the lens is by
        // far the most likely explanation and it belongs first.
        assertThat(result.falsePositiveCauses.first()).contains("finger over the lens")
    }

    @Test
    fun `a flat but bright frame is described as flat rather than black`() {
        // Pointed at a lightbox or a white wall in bright sun: no detail, but not dark. The wording has
        // to match what the buyer is looking at or they will not believe the app.
        val result = CameraCheck.evaluate(stats(luma = 0.8f, variation = 0.002f))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("flat frame")
        assertThat(result.headline).doesNotContain("black")
    }

    @Test
    fun `identical frames are caught even when the image looks fine`() {
        // The subtle failure. Brightness and detail both look healthy, but nothing is changing — which a
        // live sensor cannot do, because its own noise differs frame to frame against a blank wall.
        val result = CameraCheck.evaluate(stats(luma = 0.4f, variation = 0.3f, identical = true))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("identical")
    }

    @Test
    fun `a single frame is not judged as frozen`() {
        // One frame cannot be identical to a previous one. Without the frame-count guard, a camera that
        // managed exactly one capture would be reported as frozen.
        val result = CameraCheck.evaluate(stats(frames = 1, identical = true))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `each camera gets its own id so both can sit in one report`() {
        // A report with two rows both keyed "hardware.camera" would collapse into one, and the buyer
        // would silently lose whichever came second.
        val back = CameraCheck.evaluate(stats(facing = CameraFacing.BACK))
        val front = CameraCheck.evaluate(stats(facing = CameraFacing.FRONT))

        assertThat(back.id).isEqualTo("hardware.camera.back")
        assertThat(front.id).isEqualTo("hardware.camera.front")
        assertThat(back.id).isNotEqualTo(front.id)
        assertThat(back.title).isEqualTo("Rear camera")
        assertThat(front.title).isEqualTo("Front camera")
    }

    @Test
    fun `the flat-field threshold is the boundary it claims to be`() {
        assertThat(CameraCheck.evaluate(stats(variation = CameraCheck.FLAT_FIELD_VARIATION)).outcome)
            .isEqualTo(CheckOutcome.PASS)
        assertThat(
            CameraCheck.evaluate(stats(variation = CameraCheck.FLAT_FIELD_VARIATION - 0.001f)).outcome,
        ).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `no camera outcome is ever a FAIL, because the app cannot see the lens`() {
        // The governing rule of this check, asserted across every branch. A finger, a case, a dark
        // counter and a dead sensor are indistinguishable from here.
        listOf(
            stats(frames = 0),
            stats(luma = 0f, variation = 0f),
            stats(identical = true),
            stats(),
        ).forEach { assertThat(CameraCheck.evaluate(it).outcome).isNotEqualTo(CheckOutcome.FAIL) }
    }
}

class TorchCheckTest {

    @Test
    fun `no flash unit is UNKNOWN rather than a fault`() {
        val result = TorchCheck.evaluate(flashAvailable = false, accepted = false)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.consequence).isNull()
    }

    @Test
    fun `a refused torch is a CAUTION and offers the mundane causes`() {
        val result = TorchCheck.evaluate(flashAvailable = true, accepted = false)

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.falsePositiveCauses.first()).contains("overheated")
    }

    @Test
    fun `switched on but not yet answered is UNKNOWN and asks`() {
        val result = TorchCheck.evaluate(flashAvailable = true, accepted = true)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("Look at the back")
    }

    @Test
    fun `the buyer seeing it lit is a pass at medium confidence`() {
        val result = TorchCheck.evaluate(true, accepted = true, lit = HeardTone.YES)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        // Their eyes, not a measurement. The phone cannot see its own flash.
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.headline).contains("You saw")
    }

    @Test
    fun `switched on with nothing visible is a failure`() {
        val result = TorchCheck.evaluate(true, accepted = true, lit = HeardTone.NO)

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        // MEDIUM, not HIGH: this rests entirely on one person's report in unknown lighting.
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.consequence).isNotEmpty()
        assertThat(result.falsePositiveCauses).isNotEmpty()
    }

    @Test
    fun `check ids are stable`() {
        assertThat(TorchCheck.evaluate(true, true).id).isEqualTo("hardware.torch")
    }
}
