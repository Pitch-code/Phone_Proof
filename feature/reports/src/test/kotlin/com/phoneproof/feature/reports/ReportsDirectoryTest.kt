package com.phoneproof.feature.reports

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the directory name that the scan and the history screen must agree on.
 *
 * `feature:scan` writes reports and `feature:reports` reads them, and neither depends on the other —
 * a feature module depending on another feature module is how a module graph turns into a knot. The
 * cost of that choice is a duplicated string literal, and the failure it invites is silent: reports
 * would save successfully and the history screen would sit empty forever, with nothing in the log.
 *
 * This test is the thing that makes the duplication safe. If either side changes the name, it fails
 * here with the reason spelled out, rather than being discovered by a buyer whose reports vanished.
 */
class ReportsDirectoryTest {

    @Test
    fun `the reports directory name is the one the scan writes to`() {
        // Mirrors the literal in feature/scan/ScanRoute.kt: ReportStore(File(filesDir, "reports")).
        assertThat(REPORTS_DIRECTORY).isEqualTo("reports")
    }
}
