package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class RootCheckTest {

    @Test
    fun `locked bootloader with no root passes`() {
        val result = RootCheck.evaluate(RootSignals(verifiedBootState = "green"))
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `a found su binary is a high confidence failure`() {
        val result = RootCheck.evaluate(
            RootSignals(suBinaryPaths = listOf("/system/xbin/su"), verifiedBootState = "green"),
        )
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("rooted")
        // The consequence a buyer actually cares about.
        assertThat(result.consequence).contains("UPI")
    }

    @Test
    fun `a root manager app is a failure even with a green boot state`() {
        // Root can be present with verified boot reporting green on some setups, so the signals are
        // independent rather than hierarchical.
        val result = RootCheck.evaluate(
            RootSignals(
                rootManagerPackages = listOf("com.topjohnwu.magisk"),
                verifiedBootState = "green",
            ),
        )
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `both signals together are named in the headline`() {
        val result = RootCheck.evaluate(
            RootSignals(
                suBinaryPaths = listOf("/sbin/su", "/system/bin/su"),
                rootManagerPackages = listOf("com.topjohnwu.magisk"),
            ),
        )
        assertThat(result.headline).contains("2 su binaries")
        assertThat(result.headline).contains("1 root manager app")
    }

    @Test
    fun `an unlocked bootloader is a failure on its own`() {
        val result = RootCheck.evaluate(RootSignals(verifiedBootState = "orange"))
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("unlocked")
        // The point of this outcome: an unlocked bootloader undermines every other measurement,
        // which is why it sits second in the scan order rather than last.
        assertThat(result.consequence).contains("trusted")
        assertThat(result.action).contains("relock")
    }

    @Test
    fun `a red verified boot state is a failure`() {
        val result = RootCheck.evaluate(RootSignals(verifiedBootState = "red"))
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.action).contains("Do not buy")
    }

    @Test
    fun `a yellow state is a caution about custom firmware, not a failure`() {
        val result = RootCheck.evaluate(RootSignals(verifiedBootState = "yellow"))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.consequence).contains("custom operating system")
    }

    @Test
    fun `state matching is case and whitespace tolerant`() {
        assertThat(RootCheck.evaluate(RootSignals(verifiedBootState = " GREEN ")).outcome)
            .isEqualTo(CheckOutcome.PASS)
        assertThat(RootCheck.evaluate(RootSignals(verifiedBootState = "Orange")).outcome)
            .isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `no root found but unreadable boot state is a low confidence caution, not a pass`() {
        // Root managers hide themselves, so "found nothing" without the verified-boot tie-breaker
        // must not be dressed up as a clean result.
        val result = RootCheck.evaluate(RootSignals(verifiedBootState = null))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
        assertThat(result.headline).contains("could not be read")
    }

    @Test
    fun `nothing readable at all is CAN'T TELL`() {
        val result = RootCheck.evaluate(RootSignals(readable = false))
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `measurements always report all four signals`() {
        val labels = RootCheck.evaluate(RootSignals(verifiedBootState = "green"))
            .measurements.map { it.label }
        assertThat(labels).containsExactly(
            "su binary", "Root manager app", "Verified boot", "Build tags",
        )
    }

    @Test
    fun `check id is stable`() {
        assertThat(RootCheck.evaluate(RootSignals(verifiedBootState = "green")).id)
            .isEqualTo("security.root")
    }
}
