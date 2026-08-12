package com.phoneproof.core.reports

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import org.junit.Test

class ReportTextTest {

    private fun result(
        outcome: CheckOutcome,
        title: String = "Touch response",
    ) = when (outcome) {
        CheckOutcome.PASS, CheckOutcome.UNKNOWN -> CheckResult(
            id = "screen.touch_coverage",
            title = title,
            outcome = outcome,
            confidence = Confidence.HIGH,
            headline = "Every part of the screen responded.",
            measurements = listOf(Measurement("Coverage", "99.4", "%")),
        )
        else -> CheckResult(
            id = "screen.touch_coverage",
            title = title,
            outcome = outcome,
            confidence = Confidence.HIGH,
            headline = "A strip along the bottom never responded.",
            consequence = "You will fight this every time you type.",
            action = "Get the price down, or walk away.",
            measurements = listOf(Measurement("Coverage", "88.0", "%")),
            falsePositiveCauses = listOf("A screen protector can mask touches."),
        )
    }

    private fun report(vararg results: CheckResult) = SavedReport(
        id = "1000-a",
        createdAtEpochMs = 1_000,
        deviceLabel = "realme RMX5110",
        androidLabel = "Android 16 (API 36)",
        results = results.toList(),
    )

    @Test
    fun `the text names the phone, the date and where it came from`() {
        val text = report(result(CheckOutcome.PASS)).asPlainText("9 August 2026, 1:26 am")

        assertThat(text).contains("PhoneProof report")
        assertThat(text).contains("realme RMX5110 · Android 16 (API 36)")
        assertThat(text).contains("9 August 2026, 1:26 am")
        assertThat(text).contains("Nothing was uploaded.")
    }

    @Test
    fun `a problem carries its consequence and what to do about it`() {
        val text = report(result(CheckOutcome.FAIL)).asPlainText("today")

        assertThat(text).contains("[FAIL] Touch response")
        assertThat(text).contains("A strip along the bottom never responded.")
        assertThat(text).contains("You will fight this every time you type.")
        assertThat(text).contains("Get the price down, or walk away.")
        assertThat(text).contains("Coverage: 88.0 %")
    }

    @Test
    fun `outcomes are words, not symbols, so a forwarded quote still reads`() {
        val text = report(
            result(CheckOutcome.PASS, "A"),
            result(CheckOutcome.CAUTION, "B"),
            result(CheckOutcome.FAIL, "C"),
            result(CheckOutcome.UNKNOWN, "D"),
        ).asPlainText("today")

        assertThat(text).contains("[PASS] A")
        assertThat(text).contains("[CHECK] B")
        assertThat(text).contains("[FAIL] C")
        assertThat(text).contains("[UNKNOWN] D")
    }

    @Test
    fun `the summary counts problems, unreadables and passes separately`() {
        val subject = report(
            result(CheckOutcome.PASS, "A"),
            result(CheckOutcome.CAUTION, "B"),
            result(CheckOutcome.FAIL, "C"),
            result(CheckOutcome.UNKNOWN, "D"),
        )

        assertThat(subject.summaryLine()).isEqualTo("2 to check, 1 could not be read, 1 fine")
    }

    @Test
    fun `a clean report says only what is true`() {
        val subject = report(result(CheckOutcome.PASS))

        assertThat(subject.summaryLine()).isEqualTo("1 fine")
    }

    @Test
    fun `an empty report does not claim everything is fine`() {
        // "0 fine" would read as a clean bill of health for a scan that never ran.
        assertThat(report().summaryLine()).isEqualTo("No checks were run.")
    }

    @Test
    fun `could not tell outranks fine in the headline outcome`() {
        // A report full of unreadable checks must not be summarised as a pass.
        val subject = report(result(CheckOutcome.PASS, "A"), result(CheckOutcome.UNKNOWN, "B"))

        assertThat(subject.worstOutcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `a failure outranks everything else`() {
        val subject = report(
            result(CheckOutcome.PASS, "A"),
            result(CheckOutcome.CAUTION, "B"),
            result(CheckOutcome.FAIL, "C"),
        )

        assertThat(subject.worstOutcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `an all clear report reports pass`() {
        val subject = report(result(CheckOutcome.PASS, "A"), result(CheckOutcome.PASS, "B"))

        assertThat(subject.worstOutcome).isEqualTo(CheckOutcome.PASS)
        assertThat(subject.problemCount).isEqualTo(0)
        assertThat(subject.passCount).isEqualTo(2)
    }
}
