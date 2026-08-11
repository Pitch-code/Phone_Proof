package com.phoneproof.checks.touch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TouchCoverageTest {

    private val spec = GridSpec(columns = 4, rows = 4)

    private fun coverageWithUntouched(vararg untouched: Cell): TouchCoverage {
        val all = buildSet {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) add(Cell(column, row))
            }
        }
        return TouchCoverage(spec, all - untouched.toSet())
    }

    @Test
    fun `grid rejects non positive dimensions`() {
        runCatching { GridSpec(0, 4) }.also { assertThat(it.isFailure).isTrue() }
        runCatching { GridSpec(4, -1) }.also { assertThat(it.isFailure).isTrue() }
    }

    @Test
    fun `full coverage reports no dead zones`() {
        val coverage = coverageWithUntouched()
        assertThat(coverage.coverageRatio).isEqualTo(1f)
        assertThat(coverage.untouchedCells).isEmpty()
        assertThat(coverage.deadZones()).isEmpty()
    }

    @Test
    fun `empty coverage is one single dead zone spanning the grid`() {
        val coverage = TouchCoverage(spec, emptySet())
        assertThat(coverage.coverageRatio).isEqualTo(0f)
        val zones = coverage.deadZones()
        assertThat(zones).hasSize(1)
        assertThat(zones.first().size).isEqualTo(16)
    }

    @Test
    fun `orthogonally adjacent untouched cells form one zone`() {
        val coverage = coverageWithUntouched(Cell(1, 1), Cell(1, 2), Cell(2, 2))
        val zones = coverage.deadZones()
        assertThat(zones).hasSize(1)
        assertThat(zones.first().size).isEqualTo(3)
    }

    @Test
    fun `diagonally touching cells are counted as separate zones`() {
        // This is the deliberate design choice: a corner-only touch is far more likely to be
        // two independent finger skips than a single physical defect.
        val coverage = coverageWithUntouched(Cell(0, 0), Cell(1, 1))
        val zones = coverage.deadZones()
        assertThat(zones).hasSize(2)
        assertThat(zones.map { it.size }).containsExactly(1, 1)
    }

    @Test
    fun `separate zones are reported largest first`() {
        val coverage = coverageWithUntouched(
            Cell(3, 3),
            Cell(0, 0), Cell(1, 0), Cell(0, 1),
        )
        val zones = coverage.deadZones()
        assertThat(zones).hasSize(2)
        assertThat(zones[0].size).isEqualTo(3)
        assertThat(zones[1].size).isEqualTo(1)
    }

    @Test
    fun `region naming places a zone in the expected third of the screen`() {
        val big = GridSpec(columns = 9, rows = 9)
        assertThat(DeadZone(setOf(Cell(0, 0))).region(big)).isEqualTo(ScreenRegion.TOP_LEFT)
        assertThat(DeadZone(setOf(Cell(4, 4))).region(big)).isEqualTo(ScreenRegion.CENTRE)
        assertThat(DeadZone(setOf(Cell(8, 8))).region(big)).isEqualTo(ScreenRegion.BOTTOM_RIGHT)
        assertThat(DeadZone(setOf(Cell(8, 0))).region(big)).isEqualTo(ScreenRegion.TOP_RIGHT)
        assertThat(DeadZone(setOf(Cell(0, 8))).region(big)).isEqualTo(ScreenRegion.BOTTOM_LEFT)
        assertThat(DeadZone(setOf(Cell(4, 0))).region(big)).isEqualTo(ScreenRegion.TOP)
        assertThat(DeadZone(setOf(Cell(0, 4))).region(big)).isEqualTo(ScreenRegion.LEFT)
    }

    @Test
    fun `dead zone cannot be empty`() {
        assertThat(runCatching { DeadZone(emptySet()) }.isFailure).isTrue()
    }
}

class TouchCoverageTrackerTest {

    private val tracker = TouchCoverageTracker(GridSpec(columns = 10, rows = 20))

    @Test
    fun `mark returns true only the first time a cell is covered`() {
        assertThat(tracker.mark(Cell(3, 3))).isTrue()
        assertThat(tracker.mark(Cell(3, 3))).isFalse()
        assertThat(tracker.touchedCount).isEqualTo(1)
    }

    @Test
    fun `marks outside the grid are rejected`() {
        assertThat(tracker.mark(Cell(-1, 0))).isFalse()
        assertThat(tracker.mark(Cell(0, -1))).isFalse()
        assertThat(tracker.mark(Cell(10, 0))).isFalse()
        assertThat(tracker.mark(Cell(0, 20))).isFalse()
        assertThat(tracker.touchedCount).isEqualTo(0)
    }

    @Test
    fun `normalised origin maps to the first cell`() {
        tracker.markNormalised(0f, 0f)
        assertThat(tracker.isTouched(Cell(0, 0))).isTrue()
    }

    @Test
    fun `normalised far corner maps to the last cell and never overflows`() {
        tracker.markNormalised(1f, 1f)
        assertThat(tracker.isTouched(Cell(9, 19))).isTrue()
        assertThat(tracker.touchedCount).isEqualTo(1)
    }

    @Test
    fun `normalised midpoint maps to the middle of the grid`() {
        tracker.markNormalised(0.5f, 0.5f)
        assertThat(tracker.isTouched(Cell(5, 10))).isTrue()
    }

    @Test
    fun `out of range and non finite values are ignored rather than clamped`() {
        // Clamping would silently credit coverage to an edge cell the finger never reached.
        assertThat(tracker.markNormalised(-0.01f, 0.5f)).isNull()
        assertThat(tracker.markNormalised(1.01f, 0.5f)).isNull()
        assertThat(tracker.markNormalised(Float.NaN, 0.5f)).isNull()
        assertThat(tracker.markNormalised(0.5f, Float.POSITIVE_INFINITY)).isNull()
        assertThat(tracker.touchedCount).isEqualTo(0)
    }

    @Test
    fun `a repeated point returns null so callers can skip redundant state updates`() {
        // A fast drag reports the same cell many times, including historical samples. Returning
        // null on a duplicate is what keeps the UI from rebuilding state on every one of them.
        assertThat(tracker.markNormalised(0.25f, 0.25f)).isNotNull()
        assertThat(tracker.markNormalised(0.25f, 0.25f)).isNull()
        assertThat(tracker.markNormalised(0.251f, 0.251f)).isNull()
        assertThat(tracker.touchedCount).isEqualTo(1)
    }

    @Test
    fun `reset clears all coverage`() {
        tracker.markNormalised(0.2f, 0.2f)
        tracker.markNormalised(0.8f, 0.8f)
        assertThat(tracker.touchedCount).isEqualTo(2)
        tracker.reset()
        assertThat(tracker.touchedCount).isEqualTo(0)
        assertThat(tracker.snapshot().untouchedCells).hasSize(200)
    }

    @Test
    fun `snapshot is immutable and does not track later marks`() {
        tracker.markNormalised(0.1f, 0.1f)
        val snapshot = tracker.snapshot()
        tracker.markNormalised(0.9f, 0.9f)
        assertThat(snapshot.touchedCount).isEqualTo(1)
        assertThat(tracker.touchedCount).isEqualTo(2)
    }

    @Test
    fun `every cell can be covered by sweeping normalised space`() {
        val small = TouchCoverageTracker(GridSpec(8, 8))
        var y = 0f
        while (y <= 1f) {
            var x = 0f
            while (x <= 1f) {
                small.markNormalised(x, y)
                x += 0.01f
            }
            y += 0.01f
        }
        assertThat(small.snapshot().coverageRatio).isEqualTo(1f)
    }
}


class TouchCoverageReservedCellsTest {

    private val spec = GridSpec(columns = 4, rows = 4)

    private fun coverage(untouched: Set<Cell>, reserved: Set<Cell>): TouchCoverage {
        val all = buildSet {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) add(Cell(column, row))
            }
        }
        return TouchCoverage(spec, all - untouched, reserved)
    }

    private val topRow: Set<Cell> = (0 until spec.columns).map { Cell(it, 0) }.toSet()

    @Test
    fun `the two groupings differ exactly by the reserved cells`() {
        // This is the mechanism behind the false CAUTION, stated as an assertion. The same three
        // uncovered cells are one group of three when the platform's strip is ignored, and no
        // group at all once it is accounted for. The old code read the first number.
        val untouched = setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        val subject = coverage(untouched, topRow)

        assertThat(subject.deadZones()).hasSize(1)
        assertThat(subject.deadZones().first().size).isEqualTo(3)
        assertThat(subject.testableDeadZones()).isEmpty()
    }

    @Test
    fun `removing reserved cells can split one apparent patch into two real ones`() {
        // Three uncovered cells in a row where only the middle one is reserved. That middle cell is
        // the sole thing joining the outer two, so grouping before removal reports a single
        // three-cell patch, while the honest reading is two unrelated single-cell skips. This is
        // why reserved cells are removed before grouping rather than subtracted from the totals
        // afterwards.
        val untouched = setOf(Cell(0, 1), Cell(1, 1), Cell(2, 1))
        val subject = coverage(untouched, setOf(Cell(1, 1)))

        assertThat(subject.deadZones()).hasSize(1)
        assertThat(subject.deadZones().first().size).isEqualTo(3)

        val testable = subject.testableDeadZones()
        assertThat(testable).hasSize(2)
        assertThat(testable.map { it.size }).containsExactly(1, 1)
    }

    @Test
    fun `untested reserved cells count only the reserved ones left uncovered`() {
        val untouched = setOf(Cell(0, 0), Cell(2, 2))
        val subject = coverage(untouched, topRow)

        // Cell(2,2) is uncovered but not reserved, and the other three reserved cells were covered.
        assertThat(subject.untestedReservedCells).containsExactly(Cell(0, 0))
    }

    @Test
    fun `testable coverage ignores reserved cells in both halves of the fraction`() {
        val subject = coverage(topRow, topRow)

        assertThat(subject.testableCellCount).isEqualTo(12)
        assertThat(subject.coverageRatio).isEqualTo(0.75f)
        assertThat(subject.testableCoverageRatio).isEqualTo(1f)
    }

    @Test
    fun `a grid that is entirely reserved reports zero rather than dividing by zero`() {
        val all = buildSet {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) add(Cell(column, row))
            }
        }
        val subject = TouchCoverage(spec, all, all)

        assertThat(subject.testableCellCount).isEqualTo(0)
        assertThat(subject.testableCoverageRatio).isEqualTo(0f)
    }

    @Test
    fun `default reserved set is empty so existing behaviour is untouched`() {
        val subject = TouchCoverage(spec, emptySet())

        assertThat(subject.reservedCells).isEmpty()
        assertThat(subject.testableCellCount).isEqualTo(subject.cellCount)
        assertThat(subject.testableDeadZones()).isEqualTo(subject.deadZones())
    }
}
