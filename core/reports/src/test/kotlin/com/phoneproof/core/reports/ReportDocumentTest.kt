package com.phoneproof.core.reports

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import org.junit.Test

class ReportDocumentTest {

    private fun pass(id: String = "hardware.storage", title: String = "Storage") = CheckResult(
        id = id,
        title = title,
        outcome = CheckOutcome.PASS,
        confidence = Confidence.HIGH,
        headline = "109 GB usable, consistent with a 128 GB phone.",
        measurements = listOf(Measurement("Usable total", "109", "GB")),
    )

    private fun fail(id: String = "screen.touch_coverage") = CheckResult(
        id = id,
        title = "Touch response",
        outcome = CheckOutcome.FAIL,
        confidence = Confidence.HIGH,
        headline = "A strip along the bottom never responded.",
        consequence = "You will fight this every time you type.",
        action = "Get the price down, or walk away.",
        measurements = listOf(Measurement("Coverage", "88.0", "%")),
        falsePositiveCauses = listOf("A screen protector can mask touches."),
    )

    private fun report(results: List<CheckResult>) = SavedReport(
        id = "1000-a",
        createdAtEpochMs = 1_000,
        deviceLabel = "realme RMX5110",
        androidLabel = "Android 16 (API 36)",
        results = results,
    )

    private fun flatten(pages: List<DocPage>) = pages.flatMap { it.lines }

    @Test
    fun `the report names the phone and the date`() {
        val lines = flatten(ReportDocument.build(report(listOf(pass())), "9 Aug 2026"))
        val text = lines.joinToString(" ") { it.text }

        assertThat(text).contains("realme RMX5110")
        assertThat(text).contains("9 Aug 2026")
        assertThat(text).contains("Phone inspection report")
    }

    @Test
    fun `provenance is always printed, even with shop branding`() {
        // A shop may put its name at the top. It does not get to remove where the numbers came from,
        // or a dishonest one could attach its name to readings it had edited.
        val branded = ReportDocument.build(
            report(listOf(pass())),
            "today",
            ShopBranding(name = "Krishna Mobiles", contact = "98765 43210"),
        )
        val text = flatten(branded).joinToString(" ") { it.text }

        assertThat(text).contains("Krishna Mobiles")
        assertThat(text).contains("Measured on the device by PhoneProof")
        assertThat(text).contains("Nothing was uploaded")
    }

    @Test
    fun `branding is absent when none is set`() {
        val lines = flatten(ReportDocument.build(report(listOf(pass())), "today"))

        assertThat(lines.map { it.style }).doesNotContain(LineStyle.SHOP_NAME)
    }

    @Test
    fun `false positive causes are printed, not just shown on screen`() {
        // A printed report becomes a document someone waves as proof. Every negative reading has to
        // carry its own caveats onto the paper.
        val text = flatten(ReportDocument.build(report(listOf(fail())), "today"))
            .joinToString(" ") { it.text }

        assertThat(text).contains("screen protector")
    }

    @Test
    fun `a failure carries its consequence and what to do`() {
        val text = flatten(ReportDocument.build(report(listOf(fail())), "today"))
            .joinToString(" ") { it.text }

        assertThat(text).contains("every time you type")
        assertThat(text).contains("What to do:")
    }

    @Test
    fun `verdicts are words so a photocopy still reads`() {
        val verdicts = flatten(ReportDocument.build(report(listOf(pass(), fail())), "today"))
            .filter { it.style == LineStyle.VERDICT }
            .map { it.text }

        assertThat(verdicts).containsExactly("PASS", "FAIL").inOrder()
    }

    @Test
    fun `every verdict line carries its outcome for colouring`() {
        flatten(ReportDocument.build(report(listOf(pass(), fail())), "today"))
            .filter { it.style == LineStyle.VERDICT }
            .forEach { assertThat(it.outcome).isNotNull() }
    }

    // ------------------------------------------------------------------ pagination

    @Test
    fun `a short report is a single page`() {
        assertThat(ReportDocument.build(report(listOf(pass())), "today")).hasSize(1)
    }

    @Test
    fun `no page ever exceeds the line budget`() {
        // The failure this guards against is text running off the bottom of a printed page, which is
        // invisible in code and obvious to whoever is holding the paper.
        val many = (1..14).map { pass(id = "hardware.item$it", title = "Check $it") }
        val pages = ReportDocument.build(report(many), "today")

        assertThat(pages.size).isGreaterThan(1)
        pages.forEach { assertThat(it.lines.size).isAtMost(ReportDocument.LINES_PER_PAGE) }
    }

    @Test
    fun `a check heading is never orphaned at the foot of a page`() {
        // The one break that makes a printed report actively misleading: a title on one page and a
        // verdict on the next, which a reader could pair with whatever followed.
        val many = (1..20).map { fail(id = "screen.item$it") }
        val pages = ReportDocument.build(report(many), "today")

        pages.forEach { page ->
            val lastTwo = page.lines.takeLast(2).map { it.style }
            assertThat(lastTwo).doesNotContain(LineStyle.SECTION)
        }
    }

    @Test
    fun `pages do not end on blank lines`() {
        val many = (1..12).map { fail(id = "screen.item$it") }

        ReportDocument.build(report(many), "today").forEach { page ->
            assertThat(page.lines.last().style).isNotEqualTo(LineStyle.SPACER)
        }
    }

    @Test
    fun `an empty line list produces no pages rather than one blank page`() {
        assertThat(ReportDocument.paginate(emptyList())).isEmpty()
    }

    @Test
    fun `a report with no results still prints its heading`() {
        val pages = ReportDocument.build(report(emptyList()), "today")

        assertThat(pages).hasSize(1)
        assertThat(pages.first().lines.map { it.text }).contains("Phone inspection report")
    }

    // ---------------------------------------------------------------------- wrapping

    @Test
    fun `short text is left alone`() {
        assertThat(ReportDocument.wrap("Hello there", 40)).containsExactly("Hello there")
    }

    @Test
    fun `long text wraps on word boundaries`() {
        val wrapped = ReportDocument.wrap("one two three four five six seven", 12)

        wrapped.forEach { assertThat(it.length).isAtMost(12) }
        assertThat(wrapped.joinToString(" ")).isEqualTo("one two three four five six seven")
    }

    @Test
    fun `a word longer than the budget is kept whole`() {
        // Model names and URLs are exactly the strings a reader needs intact, so a long word gets
        // its own line rather than being split.
        val wrapped = ReportDocument.wrap("see https://example.com/a/very/long/path now", 12)

        assertThat(wrapped).contains("https://example.com/a/very/long/path")
    }

    @Test
    fun `blank text produces nothing`() {
        assertThat(ReportDocument.wrap("   ", 20)).isEmpty()
    }

    @Test
    fun `a zero budget is rejected rather than looping forever`() {
        assertThat(runCatching { ReportDocument.wrap("text", 0) }.isFailure).isTrue()
    }
}
