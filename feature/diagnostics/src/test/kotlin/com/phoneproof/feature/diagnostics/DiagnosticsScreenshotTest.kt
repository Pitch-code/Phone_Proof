package com.phoneproof.feature.diagnostics

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.diagnostics.DiagEntry
import com.phoneproof.core.diagnostics.DiagLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class DiagnosticsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private val header =
        "Xiaomi Redmi Note 13  ·  Android 14 (API 34)  ·  app 0.1.0"

    private fun render(
        name: String,
        entries: List<DiagEntry>,
        dropped: Int = 0,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                DiagnosticsScreen(
                    entries = entries,
                    droppedCount = dropped,
                    header = header,
                    onCopy = {},
                    onShare = {},
                    onClear = {},
                    // Fixed formatter so the render is byte-identical on every run; a real clock
                    // would make the screenshot drift check fail every single time.
                    formatTimestamp = { "12:04:${(it % 60).toString().padStart(2, '0')}.000" },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun empty_log_explains_itself() {
        render("diagnostics-1-empty", emptyList())
    }

    @Test
    fun log_with_a_crash_and_a_dropped_count() {
        render(
            "diagnostics-2-with-crash",
            listOf(
                DiagEntry(1L, DiagLevel.INFO, "app", "start 0.1.0 (1) on Xiaomi Redmi Note 13"),
                DiagEntry(9L, DiagLevel.INFO, "DeviceAdminInspector", "found 1 device admin(s)"),
                DiagEntry(17L, DiagLevel.WARN, "touch", "coverage stalled at 62%"),
                DiagEntry(
                    timestampMillis = 23L,
                    level = DiagLevel.ERROR,
                    tag = "EmiLockRoute",
                    message = "lock check failed",
                    stackTrace = "java.lang.SecurityException: not permitted\n" +
                        "    at android.app.admin.DevicePolicyManager.getActiveAdmins",
                ),
                DiagEntry(
                    timestampMillis = 31L,
                    level = DiagLevel.CRASH,
                    tag = "uncaught",
                    message = "uncaught on thread 'main'",
                    stackTrace = "java.lang.IllegalStateException: boom\n" +
                        "    at com.phoneproof.feature.touchgrid.TouchGridViewModel.onTouch",
                ),
            ),
            dropped = 14,
        )
    }
}
