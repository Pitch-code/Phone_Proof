package com.phoneproof.core.reports

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import org.junit.Test

class ReportComparisonTest {

    private fun result(
        id: String,
        outcome: CheckOutcome,
        title: String = "Check $id",
    ) = when (outcome) {
        CheckOutcome.PASS, CheckOutcome.UNKNOWN -> CheckResult(
            id = id,
            title = title,
            outcome = outcome,
            confidence = Confidence.HIGH,
            headline = "Fine on $id",
        )
        else -> CheckResult(
            id = id,
            title = title,
            outcome = outcome,
            confidence = Confidence.HIGH,
            headline = "Problem on $id",
            consequence = "It matters.",
            action = "Do something.",
            falsePositiveCauses = listOf("Could be wrong."),
        )
    }

    private fun report(id: String, device: String, results: List<CheckResult>) = SavedReport(
        id = id,
        createdAtEpochMs = 1_000,
        deviceLabel = device,
        androidLabel = "Android 16 (API 36)",
        results = results,
    )

    @Test
    fun `checks are matched by id, not by position`() {
        // Two phones may have been scanned by different app versions. Matching by index would
        // compare a battery reading against a storage reading and report nonsense.
        val left = report("l", "Phone A", listOf(result("a.one", CheckOutcome.PASS), result("a.two", CheckOutcome.FAIL)))
        val right = report("r", "Phone B", listOf(result("a.two", CheckOutcome.PASS), result("a.one", CheckOutcome.PASS)))

        val rows = compareReports(left, right).rows

        val two = rows.first { it.checkId == "a.two" }
        assertThat(two.left).isEqualTo(CheckOutcome.FAIL)
        assertThat(two.right).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `the left order is preserved so the opened report stays recognisable`() {
        val left = report("l", "A", listOf(result("z.last", CheckOutcome.PASS), result("a.first", CheckOutcome.PASS)))
        val right = report("r", "B", listOf(result("a.first", CheckOutcome.PASS)))

        assertThat(compareReports(left, right).rows.map { it.checkId })
            .containsExactly("z.last", "a.first").inOrder()
    }

    @Test
    fun `a check only one phone was tested for still appears`() {
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS)))
        val right = report("r", "B", listOf(result("b.only", CheckOutcome.FAIL)))

        assertThat(compareReports(left, right).rows.map { it.checkId })
            .containsExactly("a.one", "b.only").inOrder()
    }

    @Test
    fun `a missing check is not a win for the other phone`() {
        // Treating absence as a pass would make a half-finished scan look like the better phone,
        // which is exactly backwards.
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS)))
        val right = report("r", "B", emptyList())

        val row = compareReports(left, right).rows.single()
        assertThat(row.better).isNull()
    }

    @Test
    fun `a pass beats a failure`() {
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS)))
        val right = report("r", "B", listOf(result("a.one", CheckOutcome.FAIL)))

        assertThat(compareReports(left, right).rows.single().better)
            .isEqualTo(ComparisonSide.LEFT)
    }

    @Test
    fun `could not tell ranks above a caution`() {
        // UNKNOWN is an absence of evidence; CAUTION is evidence of a problem. Ranking them the
        // other way would make an untested phone look worse than one with a known fault.
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.UNKNOWN)))
        val right = report("r", "B", listOf(result("a.one", CheckOutcome.CAUTION)))

        assertThat(compareReports(left, right).rows.single().better)
            .isEqualTo(ComparisonSide.LEFT)
    }

    @Test
    fun `identical outcomes have no winner`() {
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS)))
        val right = report("r", "B", listOf(result("a.one", CheckOutcome.PASS)))

        val row = compareReports(left, right).rows.single()
        assertThat(row.better).isNull()
        assertThat(row.differs).isFalse()
    }

    @Test
    fun `differing rows are listed separately so the difference is easy to find`() {
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS), result("a.two", CheckOutcome.PASS)))
        val right = report("r", "B", listOf(result("a.one", CheckOutcome.PASS), result("a.two", CheckOutcome.FAIL)))

        assertThat(compareReports(left, right).differingRows.map { it.checkId })
            .containsExactly("a.two")
    }

    @Test
    fun `one phone better on every difference is called clearly better`() {
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS), result("a.two", CheckOutcome.PASS)))
        val right = report("r", "B", listOf(result("a.one", CheckOutcome.PASS), result("a.two", CheckOutcome.FAIL)))

        assertThat(compareReports(left, right).clearlyBetter).isEqualTo(ComparisonSide.LEFT)
    }

    @Test
    fun `a split decision refuses to recommend`() {
        // Three wins and two losses is a judgement about which faults the buyer cares about. The app
        // does not know that, and guessing would be confident nonsense.
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS), result("a.two", CheckOutcome.FAIL)))
        val right = report("r", "B", listOf(result("a.one", CheckOutcome.FAIL), result("a.two", CheckOutcome.PASS)))

        assertThat(compareReports(left, right).clearlyBetter).isNull()
    }

    @Test
    fun `two identical phones produce no recommendation`() {
        val results = listOf(result("a.one", CheckOutcome.PASS))
        val comparison = compareReports(report("l", "A", results), report("r", "B", results))

        assertThat(comparison.clearlyBetter).isNull()
        assertThat(comparison.differingRows).isEmpty()
    }

    @Test
    fun `the headline from each side is carried so a difference reads without opening both`() {
        val left = report("l", "A", listOf(result("a.one", CheckOutcome.PASS)))
        val right = report("r", "B", listOf(result("a.one", CheckOutcome.FAIL)))

        val row = compareReports(left, right).rows.single()
        assertThat(row.leftDetail).contains("Fine on")
        assertThat(row.rightDetail).contains("Problem on")
    }

    @Test
    fun `comparing a report with itself finds no differences`() {
        val r = report("l", "A", listOf(result("a.one", CheckOutcome.CAUTION)))

        assertThat(compareReports(r, r).differingRows).isEmpty()
    }
}
