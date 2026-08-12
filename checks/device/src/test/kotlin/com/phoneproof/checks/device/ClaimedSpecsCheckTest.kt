package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import org.junit.Test

class ClaimedSpecsCheckTest {

    private fun facts(
        totalStorageBytes: Long? = 109_678_919_680,
        totalRamBytes: Long? = 7_900_000_000,
        manufacturer: String = "realme",
        model: String = "RMX5110",
    ) = DeviceFacts(
        manufacturer = manufacturer,
        brand = "realme",
        model = model,
        device = "RE6440L1",
        hardware = "mt6878",
        sdkInt = 36,
        androidRelease = "16",
        totalStorageBytes = totalStorageBytes,
        totalRamBytes = totalRamBytes,
    )

    // ---------------------------------------------------------------- honest phones

    @Test
    fun `an honest 128 GB phone passes`() {
        // The realme RMX5110's real reading: 109.7 GB usable on a 128 GB phone, which is 86%. This
        // is what every honest phone looks like, and flagging it would make the check useless.
        val result = ClaimedSpecsCheck.evaluate(ClaimedSpecs(storageGb = 128), facts())

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `an honest 8 GB RAM phone passes`() {
        // 7.9e9 bytes is 7.36 GiB, or 92% of a marketed 8 GB. Passing this is the point of measuring
        // RAM in binary units.
        val result = ClaimedSpecsCheck.evaluate(ClaimedSpecs(ramGb = 8), facts())

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `RAM is judged in binary units, not decimal`() {
        // The trap this check exists to avoid. 7.9e9 bytes is 7.9 "decimal GB" but only 7.36 GiB.
        // Using the storage divisor for RAM would compute 0.99 and pass everything, including a
        // phone with genuinely half the memory it claims — and using the binary divisor for storage
        // would accuse every honest phone. Both units are exercised here on one set of facts.
        val bothClaimed = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(storageGb = 128, ramGb = 8),
            facts(),
        )
        assertThat(bothClaimed.outcome).isEqualTo(CheckOutcome.PASS)

        val halfTheRam = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(ramGb = 8),
            facts(totalRamBytes = 3_900_000_000),
        )
        assertThat(halfTheRam.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `a phone at exactly the storage threshold is not accused`() {
        val atThreshold = (128 * 1_000_000_000.0 * ClaimedSpecsCheck.MIN_STORAGE_FRACTION).toLong()
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(storageGb = 128),
            facts(totalStorageBytes = atThreshold),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    // ------------------------------------------------------------------- the frauds

    @Test
    fun `a phone sold as 128 GB holding 32 fails`() {
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(storageGb = 128),
            facts(totalStorageBytes = 30_000_000_000),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.headline).contains("less storage")
        assertThat(result.action).isNotEmpty()
        assertThat(result.falsePositiveCauses).isNotEmpty()
    }

    @Test
    fun `halved memory fails and names memory`() {
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(ramGb = 8),
            facts(totalRamBytes = 3_900_000_000),
        )

        assertThat(result.headline).contains("memory")
    }

    @Test
    fun `both short is reported as both`() {
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(storageGb = 256, ramGb = 12),
            facts(totalStorageBytes = 30_000_000_000, totalRamBytes = 3_900_000_000),
        )

        assertThat(result.headline).contains("storage and memory")
    }

    // -------------------------------------------------------------- absent readings

    @Test
    fun `nothing claimed is unknown, not a pass`() {
        val result = ClaimedSpecsCheck.evaluate(ClaimedSpecs(), facts())

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `an unreadable measurement never becomes an accusation`() {
        // A phone that will not report its storage must not be accused of hiding a small chip.
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(storageGb = 128, ramGb = 8),
            facts(totalStorageBytes = null, totalRamBytes = null),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.measurements.map { it.display }).contains("not readable")
    }

    // ----------------------------------------------------------------- model naming

    @Test
    fun `a model name that differs is never a failure on its own`() {
        // "realme P4 5G" against a reported "RMX5110" is the normal case, not a fraud. Judging it
        // would fire on nearly every phone, which is why no device catalogue is used.
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(modelName = "realme P4 5G"),
            facts(),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.consequence).contains("rarely match exactly")
    }

    @Test
    fun `both model lines are shown so the buyer can judge`() {
        val result = ClaimedSpecsCheck.evaluate(ClaimedSpecs(modelName = "realme P4 5G"), facts())

        assertThat(result.measurements.first { it.label == "Model claimed" }.display)
            .isEqualTo("realme P4 5G")
        assertThat(result.measurements.first { it.label == "Phone reports" }.display)
            .isEqualTo("realme RMX5110")
    }

    @Test
    fun `no model explanation is added when no model was claimed`() {
        val result = ClaimedSpecsCheck.evaluate(ClaimedSpecs(storageGb = 128), facts())

        assertThat(result.consequence).isNull()
    }

    // ------------------------------------------------------------------------ extras

    @Test
    fun `more storage than claimed is reported without alarm`() {
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(storageGb = 64),
            facts(totalStorageBytes = 109_678_919_680),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("more storage")
    }

    @Test
    fun `the storage threshold matches the storage check, so one report cannot contradict itself`() {
        assertThat(ClaimedSpecsCheck.MIN_STORAGE_FRACTION)
            .isEqualTo(StorageCheck.MIN_PLAUSIBLE_FRACTION)
    }

    @Test
    fun `the claim is named as the buyer's own input`() {
        val result = ClaimedSpecsCheck.evaluate(
            ClaimedSpecs(storageGb = 128),
            facts(totalStorageBytes = 30_000_000_000),
        )

        assertThat(result.falsePositiveCauses.first()).contains("what you typed in")
    }

    @Test
    fun `check id is stable and sits in the hardware namespace`() {
        assertThat(ClaimedSpecsCheck.CHECK_ID).isEqualTo("hardware.claimed_specs")
    }
}
