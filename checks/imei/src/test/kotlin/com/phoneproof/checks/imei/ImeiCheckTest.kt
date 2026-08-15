package com.phoneproof.checks.imei

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class ImeiCheckTest {

    private val valid = Imei.of("490154203237518")
    private val invalid = Imei.of("490154203237510")

    @Test
    fun `a well-formed number passes on the arithmetic and says nothing more`() {
        val result = ImeiCheck.evaluate(valid)

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        // The headline must disclaim in the same breath as it passes. A buyer reads the badge and the
        // first line, and nothing else.
        assertThat(result.headline).contains("does not tell you whether the phone is blocked")
    }

    @Test
    fun `a pass still tells the buyer to check the blocklist`() {
        // The point of putting the action on every outcome. The blocklist question is open regardless
        // of what the checksum said, and a pass that dropped the instruction would read as clearance.
        val result = ImeiCheck.evaluate(valid)

        assertThat(result.action).isNotNull()
        assertThat(result.action).contains("CEIR")
    }

    @Test
    fun `the title names the arithmetic rather than the phone's identity`() {
        // "IMEI: PASS" would be read as "not stolen". "IMEI checksum: PASS" cannot be.
        assertThat(ImeiCheck.evaluate(valid).title).isEqualTo("IMEI checksum")
    }

    @Test
    fun `a bad checksum is a CAUTION and never a FAIL`() {
        val result = ImeiCheck.evaluate(invalid)

        // The whole judgement of this check. A failed checksum is equally consistent with a cloned
        // handset and with one mistyped digit, and the app cannot tell which — so it must not accuse.
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.outcome).isNotEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `a bad checksum carries consequence action and false positive causes`() {
        // Enforced by CheckResult's init block too; asserted here so the reason is visible.
        val result = ImeiCheck.evaluate(invalid)

        assertThat(result.consequence).isNotEmpty()
        assertThat(result.action).isNotEmpty()
        assertThat(result.falsePositiveCauses).isNotEmpty()
        // The first cause has to be the mundane one, because it is by far the most likely.
        assertThat(result.falsePositiveCauses.first()).contains("mistyped")
    }

    @Test
    fun `a bad checksum shows what the last digit should have been`() {
        val result = ImeiCheck.evaluate(invalid)

        val row = result.measurements.first { it.label == "Last digit" }
        assertThat(row.display).isEqualTo("0 — expected 8")
    }

    @Test
    fun `nothing typed is UNKNOWN with no consequence to answer for`() {
        val result = ImeiCheck.evaluate(Imei.of(""))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.consequence).isNull()
        assertThat(result.action).contains("*#06#")
    }

    @Test
    fun `a partial number reports its length rather than judging it`() {
        val result = ImeiCheck.evaluate(Imei.of("4901542"))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.headline).contains("7 digits")
        assertThat(result.measurements.first { it.label == "Entered" }.display)
            .isEqualTo("7 of 15 digits")
    }

    @Test
    fun `no outcome ever claims the phone is or is not stolen`() {
        // Play policy, asserted rather than trusted to review: the app must not imply it can confirm a
        // phone is stolen. Scanning every string of every outcome is crude and exactly right — this is
        // the claim that must never creep back in through a reworded headline.
        val forbidden = listOf("not stolen", "is stolen", "clean", "cleared", "verified", "legitimate")
        val results = listOf(
            ImeiCheck.evaluate(valid),
            ImeiCheck.evaluate(invalid),
            ImeiCheck.evaluate(Imei.of("")),
            ImeiCheck.evaluate(Imei.of("4901542")),
        )

        results.forEach { result ->
            val prose = listOfNotNull(result.headline, result.consequence, result.action)
                .joinToString(" ")
                .lowercase()
            forbidden.forEach { claim ->
                assertThat(prose).doesNotContain(claim)
            }
        }
    }

    @Test
    fun `check id is stable so saved reports keep comparing correctly`() {
        assertThat(ImeiCheck.evaluate(valid).id).isEqualTo("identity.imei_checksum")
    }
}
