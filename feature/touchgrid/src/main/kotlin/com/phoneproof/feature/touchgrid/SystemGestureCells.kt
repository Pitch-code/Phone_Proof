package com.phoneproof.feature.touchgrid

import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.GridSpec

/**
 * Maps the edge strips Android uses for its own gestures onto grid cells.
 *
 * These cells are **tested like any other**. The name of this file used to be `ReservedCells`, and
 * "reserved" was accurate when the app measured the strips and then excused them; it is not accurate
 * now that the strips are swept, the app asks the system to keep out of them, and the only thing this
 * set decides is the *wording* when a gap is left inside one.
 *
 * The strips are read from `WindowInsets.systemGestures` at runtime rather than assumed, because they
 * differ by phone and by navigation style: a gesture-navigation phone claims a tall band at the bottom
 * and thin bands at both sides for the back swipe, while a three-button phone claims almost nothing at
 * the sides. Hardcoding a guess would either excuse a real dead zone on one phone or keep blaming
 * another for the platform.
 *
 * Deliberately a plain function over integers rather than something that reads insets itself. The
 * screenshot tests run under Robolectric, which reports no system bars at all, so this arithmetic
 * would be permanently untested if it were welded to the Compose inset APIs.
 *
 * A cell counts as gesture territory when **its centre** falls inside a strip, meaning more than half
 * of it is at risk. Treating any overlap as gesture territory was the obvious alternative and is
 * worse: a strip one pixel into a row would mark that entire row across the screen as unattributable,
 * and with it the ability to report a genuine defect near the edge.
 *
 * @param width canvas width in pixels.
 * @param height canvas height in pixels.
 * @param left inset from the left edge in pixels, and so on for [top], [right] and [bottom].
 * @return every cell whose centre lies in a gesture strip, or an empty set when there are none —
 *   which is the normal case under Robolectric and on a phone with no gesture insets.
 */
internal fun systemGestureCells(
    spec: GridSpec,
    width: Int,
    height: Int,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): Set<Cell> {
    // A zero-sized canvas happens on the first frame before layout. Reporting every cell as
    // gesture territory for that frame would briefly make the whole grid unattributable.
    if (width <= 0 || height <= 0) return emptySet()

    val cellWidth = width.toFloat() / spec.columns
    val cellHeight = height.toFloat() / spec.rows

    val gestureColumns = (0 until spec.columns).filterTo(HashSet()) { column ->
        val centre = (column + 0.5f) * cellWidth
        centre < left || centre > width - right
    }

    val gestureRows = (0 until spec.rows).filterTo(HashSet()) { row ->
        val centre = (row + 0.5f) * cellHeight
        centre < top || centre > height - bottom
    }

    if (gestureColumns.isEmpty() && gestureRows.isEmpty()) return emptySet()

    // Insets are full-width and full-height bands, so a gesture row covers every column and a
    // gesture column covers every row.
    return buildSet {
        for (row in 0 until spec.rows) {
            for (column in 0 until spec.columns) {
                if (row in gestureRows || column in gestureColumns) add(Cell(column, row))
            }
        }
    }
}
