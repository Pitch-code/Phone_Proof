package com.phoneproof.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * These tests exist to keep the product's rules enforceable by the compiler and the test
 * suite rather than by memory. If someone later adds a check that reports a bare failure,
 * one of these tests is what stops it reaching a buyer.
 */
class CheckResultTest {

    private fun result(
        outcome: CheckOutcome,
        confidence: Confidence = Confidence.HIGH,
        consequence: String? = "It will annoy you daily.",
        action: String? = "Negotiate or walk away.",
        causes: List<String> = listOf("A screen protector can block touch."),
        id: String = "test.check",
        headline: String = "Something was found.",
    ) = CheckResult(
        id = id,
        title = "Test",
        outcome = outcome,
        confidence = confidence,
        headline = headline,
        consequence = consequence,
        action = action,
        falsePositiveCauses = causes,
    )

    @Test
    fun `pass needs no consequence or action`() {
        val passing = CheckResult(
            id = "test.pass",
            title = "Test",
            outcome = CheckOutcome.PASS,
            confidence = Confidence.HIGH,
            headline = "All good.",
        )
        assertThat(passing.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `unknown needs no consequence or action`() {
        val unknown = CheckResult(
            id = "test.unknown",
            title = "Test",
            outcome = CheckOutcome.UNKNOWN,
            confidence = Confidence.HIGH,
            headline = "Android does not expose this.",
        )
        assertThat(unknown.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `fail without a consequence is rejected`() {
        assertThat(runCatching { result(CheckOutcome.FAIL, consequence = null) }.isFailure).isTrue()
        assertThat(runCatching { result(CheckOutcome.FAIL, consequence = "  ") }.isFailure).isTrue()
    }

    @Test
    fun `fail without an action is rejected`() {
        assertThat(runCatching { result(CheckOutcome.FAIL, action = null) }.isFailure).isTrue()
    }

    @Test
    fun `fail without false positive causes is rejected`() {
        assertThat(
            runCatching { result(CheckOutcome.FAIL, causes = emptyList()) }.isFailure,
        ).isTrue()
    }

    @Test
    fun `caution is held to the same standard as fail`() {
        assertThat(runCatching { result(CheckOutcome.CAUTION, action = null) }.isFailure).isTrue()
        assertThat(
            runCatching { result(CheckOutcome.CAUTION, causes = emptyList()) }.isFailure,
        ).isTrue()
    }

    @Test
    fun `a low confidence fail is rejected and must be reported as caution`() {
        assertThat(
            runCatching { result(CheckOutcome.FAIL, confidence = Confidence.LOW) }.isFailure,
        ).isTrue()
        // The same finding at low confidence is allowed as a CAUTION.
        val downgraded = result(CheckOutcome.CAUTION, confidence = Confidence.LOW)
        assertThat(downgraded.outcome).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `blank id or headline is rejected`() {
        assertThat(runCatching { result(CheckOutcome.PASS, id = " ") }.isFailure).isTrue()
        assertThat(runCatching { result(CheckOutcome.PASS, headline = "") }.isFailure).isTrue()
    }

    @Test
    fun `measurement renders unit only when present`() {
        assertThat(Measurement("Coverage", "98.4", "%").display).isEqualTo("98.4 %")
        assertThat(Measurement("Cells", "504 / 512").display).isEqualTo("504 / 512")
    }
}
