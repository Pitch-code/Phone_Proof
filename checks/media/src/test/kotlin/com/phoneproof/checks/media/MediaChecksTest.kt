package com.phoneproof.checks.media

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class MicrophoneCheckTest {

    @Test
    fun `speech heard clearly is a pass`() {
        val result = MicrophoneCheck.evaluate(
            analyse(Signals.speechBurst(2f, floorAmplitude = 0.01f, burstAmplitude = 0.3f)),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `a loud room with nobody speaking is UNKNOWN, not a fault`() {
        // The important one. The microphone is plainly delivering audio, so calling it broken would be a
        // false accusation; but nothing stood out, so calling it working would be a guess.
        val result = MicrophoneCheck.evaluate(analyse(Signals.noise(2f, amplitude = 0.4f)))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `complete silence is a CAUTION and blames the toggle before the hardware`() {
        val result = MicrophoneCheck.evaluate(analyse(Signals.digitalSilence(2f)))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        // Never a FAIL: a system-wide mic toggle and another app holding the input both produce exactly
        // this, and neither is a fault in the phone being bought.
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
        assertThat(result.falsePositiveCauses.first()).contains("privacy toggle")
    }

    @Test
    fun `nothing recorded at all is UNKNOWN with no consequence`() {
        val result = MicrophoneCheck.evaluate(analyse(AudioWindow(Signals.RATE, ShortArray(4))))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.consequence).isNull()
        assertThat(result.action).contains("Allow the microphone")
    }

    @Test
    fun `clipping is still a pass, because it proves the microphone works`() {
        val samples = ShortArray((Signals.RATE * 2f).toInt())
        // A quiet floor with a full-scale burst on top of it.
        val random = kotlin.random.Random(3)
        for (index in samples.indices) {
            val loud = index in samples.size / 3 until samples.size / 2
            samples[index] = if (loud) {
                if (index % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE
            } else {
                ((random.nextFloat() * 2f - 1f) * 0.005f * 32767).toInt().toShort()
            }
        }
        val result = MicrophoneCheck.evaluate(analyse(AudioWindow(Signals.RATE, samples)))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("distort")
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        val result = MicrophoneCheck.evaluate(analyse(Signals.digitalSilence(1f)))
        assertThat(result.id).isEqualTo("hardware.microphone")
    }
}

class SpeakerCheckTest {

    @Test
    fun `hearing the tone is a measured pass, and nothing is asked`() {
        val result = SpeakerCheck.evaluate(toneRatio = 0.8f, roomFloor = 0.01f)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        // No question, because there is nothing left to ask.
        assertThat(result.headline).contains("picked up the test tone")
    }

    @Test
    fun `a noisy room with no detection is UNKNOWN and asks rather than accuses`() {
        // The heart of measure-then-ask. In this much noise a working speaker is undetectable, so the
        // app must not conclude anything about it.
        val result = SpeakerCheck.evaluate(toneRatio = 0.05f, roomFloor = 0.3f)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("background noise")
        assertThat(result.action).contains("heard the tone")
    }

    @Test
    fun `a quiet room with no detection is still UNKNOWN, and says the room was quiet`() {
        // Suggestive, and still not proof: the microphone and the volume setting are both in the way.
        val result = SpeakerCheck.evaluate(toneRatio = 0.02f, roomFloor = 0.005f)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("quiet room")
        assertThat(result.consequence).contains("may be")
    }

    @Test
    fun `the buyer saying yes is a pass at medium confidence, and says whose finding it is`() {
        val result = SpeakerCheck.evaluate(0.05f, roomFloor = 0.3f, heard = HeardTone.YES)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        // Not HIGH. A saved report read a week later must not look like the app confirmed this itself.
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.headline).contains("You heard")
    }

    @Test
    fun `both the app and the buyer missing the tone is a confident failure`() {
        val result = SpeakerCheck.evaluate(0.01f, roomFloor = 0.005f, heard = HeardTone.NO)

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        // The one place HIGH is justified: two independent misses on the same tone.
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.consequence).isNotEmpty()
        assertThat(result.action).isNotEmpty()
        assertThat(result.falsePositiveCauses).isNotEmpty()
    }

    @Test
    fun `a detected tone outranks the buyer saying no`() {
        // Deliberate ordering. If the microphone recorded the tone then sound was produced and travelled,
        // whatever the buyer thinks they heard in a loud room.
        val result = SpeakerCheck.evaluate(0.9f, roomFloor = 0.2f, heard = HeardTone.NO)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `the threshold is the boundary it claims to be`() {
        val floor = 0.005f
        assertThat(SpeakerCheck.evaluate(SpeakerCheck.TONE_DETECTED_RATIO, floor).outcome)
            .isEqualTo(CheckOutcome.PASS)
        assertThat(SpeakerCheck.evaluate(SpeakerCheck.TONE_DETECTED_RATIO - 0.01f, floor).outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `a failure lists the noise and the volume before the hardware`() {
        // Order matters in a list a buyer reads while deciding whether to walk away. The two most common
        // explanations are not faults at all.
        val causes = SpeakerCheck.evaluate(0.01f, 0.005f, HeardTone.NO).falsePositiveCauses

        assertThat(causes[0]).contains("noise")
        assertThat(causes[1]).contains("volume")
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        assertThat(SpeakerCheck.evaluate(0.9f, 0.01f).id).isEqualTo("hardware.speaker")
    }
}
