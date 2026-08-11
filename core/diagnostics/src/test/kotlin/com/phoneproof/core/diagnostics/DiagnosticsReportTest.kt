package com.phoneproof.core.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticsReportTest {

    private val environment = DiagnosticsEnvironment(
        appVersion = "0.1.0",
        versionCode = 1,
        manufacturer = "Xiaomi",
        model = "Redmi Note 13",
        androidRelease = "14",
        sdkInt = 34,
        screen = "1080x2400 @ 120Hz",
        locale = "en-IN",
    )

    private fun report(
        entries: List<DiagEntry>,
        dropped: Int = 0,
    ) = DiagnosticsReport.format(environment, entries, dropped) { "T$it" }

    @Test
    fun `the header carries the facts needed to act on a report`() {
        val text = report(emptyList())
        assertThat(text).contains("0.1.0")
        assertThat(text).contains("Xiaomi Redmi Note 13")
        assertThat(text).contains("API 34")
        assertThat(text).contains("1080x2400 @ 120Hz")
        assertThat(text).contains("en-IN")
    }

    @Test
    fun `an empty log says so instead of looking broken`() {
        assertThat(report(emptyList())).contains("No events recorded.")
    }

    @Test
    fun `entries render with timestamp level tag and message`() {
        val text = report(listOf(DiagEntry(7L, DiagLevel.WARN, "touch", "slow frame")))
        assertThat(text).contains("T7")
        assertThat(text).contains("WARN")
        assertThat(text).contains("touch")
        assertThat(text).contains("slow frame")
    }

    @Test
    fun `stack traces are indented under their entry`() {
        val text = report(
            listOf(DiagEntry(1L, DiagLevel.CRASH, "app", "died", "java.lang.Boom\n    at Foo.bar")),
        )
        assertThat(text).contains("java.lang.Boom")
        assertThat(text).contains("    at Foo.bar")
    }

    @Test
    fun `a truncated log announces what was dropped`() {
        // Silence here would send the reader hunting for events that were never included.
        val text = report(listOf(DiagEntry(1L, DiagLevel.INFO, "t", "m")), dropped = 12)
        assertThat(text).contains("dropped")
        assertThat(text).contains("12")
    }

    @Test
    fun `a complete log does not mention dropping anything`() {
        assertThat(report(listOf(DiagEntry(1L, DiagLevel.INFO, "t", "m")))).doesNotContain("dropped")
    }

    @Test
    fun `optional environment fields are omitted rather than printed empty`() {
        val minimal = DiagnosticsEnvironment(
            appVersion = "0.1.0", versionCode = 1, manufacturer = "Nothing",
            model = "Phone 2a", androidRelease = "15", sdkInt = 35,
        )
        val text = DiagnosticsReport.format(minimal, emptyList())
        assertThat(text).doesNotContain("screen")
        assertThat(text).doesNotContain("locale")
    }

    @Test
    fun `report ends with exactly one trailing newline so it pastes cleanly`() {
        val text = report(listOf(DiagEntry(1L, DiagLevel.INFO, "t", "m")))
        assertThat(text).endsWith("\n")
        assertThat(text).doesNotContain("\n\n\n")
    }
}
