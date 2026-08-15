package com.phoneproof.feature.touchgrid

import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.GridSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM tests, no Robolectric.
 *
 * Robolectric reports no system bars, so the screenshot tests can never exercise this arithmetic
 * with real insets. Testing it directly is the only coverage it will ever get.
 */
class SystemGestureCellsTest {

    // 10 columns x 10 rows over a 1000x1000 canvas, so one cell is exactly 100px square and the
    // expected answers can be reasoned about without arithmetic.
    private val spec = GridSpec(columns = 10, rows = 10)

    @Test
    fun `no insets reserves nothing`() {
        assertTrue(systemGestureCells(spec, width = 1000, height = 1000).isEmpty())
    }

    @Test
    fun `a zero sized canvas reserves nothing rather than everything`() {
        // The first frame before layout. Reserving everything here would flash "nothing can be
        // tested" at the buyer.
        assertTrue(systemGestureCells(spec, width = 0, height = 0, top = 50).isEmpty())
        assertTrue(systemGestureCells(spec, width = 1000, height = 0, top = 50).isEmpty())
    }

    @Test
    fun `a top inset reserves whole rows across the full width`() {
        // 150px covers row 0 entirely and the top half of row 1. Row 1's centre is at 150, which is
        // not strictly less than 150, so only row 0 is gestures.
        val gestures = systemGestureCells(spec, width = 1000, height = 1000, top = 150)

        assertEquals((0 until 10).map { Cell(it, 0) }.toSet(), gestures)
    }

    @Test
    fun `a cell is gestures once its centre is covered, not on first overlap`() {
        // 151px passes the centre of row 1 (150px), so rows 0 and 1 both go.
        val gestures = systemGestureCells(spec, width = 1000, height = 1000, top = 151)
        val expected = (0 until 10).flatMap { column ->
            listOf(Cell(column, 0), Cell(column, 1))
        }.toSet()

        assertEquals(expected, gestures)
    }

    @Test
    fun `an inset thinner than half a cell reserves nothing`() {
        // 49px does not reach row 0's centre at 50px. Treating any overlap as gestures would write
        // off an entire row of testable screen for a sliver of inset.
        assertTrue(systemGestureCells(spec, width = 1000, height = 1000, top = 49).isEmpty())
    }

    @Test
    fun `a bottom inset reserves rows measured from the far edge`() {
        val gestures = systemGestureCells(spec, width = 1000, height = 1000, bottom = 150)

        assertEquals((0 until 10).map { Cell(it, 9) }.toSet(), gestures)
    }

    @Test
    fun `side insets reserve whole columns down the full height`() {
        val gestures = systemGestureCells(spec, width = 1000, height = 1000, left = 60, right = 60)

        val expected = (0 until 10).flatMap { row ->
            listOf(Cell(0, row), Cell(9, row))
        }.toSet()
        assertEquals(expected, gestures)
    }

    @Test
    fun `all four insets combine into a frame`() {
        val gestures = systemGestureCells(
            spec,
            width = 1000,
            height = 1000,
            left = 60,
            top = 60,
            right = 60,
            bottom = 60,
        )

        // A one-cell border: 36 of the 100 cells, with the 8x8 interior untouched.
        assertEquals(36, gestures.size)
        assertTrue(Cell(0, 0) in gestures)
        assertTrue(Cell(5, 0) in gestures)
        assertTrue(Cell(0, 5) in gestures)
        assertTrue(Cell(9, 9) in gestures)
        assertTrue(Cell(5, 5) !in gestures)
    }

    @Test
    fun `insets larger than the canvas reserve every cell without crashing`() {
        val gestures = systemGestureCells(spec, width = 1000, height = 1000, top = 5000)

        assertEquals(spec.cellCount, gestures.size)
    }

    @Test
    fun `the real grid and a realistic phone reserve only the edges`() {
        // The default 16x32 grid on the realme RMX5110's 1080x2392 panel, with gesture insets close
        // to what that phone reports: a status-bar band at the top, the home band at the bottom,
        // and a thin back-swipe column each side.
        val gestures = systemGestureCells(
            GridSpec.Default,
            width = 1080,
            height = 2392,
            left = 60,
            top = 130,
            right = 60,
            bottom = 130,
        )

        // A frame, not the whole screen: the check must keep most of the panel testable, otherwise
        // forgiving the edges would mean forgiving real defects too.
        assertTrue(gestures.size < GridSpec.Default.cellCount / 3)
        assertTrue(Cell(8, 0) in gestures)
        assertTrue(Cell(8, 31) in gestures)
        assertTrue(Cell(0, 16) in gestures)
        assertTrue(Cell(8, 16) !in gestures)
    }
}
