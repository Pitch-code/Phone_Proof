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

    // ---------------------------------------------------------------------------------------
    // Cells Android reserves for its own edge gestures.
    //
    // Reproduces a false alarm from a realme RMX5110: a flawless screen reported CAUTION over
    // three cells along the top edge, because the system took those swipes to open the
    // notification shade and the app never received them.
    // ---------------------------------------------------------------------------------------

    /** The top row, standing in for the strip the shade gesture occupies. */
    private val topStrip: Set<Cell> = (0 until spec.columns).map { Cell(it, 0) }.toSet()

    private fun coverage(untouched: Set<Cell>, reserved: Set<Cell>): TouchCoverage {
        val all = buildSet {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) add(Cell(column, row))
            }
        }
        return TouchCoverage(spec, all - untouched, reserved)
    }

    @Test
    fun `uncovered cells inside a reserved strip pass instead of accusing the screen`() {
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        // The whole point of the fix. This was CAUTION on real hardware.
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `a pass with untestable cells is honest about it rather than claiming certainty`() {
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        val notTestable = result.measurements.first { it.label == "Not testable" }
        assertThat(notTestable.display).isEqualTo("3 cells")
        assertThat(result.headline).contains("the app can read")
    }

    @Test
    fun `covering the reserved strip earns full confidence and no disclaimer`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet(), topStrip))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.measurements.map { it.label }).doesNotContain("Not testable")
    }

    @Test
    fun `a real dead zone outside the reserved strip still fails`() {
        // The fix must not become a blanket amnesty: forgiving the strip cannot cost the app its
        // ability to catch an actual dead patch.
        val result = TouchCoverageEvaluator.evaluate(coverage(block(5, 5, 2, 2), topStrip))

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `a patch straddling the strip is judged only on the part that could be read`() {
        // Four contiguous uncovered cells, two of them unreachable. Counting all four would clear
        // DEAD_ZONE_MIN_CELLS and report a fault the app cannot actually evidence; only the two
        // readable cells count, which is a scattered-miss CAUTION.
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `the judging threshold is measured against reachable cells only`() {
        // Two reserved rows left uncovered, every reachable cell covered. Raw coverage is 80%,
        // under the 90% threshold, so judging on the raw ratio would strand the tester on
        // "keep going" forever on a phone with wide gesture strips.
        val reserved = topStrip + (0 until spec.columns).map { Cell(it, 1) }
        val subject = coverage(reserved, reserved)

        assertThat(subject.coverageRatio).isEqualTo(0.80f)
        assertThat(subject.testableCoverageRatio).isEqualTo(1.0f)

        val result = TouchCoverageEvaluator.evaluate(subject)
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `the report counts coverage over reachable cells, not every cell`() {
        // The realme reading that prompted this: 3 cells under the gesture strip, 509 covered out of
        // 512. Reporting "509 / 512, 99.4%" told the buyer they had missed three tiles and made a
        // flawless screen look imperfect, which is what the "Not testable" row exists to prevent.
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        assertThat(result.measurements.first { it.label == "Cells covered" }.display)
            .isEqualTo("${spec.cellCount - topStrip.size} / ${spec.cellCount - topStrip.size}")
        assertThat(result.measurements.first { it.label == "Coverage" }.display)
            .isEqualTo("100.0 %")
        // The forgiven cells are still disclosed rather than vanishing into a rounder number.
        assertThat(result.measurements.first { it.label == "Not testable" }.display)
            .isEqualTo("3 cells")
    }

    @Test
    fun `with nothing reserved the count is unchanged`() {
        // Guards the common case: a phone that reserves nothing must still report every cell.
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet(), emptySet()))

        assertThat(result.measurements.first { it.label == "Cells covered" }.display)
            .isEqualTo("${spec.cellCount} / ${spec.cellCount}")
        assertThat(result.measurements.map { it.label }).doesNotContain("Not testable")
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet()))
        assertThat(result.id).isEqualTo("screen.touch_coverage")
    }
}
