package com.phoneproof.checks.touch

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class TouchCoverageEvaluatorTest {

    private val spec = GridSpec(columns = 10, rows = 10)

    private fun coverage(untouched: Set<Cell>): TouchCoverage {
        val all = buildSet {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) add(Cell(column, row))
            }
        }
        return TouchCoverage(spec, all - untouched)
    }

    /** A solid rectangle of untouched cells, i.e. a genuine dead patch. */
    private fun block(x: Int, y: Int, width: Int, height: Int): Set<Cell> = buildSet {
        for (row in y until y + height) {
            for (column in x until x + width) add(Cell(column, row))
        }
    }

    @Test
    fun `sparse coverage is UNKNOWN and never a failure`() {
        val result = TouchCoverageEvaluator.evaluate(TouchCoverage(spec, emptySet()))
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        // The critical property: an unfinished test must not accuse the phone.
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
        assertThat(result.consequence).isNull()
    }

    @Test
    fun `coverage just below the judging threshold stays UNKNOWN`() {
        // 11 of 100 cells missing -> 89 percent, one under the 90 percent threshold.
        // The grid is only 10 wide, so this is a full top row plus one cell on the next.
        val untouched = block(0, 0, 10, 1) + Cell(0, 1)
        val subject = coverage(untouched)
        assertThat(subject.touchedCount).isEqualTo(89)

        val result = TouchCoverageEvaluator.evaluate(subject)
        // Even though those 11 cells are contiguous and would otherwise read as a defect,
        // insufficient coverage must win: the test simply is not finished yet.
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `coverage exactly at the threshold is judged rather than skipped`() {
        val untouched = block(0, 0, 10, 1)
        val subject = coverage(untouched)
        assertThat(subject.coverageRatio).isEqualTo(0.90f)

        val result = TouchCoverageEvaluator.evaluate(subject)
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("top edge")
    }

    @Test
    fun `full coverage passes`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet()))
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.headline).isNotEmpty()
    }

    @Test
    fun `a contiguous block above the threshold is a FAIL with high confidence`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(block(7, 7, 2, 2)))
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.headline).contains("bottom-right")
    }

    @Test
    fun `a FAIL always carries consequence action and false positive causes`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(block(0, 0, 2, 2)))
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.consequence).isNotEmpty()
        assertThat(result.action).isNotEmpty()
        assertThat(result.falsePositiveCauses).isNotEmpty()
        assertThat(result.retestable).isTrue()
    }

    @Test
    fun `scattered single misses are CAUTION not FAIL`() {
        val scattered = setOf(Cell(0, 0), Cell(4, 4), Cell(9, 9))
        val result = TouchCoverageEvaluator.evaluate(coverage(scattered))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.action).contains("once more")
    }

    @Test
    fun `three contiguous cells stay below the defect threshold`() {
        // DEAD_ZONE_MIN_CELLS is 4, so an L of three is still treated as finger skips.
        val nearly = setOf(Cell(1, 1), Cell(2, 1), Cell(1, 2))
        val result = TouchCoverageEvaluator.evaluate(coverage(nearly))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `multiple dead areas are counted and mentioned`() {
        val two = block(0, 0, 2, 2) + block(8, 8, 2, 2)
        val result = TouchCoverageEvaluator.evaluate(coverage(two))
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("1 more area")
        assertThat(result.measurements.map { it.label }).contains("Dead areas")
    }

    @Test
    fun `measurements always report covered cells and percentage`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet()))
        val labels = result.measurements.map { it.label }
        assertThat(labels).contains("Cells covered")
        assertThat(labels).contains("Coverage")
        assertThat(result.measurements.first { it.label == "Coverage" }.display).isEqualTo("100.0 %")
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet()))
        assertThat(result.id).isEqualTo("screen.touch_coverage")
    }
}
