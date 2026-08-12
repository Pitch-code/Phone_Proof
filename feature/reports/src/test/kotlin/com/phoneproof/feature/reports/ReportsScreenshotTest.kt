package com.phoneproof.feature.reports

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.reports.SavedReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ReportsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    // Fixed strings, never the real formatter. formatReportDate follows the machine's locale and
    // time zone, which would make these renders differ between this sandbox and CI.
    private val dateLabel = "9 Aug 2026, 1:26 am"

    private fun pass() = CheckResult(
        id = "security.device_admin_lock",
        title = "Remote lock control",
        outcome = CheckOutcome.PASS,
        confidence = Confidence.HIGH,
        headline = "Nothing has remote control over this phone.",
        measurements = listOf(Measurement("Admin apps found", "0")),
    )

    private fun caution() = CheckResult(
        id = "screen.display",
        title = "Display",
        outcome = CheckOutcome.CAUTION,
        confidence = Confidence.HIGH,
        headline = "The screen supports 144 Hz but is running at 60 Hz.",
        consequence = "You are not getting the smoothness the phone is capable of.",
        action = "Check Settings, Display, Refresh rate — then judge the screen with it on.",
        measurements = listOf(Measurement("Running at", "60", "Hz")),
        falsePositiveCauses = listOf("Battery saver caps the refresh rate automatically."),
    )

    private fun report(id: String, at: Long, results: List<CheckResult>) = SavedReport(
        id = id,
        createdAtEpochMs = at,
        deviceLabel = "realme RMX5110",
        androidLabel = "Android 16 (API 36)",
        results = results,
    )

    private fun renderList(name: String, state: ReportsUiState) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ReportsScreen(
                    state = state,
                    formatDate = { dateLabel },
                    onOpenReport = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun empty_before_any_scan() {
        renderList(
            "reports-1-empty",
            ReportsUiState(loading = false, retained = 2),
        )
    }

    @Test
    fun two_saved_reports_with_the_limit_stated() {
        renderList(
            "reports-2-list",
            ReportsUiState(
                loading = false,
                reports = listOf(
                    report("2-b", 2_000, listOf(pass(), caution())),
                    report("1-a", 1_000, listOf(pass())),
                ),
                retained = 2,
            ),
        )
    }

    @Test
    fun a_damaged_file_is_admitted_not_hidden() {
        renderList(
            "reports-3-damaged",
            ReportsUiState(
                loading = false,
                reports = listOf(report("1-a", 1_000, listOf(pass()))),
                unreadableCount = 1,
                retained = 2,
            ),
        )
    }

    @Test
    fun a_report_read_back() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ReportDetailScreen(
                    report = report("2-b", 2_000, listOf(pass(), caution())),
                    dateLabel = dateLabel,
                    onShare = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/reports-4-detail.png")
    }

    @Test
    fun a_report_that_was_pruned_while_open() {
        // Genuinely reachable: a later scan can prune this report while the screen sits on the back
        // stack, so it must say so rather than render a blank page.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ReportDetailScreen(
                    report = null,
                    dateLabel = "",
                    onShare = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/reports-5-missing.png")
    }
}
