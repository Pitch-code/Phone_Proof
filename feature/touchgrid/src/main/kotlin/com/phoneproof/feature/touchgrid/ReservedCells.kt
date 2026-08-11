package com.phoneproof.feature.touchgrid

import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.GridSpec

/**
 * Maps the edge strips Android reserves for its own gestures onto grid cells.
 *
 * The strips are read from `WindowInsets.systemGestures` at runtime rather than assumed, because
 * they differ by phone and by navigation style: a gesture-navigation phone reserves a tall band at
 * the bottom and thin bands at both sides for the back swipe, while a three-button phone reserves
 * almost nothing at the sides. Hardcoding a guess would either forgive a real dead zone on one
 * phone or keep raising a false alarm on another.
 *
 * Deliberately a plain function over integers rather than something that reads insets itself. The
 * screenshot tests run under Robolectric, which reports no system bars at all, so this arithmetic
 * would be permanently untested if it were welded to the Compose inset APIs.
 *
 * A cell is reserved when **its centre** falls inside a strip, meaning more than half of it is
 * unreachable. Treating any overlap as reserved was the obvious alternative and is worse: a strip
 * one pixel into a row would write off that entire row across the screen, throwing away testable
 * area and with it the ability to spot a genuine defect near the edge.
 *
 * @param width canvas width in pixels.
 * @param height canvas height in pixels.
 * @param left inset from the left edge in pixels, and so on for [top], [right] and [bottom].
 * @return every cell whose centre lies in a reserved strip, or an empty set when nothing is
 *   reserved — which is the normal case under Robolectric and on a phone with no gesture insets.
 */
internal fun reservedCells(
    spec: GridSpec,
    width: Int,
    height: Int,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): Set<Cell> {
    // A zero-sized canvas happens on the first frame before layout. Reporting every cell as
    // reserved for that frame would briefly tell the buyer nothing can be tested.
    if (width <= 0 || height <= 0) return emptySet()

    val cellWidth = width.toFloat() / spec.columns
    val cellHeight = height.toFloat() / spec.rows

    val reservedColumns = (0 until spec.columns).filterTo(HashSet()) { column ->
        val centre = (column + 0.5f) * cellWidth
        centre < left || centre > width - right
    }

    val reservedRows = (0 until spec.rows).filterTo(HashSet()) { row ->
        val centre = (row + 0.5f) * cellHeight
        centre < top || centre > height - bottom
    }

    if (reservedColumns.isEmpty() && reservedRows.isEmpty()) return emptySet()

    // Insets are full-width and full-height bands, so a reserved row covers every column and a
    // reserved column covers every row.
    return buildSet {
        for (row in 0 until spec.rows) {
            for (column in 0 until spec.columns) {
                if (row in reservedRows || column in reservedColumns) add(Cell(column, row))
            }
        }
    }
}
