package com.phoneproof.checks.touch

/** The cell grid the screen is divided into for the coverage test. */
data class GridSpec(
    val columns: Int,
    val rows: Int,
) {
    init {
        require(columns > 0) { "columns must be positive, was $columns" }
        require(rows > 0) { "rows must be positive, was $rows" }
    }

    val cellCount: Int get() = columns * rows

    companion object {
        /**
         * Portrait default. Chosen so a cell is roughly a fingertip across on a typical
         * 6.5 inch phone: fine enough to localise a dead strip, coarse enough that a normal
         * drag can realistically cover every cell in about a minute.
         */
        val Default = GridSpec(columns = 16, rows = 32)
    }
}

data class Cell(val column: Int, val row: Int)

/** Where on the screen a dead zone sits, in words a buyer can repeat to a seller. */
enum class ScreenRegion(val label: String) {
    TOP_LEFT("top-left"),
    TOP("top edge"),
    TOP_RIGHT("top-right"),
    LEFT("left edge"),
    CENTRE("centre"),
    RIGHT("right edge"),
    BOTTOM_LEFT("bottom-left"),
    BOTTOM("bottom edge"),
    BOTTOM_RIGHT("bottom-right"),
}

/**
 * A contiguous group of cells that never registered a touch.
 *
 * Clustering matters more than counting. One stray uncovered cell almost always means the
 * tester's finger skipped a spot. A contiguous block means the digitiser is dead there.
 * Treating those two cases identically is how competing apps generate false failures.
 */
data class DeadZone(val cells: Set<Cell>) {
    init {
        require(cells.isNotEmpty()) { "a DeadZone must contain at least one cell" }
    }

    val size: Int get() = cells.size

    fun region(spec: GridSpec): ScreenRegion {
        val meanColumn = cells.sumOf { it.column }.toDouble() / size
        val meanRow = cells.sumOf { it.row }.toDouble() / size
        val horizontal = band(meanColumn, spec.columns)
        val vertical = band(meanRow, spec.rows)
        return when (vertical to horizontal) {
            0 to 0 -> ScreenRegion.TOP_LEFT
            0 to 1 -> ScreenRegion.TOP
            0 to 2 -> ScreenRegion.TOP_RIGHT
            1 to 0 -> ScreenRegion.LEFT
            1 to 1 -> ScreenRegion.CENTRE
            1 to 2 -> ScreenRegion.RIGHT
            2 to 0 -> ScreenRegion.BOTTOM_LEFT
            2 to 1 -> ScreenRegion.BOTTOM
            else -> ScreenRegion.BOTTOM_RIGHT
        }
    }

    private fun band(mean: Double, extent: Int): Int {
        val third = extent / 3.0
        return when {
            mean < third -> 0
            mean < third * 2 -> 1
            else -> 2
        }
    }
}

/** An immutable snapshot of which cells have been touched. */
data class TouchCoverage(
    val spec: GridSpec,
    val touchedCells: Set<Cell>,
    /**
     * Cells sitting under a strip Android reserves for its own edge gestures — the pull-down for
     * the notification shade at the top, the home swipe at the bottom, the back swipe at the sides.
     *
     * These exist because of a false alarm on real hardware. A flawless screen reported CAUTION
     * for three cells along the top edge: the tester swiped over them, the system swallowed the
     * gesture to open the shade, and the app never saw the touch. Blaming the phone for the
     * platform's behaviour is the worst thing a trust tool can do, because the buyer learns to
     * discount every result the app gives them afterwards.
     *
     * Reserved cells are therefore excluded from the verdict but still *shown*, and the count of
     * ones left uncovered is disclosed. Empty by default so every existing caller and test keeps
     * its previous meaning.
     */
    val reservedCells: Set<Cell> = emptySet(),
) {
    val cellCount: Int get() = spec.cellCount
    val touchedCount: Int get() = touchedCells.size

    val untouchedCells: Set<Cell>
        get() = buildSet {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) {
                    val cell = Cell(column, row)
                    if (cell !in touchedCells) add(cell)
                }
            }
        }

    /** Reserved cells the tester never managed to cover. Disclosed, never counted against the phone. */
    val untestedReservedCells: Set<Cell>
        get() = untouchedCells intersect reservedCells

    /** Cells the tester can fairly be expected to reach. */
    val testableCellCount: Int get() = cellCount - reservedCells.size

    /**
     * Coverage over the cells that can actually be reached, which is what the judging threshold
     * has to be measured against. Using the raw ratio would make a 100% target unreachable on a
     * phone whose gesture strips are wide, leaving the buyer stuck short of a verdict forever.
     */
    val testableCoverageRatio: Float
        get() {
            if (testableCellCount <= 0) return 0f
            return (touchedCells - reservedCells).size.toFloat() / testableCellCount.toFloat()
        }

    /** 0f to 1f. */
    val coverageRatio: Float
        get() = touchedCount.toFloat() / cellCount.toFloat()

    /**
     * Untouched cells grouped into connected components using 4-way adjacency.
     *
     * Diagonal adjacency is deliberately excluded: two cells touching only at a corner are
     * far more likely to be two separate finger skips than one physical defect.
     */
    /**
     * Every gap, including ones the platform caused.
     *
     * The verdict deliberately does **not** use this — see [testableDeadZones]. It is kept as the
     * raw view because the difference between the two is exactly the bug that was fixed here, and
     * the tests assert on both to hold that distinction in place. Reaching for this one when
     * judging a phone is how a flawless screen gets accused.
     */
    fun deadZones(): List<DeadZone> = connectedZones(untouchedCells)

    /**
     * Dead zones over the cells that could actually be tested.
     *
     * Reserved cells are removed *before* grouping rather than after, which matters: a patch
     * straddling the top edge would otherwise be measured including its unreachable part and be
     * promoted to a defect on the strength of cells the app never had a chance to read. Removing
     * them first also lets a straddling patch split into two smaller pieces, each judged on the
     * evidence that genuinely exists.
     */
    fun testableDeadZones(): List<DeadZone> = connectedZones(untouchedCells - reservedCells)

    private fun connectedZones(cells: Set<Cell>): List<DeadZone> {
        val remaining = cells.toHashSet()
        val zones = mutableListOf<DeadZone>()

        while (remaining.isNotEmpty()) {
            val seed = remaining.first()
            val component = HashSet<Cell>()
            val queue = ArrayDeque<Cell>()
            queue.add(seed)
            remaining.remove(seed)

            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                component.add(cell)
                for (neighbour in orthogonalNeighbours(cell)) {
                    if (remaining.remove(neighbour)) queue.add(neighbour)
                }
            }
            zones.add(DeadZone(component))
        }

        return zones.sortedByDescending { it.size }
    }

    private fun orthogonalNeighbours(cell: Cell): List<Cell> = listOf(
        Cell(cell.column - 1, cell.row),
        Cell(cell.column + 1, cell.row),
        Cell(cell.column, cell.row - 1),
        Cell(cell.column, cell.row + 1),
    )
}

/**
 * Accumulates touches during the test. Mutable and not thread-safe by design: it is driven
 * from a single Compose pointer-input stream on the main thread.
 */
class TouchCoverageTracker(val spec: GridSpec = GridSpec.Default) {

    private val touched = HashSet<Cell>()

    val touchedCount: Int get() = touched.size

    /** @return true when this touch covered a cell that had not been covered before. */
    fun mark(cell: Cell): Boolean {
        if (cell.column !in 0 until spec.columns) return false
        if (cell.row !in 0 until spec.rows) return false
        return touched.add(cell)
    }

    /**
     * Marks the cell containing a normalised point.
     *
     * Values are expected in 0f..1f. Anything outside is ignored rather than clamped, because
     * clamping would silently credit coverage to an edge cell the finger never actually reached.
     *
     * @return the cell only when this point covered something **new**, and null when the point
     *   was out of range or the cell was already covered. Callers use that to avoid rebuilding
     *   UI state on every sample of a drag — a fast sweep reports the same cell many times.
     */
    fun markNormalised(x: Float, y: Float): Cell? {
        if (!x.isFinite() || !y.isFinite()) return null
        if (x < 0f || x > 1f || y < 0f || y > 1f) return null

        // coerceAtMost keeps an exact 1.0 from indexing one past the last cell.
        val column = (x * spec.columns).toInt().coerceAtMost(spec.columns - 1)
        val row = (y * spec.rows).toInt().coerceAtMost(spec.rows - 1)
        val cell = Cell(column, row)
        return if (mark(cell)) cell else null
    }

    fun isTouched(cell: Cell): Boolean = cell in touched

    fun snapshot(): TouchCoverage = TouchCoverage(spec, touched.toSet())

    fun reset() = touched.clear()
}
