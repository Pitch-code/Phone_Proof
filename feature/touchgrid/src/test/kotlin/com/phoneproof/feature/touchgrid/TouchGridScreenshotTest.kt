package com.phoneproof.feature.touchgrid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.touch.Cell
import com.phoneproof.checks.touch.GridSpec
import com.phoneproof.checks.touch.TouchCoverage
import com.phoneproof.checks.touch.TouchCoverageEvaluator
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the touch grid to PNG on the JVM.
 *
 * This sandbox has no `/dev/kvm`, so no emulator can run. These renders are therefore the only
 * way a screen gets reviewed, and they exist specifically so nobody has to install an APK to find
 * out that a layout is wrong. The output lands in `screenshots/` at the repo root and is
 * committed, so a reviewer can look at the UI directly on GitHub.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class TouchGridScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private val spec = GridSpec.Default

    private fun allCells(): Set<Cell> = buildSet {
        for (row in 0 until spec.rows) {
            for (column in 0 until spec.columns) add(Cell(column, row))
        }
    }

    /** A solid rectangle of cells, used to build both coverage and deliberate dead zones. */
    private fun block(x: Int, y: Int, width: Int, height: Int): Set<Cell> = buildSet {
        for (row in y until (y + height)) {
            for (column in x until (x + width)) add(Cell(column, row))
        }
    }

    private fun render(name: String, state: TouchGridUiState) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                TouchGridScreen(
                    state = state,
                    onTouch = { _, _ -> },
                    onFinish = {},
                    onRetest = {},
                    // Ignored on purpose. Robolectric reports no system bars, so the live inset
                    // reading always yields an empty set here; the reserved states below are built
                    // by hand instead, which is why reservedCells lives in the state.
                    onReservedCellsChanged = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun ready_before_any_touch() {
        render("touchgrid-1-ready", TouchGridUiState(spec = spec))
    }

    @Test
    fun partway_through_the_sweep() {
        // Top two-thirds covered, so the empty cells read clearly as "not done yet".
        val covered = block(0, 0, spec.columns, (spec.rows * 2) / 3)
        render(
            "touchgrid-2-in-progress",
            TouchGridUiState(
                spec = spec,
                touchedCells = covered,
                phase = TouchTestPhase.IN_PROGRESS,
            ),
        )
    }

    @Test
    fun finished_with_a_dead_zone_in_the_bottom_right() {
        // The state a buyer actually needs to see: a real contiguous defect.
        val deadZone = block(spec.columns - 4, spec.rows - 5, 4, 5)
        val covered = allCells() - deadZone
        val coverage = TouchCoverage(spec, covered)
        render(
            "touchgrid-3-dead-zone",
            TouchGridUiState(
                spec = spec,
                touchedCells = covered,
                phase = TouchTestPhase.FINISHED,
                result = TouchCoverageEvaluator.evaluate(coverage),
                highlightedCells = coverage.untouchedCells,
            ),
        )
    }

    @Test
    fun finished_with_a_clean_screen() {
        val covered = allCells()
        val coverage = TouchCoverage(spec, covered)
        render(
            "touchgrid-4-pass",
            TouchGridUiState(
                spec = spec,
                touchedCells = covered,
                phase = TouchTestPhase.FINISHED,
                result = TouchCoverageEvaluator.evaluate(coverage),
            ),
        )
    }

    /**
     * Roughly what a gesture-navigation phone reserves: a band top and bottom for the shade and the
     * home swipe, and a column each side for the back swipe.
     */
    private fun gestureStrips(): Set<Cell> =
        block(0, 0, spec.columns, 2) +
            block(0, spec.rows - 2, spec.columns, 2) +
            block(0, 0, 1, spec.rows) +
            block(spec.columns - 1, 0, 1, spec.rows)

    @Test
    fun sweeping_with_reserved_edges_shown() {
        val reserved = gestureStrips()
        val covered = block(0, 0, spec.columns, (spec.rows * 2) / 3) - reserved
        render(
            "touchgrid-6-reserved-edges",
            TouchGridUiState(
                spec = spec,
                touchedCells = covered,
                phase = TouchTestPhase.IN_PROGRESS,
                reservedCells = reserved,
            ),
        )
    }

    @Test
    fun three_cells_missed_inside_a_reserved_strip_is_a_pass() {
        // The realme RMX5110 false alarm, as a picture. Three cells along the top edge stayed
        // uncovered because the system took those swipes to open the notification shade, and the
        // app reported CAUTION on a screen with nothing wrong with it.
        val reserved = gestureStrips()
        val missed = setOf(Cell(4, 0), Cell(5, 0), Cell(6, 0))
        val covered = allCells() - missed
        val coverage = TouchCoverage(spec, covered, reserved)
        val result = TouchCoverageEvaluator.evaluate(coverage)

        // Asserted as well as rendered. A screenshot shows the wording, but only this catches a
        // regression that quietly turns the verdict back into an accusation.
        assertEquals(CheckOutcome.PASS, result.outcome)
        assertEquals(Confidence.MEDIUM, result.confidence)

        render(
            "touchgrid-7-reserved-pass",
            TouchGridUiState(
                spec = spec,
                touchedCells = covered,
                phase = TouchTestPhase.FINISHED,
                result = result,
                highlightedCells = coverage.untouchedCells - reserved,
                reservedCells = reserved,
            ),
        )
    }

    @Test
    fun finished_with_scattered_misses_is_a_caution_not_a_failure() {
        val missed = setOf(Cell(2, 5), Cell(11, 14), Cell(6, 26))
        val covered = allCells() - missed
        val coverage = TouchCoverage(spec, covered)
        render(
            "touchgrid-5-caution",
            TouchGridUiState(
                spec = spec,
                touchedCells = covered,
                phase = TouchTestPhase.FINISHED,
                result = TouchCoverageEvaluator.evaluate(coverage),
                highlightedCells = coverage.untouchedCells,
            ),
        )
    }
}
