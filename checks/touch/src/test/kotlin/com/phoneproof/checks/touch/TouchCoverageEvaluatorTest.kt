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
    // The strips Android uses for its own edge gestures, which are now swept rather than excused.
    //
    // The original false alarm still governs these tests: a flawless realme RMX5110 reported CAUTION
    // over three cells along the top edge, because the system took those swipes to open the shade and
    // the app never received them. What changed is the remedy. The strips used to be removed from the
    // verdict, which meant a PASS; now they are part of the test, and a gap left inside one is
    // reported as UNKNOWN — unattributable — with an instruction to sweep again.
    //
    // The thing that must not regress in either design: a working screen is never accused.
    // ---------------------------------------------------------------------------------------

    /** The top row, standing in for the strip the shade gesture occupies. */
    private val topStrip: Set<Cell> = (0 until spec.columns).map { Cell(it, 0) }.toSet()

    private fun coverage(untouched: Set<Cell>, gestures: Set<Cell>): TouchCoverage {
        val all = buildSet {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) add(Cell(column, row))
            }
        }
        return TouchCoverage(spec, all - untouched, gestures)
    }

    @Test
    fun `uncovered cells inside a gesture strip are unattributable, never an accusation`() {
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        // The line that must never move. This was CAUTION on real hardware, and a CAUTION about a
        // working screen teaches the buyer to discount every later result.
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `an unattributable gap says so, and says what to do about it`() {
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.measurements.first { it.label == "Unattributed" }.display)
            .isEqualTo("3 cells")
        assertThat(result.action).isNotNull()
        // Not a PASS with small print. A buyer reads the badge, not the footnote.
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `sweeping the strip successfully earns a plain full-confidence pass`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet(), topStrip))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.measurements.map { it.label }).doesNotContain("Unattributed")
    }

    @Test
    fun `a real dead zone outside the strips still fails`() {
        // Not a blanket amnesty: treating the strips gently cannot cost the app its ability to catch
        // an actual dead patch.
        val result = TouchCoverageEvaluator.evaluate(coverage(block(5, 5, 2, 2), topStrip))

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `a patch straddling a strip is judged on the part the system could not have taken`() {
        // Four contiguous uncovered cells, two of them inside the strip. The two outside it are real
        // evidence, and two cells is under DEAD_ZONE_MIN_CELLS, so this is a scattered-miss CAUTION
        // rather than either a FAIL or a shrug.
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `a strip left entirely unswept is not enough coverage to judge`() {
        // Two whole rows of a five-row grid missed, so raw coverage is 80% and under the threshold.
        //
        // The old design deliberately passed this, by measuring against a denominator that excluded
        // the strips: the tester was told the job was done without ever having swept the edges. That
        // is precisely what the product owner reversed, so the assertion is now the opposite one.
        val strips = topStrip + (0 until spec.columns).map { Cell(it, 1) }
        val subject = coverage(strips, strips)

        assertThat(subject.coverageRatio).isEqualTo(0.80f)

        val result = TouchCoverageEvaluator.evaluate(subject)
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("Not enough")
    }

    @Test
    fun `the report counts every cell, with no second denominator`() {
        // The realme reading, recounted under the new rule: three cells in the strip left uncovered
        // now read as 12 / 15 rather than 12 / 12, because those three are part of the test.
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val result = TouchCoverageEvaluator.evaluate(coverage(untouched, topStrip))

        assertThat(result.measurements.first { it.label == "Cells covered" }.display)
            .isEqualTo("${spec.cellCount - 3} / ${spec.cellCount}")
        assertThat(result.measurements.map { it.label }).doesNotContain("Not testable")
    }

    @Test
    fun `with no strips known a gap is still the phone's to answer for`() {
        // The three-button phone and the Robolectric case. With no gesture insets reported, nothing
        // is unattributable, so a real patch must still fail rather than being quietly excused.
        val result = TouchCoverageEvaluator.evaluate(coverage(block(5, 5, 2, 2), emptySet()))

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        val result = TouchCoverageEvaluator.evaluate(coverage(emptySet()))
        assertThat(result.id).isEqualTo("screen.touch_coverage")
    }
}
