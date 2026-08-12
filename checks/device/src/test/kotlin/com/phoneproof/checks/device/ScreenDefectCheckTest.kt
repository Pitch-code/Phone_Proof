package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class ScreenDefectCheckTest {

    private val total = 6

    @Test
    fun `a clean screen after every pattern passes`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, total, total)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `a clean report never claims high confidence`() {
        // The evidence is a person glancing at a screen in a shop, not a measurement. HIGH here
        // would dress up an opinion as a reading.
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, total, total)

        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.confidence).isNotEqualTo(Confidence.HIGH)
    }

    @Test
    fun `an abandoned test is unknown, not a pass`() {
        // Tapping through two of six and saying "looked fine" is not a tested screen. Calling it a
        // PASS would be the app inventing a clean bill of health out of an abandoned test.
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, 2, total)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("not fully checked")
    }

    @Test
    fun `viewing nothing at all is unknown`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, 0, total)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `patches are a failure because burn-in is permanent`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.LARGE_PATCHES, total, total)

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.consequence).contains("permanent")
        assertThat(result.action).isNotEmpty()
    }

    @Test
    fun `dots are a caution, not a failure`() {
        // One stuck pixel is a blemish, not a broken phone, and the app can neither count them nor
        // judge where they sit. That is the buyer's call, so it must not be made for them.
        val result = ScreenDefectCheck.evaluate(ScreenFinding.SMALL_DOTS, total, total)

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `a defect seen part way through still counts`() {
        // You cannot un-see a stuck pixel. Only the clean answer depends on a complete run.
        val dots = ScreenDefectCheck.evaluate(ScreenFinding.SMALL_DOTS, 1, total)
        val patches = ScreenDefectCheck.evaluate(ScreenFinding.LARGE_PATCHES, 1, total)

        assertThat(dots.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(patches.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `every negative outcome admits it is reported rather than measured`() {
        listOf(ScreenFinding.SMALL_DOTS, ScreenFinding.LARGE_PATCHES).forEach { finding ->
            val result = ScreenDefectCheck.evaluate(finding, total, total)

            assertThat(result.falsePositiveCauses).isNotEmpty()
            assertThat(result.falsePositiveCauses.first()).contains("not something the app measured")
        }
    }

    @Test
    fun `dust and screen protectors are named as causes`() {
        // The two things that look exactly like a dead pixel and are not one.
        val causes = ScreenDefectCheck.evaluate(ScreenFinding.SMALL_DOTS, total, total)
            .falsePositiveCauses
            .joinToString(" ")
            .lowercase()

        assertThat(causes).contains("dust")
        assertThat(causes).contains("screen protector")
    }

    @Test
    fun `the report says the result came from the person, not the phone`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, total, total)

        assertThat(result.measurements.first { it.label == "Source" }.display)
            .isEqualTo("what you saw")
    }

    @Test
    fun `patterns viewed is always reported`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, 3, total)

        assertThat(result.measurements.first { it.label == "Patterns viewed" }.display)
            .isEqualTo("3 / 6")
    }

    @Test
    fun `a viewed count beyond the total is clamped rather than trusted`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, 99, total)

        assertThat(result.measurements.first { it.label == "Patterns viewed" }.display)
            .isEqualTo("6 / 6")
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `a negative viewed count is clamped to zero`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, -5, total)

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `the singular reads correctly for one pattern`() {
        val result = ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, 1, total)

        assertThat(result.headline).contains("1 pattern of 6 was viewed")
    }

    @Test
    fun `a zero pattern total is rejected rather than dividing the report by nothing`() {
        assertThat(
            runCatching { ScreenDefectCheck.evaluate(ScreenFinding.NOTHING, 0, 0) }.isFailure,
        ).isTrue()
    }

    @Test
    fun `check id is stable and sits in the screen namespace`() {
        assertThat(ScreenDefectCheck.CHECK_ID).isEqualTo("screen.defects")
        assertThat(ScreenDefectCheck.CHECK_ID.substringBefore('.')).isEqualTo("screen")
    }
}
