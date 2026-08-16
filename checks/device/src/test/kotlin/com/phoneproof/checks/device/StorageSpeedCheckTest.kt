package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.Confidence
import org.junit.Test

class StorageSpeedCheckTest {

    private val sixtyFourMb = 64L * 1024 * 1024
    private val fourMb = 4L * 1024 * 1024

    /** Sixteen chunks that each took [chunkMillis], plus any [stalls] appended. */
    private fun measured(
        chunkMillis: Long,
        stalls: List<Long> = emptyList(),
        matched: Boolean = true,
        freeBytes: Long = 20L * 1024 * 1024 * 1024,
    ): StorageSpeedTrace {
        val chunks = List(16) { chunkMillis } + stalls
        return StorageSpeedTrace(
            attempt = StorageSpeedAttempt.MEASURED,
            bytesWritten = fourMb * chunks.size,
            writeMillis = chunks.sum(),
            readMillis = chunks.sum() / 3,
            chunkBytes = fourMb,
            chunkMillis = chunks,
            readBackMatched = matched,
            freeBytes = freeBytes,
        )
    }

    // ------------------------------------------------------------------ arithmetic

    @Test
    fun throughput_is_megabytes_over_seconds() {
        // 64 MB in 1 second is about 67 MB/s, because a megabyte of storage is 1024*1024 bytes and a
        // megabyte per second is 1,000,000. Getting that wrong by 5% would not matter; getting it wrong by
        // 1024/1000 twice would.
        val trace = StorageSpeedTrace(
            attempt = StorageSpeedAttempt.MEASURED,
            bytesWritten = sixtyFourMb,
            writeMillis = 1_000L,
        )

        assertThat(trace.writeMbPerSecond).isWithin(0.5).of(67.1)
    }

    @Test
    fun a_zero_length_measurement_does_not_divide_by_zero() {
        val nothing = StorageSpeedTrace(attempt = StorageSpeedAttempt.MEASURED)

        assertThat(nothing.writeMbPerSecond).isEqualTo(0.0)
        assertThat(nothing.readMbPerSecond).isEqualTo(0.0)
        assertThat(nothing.medianChunkMbPerSecond).isEqualTo(0.0)
    }

    @Test
    fun the_median_chunk_ignores_the_outlier_that_the_average_would_absorb() {
        // The whole reason chunks are timed separately. Sixteen fast chunks and one two-second stall: the
        // median stays fast, which is what makes the stall visible as a stall rather than a slow average.
        val trace = measured(chunkMillis = 40L, stalls = listOf(2_000L))

        assertThat(trace.medianChunkMbPerSecond).isGreaterThan(90.0)
        assertThat(trace.slowestChunkMbPerSecond).isLessThan(3.0)
        assertThat(trace.stallFactor).isGreaterThan(StorageSpeedCheck.STALL_FACTOR)
    }

    // ------------------------------------------------------------------ verdicts

    @Test
    fun healthy_flash_passes() {
        val result = StorageSpeedCheck.evaluate(measured(chunkMillis = 40L))

        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun a_pass_still_says_the_capacity_was_never_checked() {
        // The single most likely way this check misleads someone: a green tick read as "the 256 GB is real".
        val result = StorageSpeedCheck.evaluate(measured(chunkMillis = 40L))

        assertThat(result.measurements.map { it.label }).contains("Capacity checked")
        assertThat(result.measurements.first { it.label == "Capacity checked" }.value)
            .contains("no")
    }

    @Test
    fun the_capacity_note_refuses_to_claim_what_it_cannot_prove() {
        // Proving capacity means writing hundreds of gigabytes to a stranger's phone. The note says so
        // rather than leaving the buyer to assume otherwise.
        assertThat(StorageSpeedCheck.CAPACITY_NOTE).contains("not how much of it there is")
        assertThat(StorageSpeedCheck.CAPACITY_NOTE).contains("unverified")
    }

    @Test
    fun very_slow_writes_are_a_caution_and_name_recycled_flash() {
        // 4 MB per 500 ms is about 8 MB/s.
        val result = StorageSpeedCheck.evaluate(measured(chunkMillis = 500L))

        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(result.consequence).contains("counterfeit")
        assertThat(result.action).contains("storage is not repairable")
    }

    @Test
    fun a_stall_is_caught_even_when_the_average_looks_respectable() {
        // The finding no benchmark reports. The average here is perfectly acceptable and the phone freezes
        // for two seconds while saving a photo.
        val trace = measured(chunkMillis = 40L, stalls = listOf(2_000L, 1_800L))

        assertThat(trace.writeMbPerSecond).isGreaterThan(StorageSpeedCheck.SLOW_WRITE_MB_PER_SECOND)

        val result = StorageSpeedCheck.evaluate(trace)
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("times slower than the rest")
        assertThat(result.consequence).contains("hides it")
    }

    @Test
    fun ordinary_variation_between_chunks_is_not_a_stall() {
        // A phone's background is busy but not that busy. Chunks between 40 and 90 ms is normal life.
        val trace = StorageSpeedTrace(
            attempt = StorageSpeedAttempt.MEASURED,
            bytesWritten = fourMb * 6,
            writeMillis = 390L,
            readMillis = 130L,
            chunkBytes = fourMb,
            chunkMillis = listOf(40L, 55L, 90L, 65L, 70L, 70L),
            freeBytes = 20L * 1024 * 1024 * 1024,
        )

        assertThat(trace.stallFactor).isLessThan(StorageSpeedCheck.STALL_FACTOR)
        assertThat(StorageSpeedCheck.evaluate(trace).outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun slowness_outranks_a_stall_because_it_is_the_bigger_finding() {
        // Both present. A chip that is slow throughout is worse news than one that is fast with a hiccup,
        // and the headline should be the more serious of the two.
        val result = StorageSpeedCheck.evaluate(measured(chunkMillis = 600L, stalls = listOf(9_000L)))

        assertThat(result.headline).contains("very slow for any phone")
    }

    // ------------------------------------------------------------------ the flat failure

    @Test
    fun bytes_that_came_back_wrong_are_the_one_outright_failure() {
        val result = StorageSpeedCheck.evaluate(measured(chunkMillis = 40L, matched = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.action).startsWith("Do not buy this phone")
    }

    @Test
    fun the_read_back_line_never_contradicts_the_verdict_above_it() {
        // It did once. "Written and verified: 64 MB" appeared on the card whose headline was that the bytes
        // came back different, because the label folded the verification into the byte count.
        val corrupt = StorageSpeedCheck.evaluate(measured(chunkMillis = 40L, matched = false))
        assertThat(corrupt.measurements.first { it.label == "Read back" }.value)
            .isEqualTo("DID NOT MATCH")

        val clean = StorageSpeedCheck.evaluate(measured(chunkMillis = 40L))
        assertThat(clean.measurements.first { it.label == "Read back" }.value)
            .isEqualTo("every byte matched")
    }

    @Test
    fun corruption_invalidates_the_rest_of_the_report_and_says_so() {
        // Worth stating explicitly: if the storage returns the wrong data, every other measurement in the
        // app was read through the same broken chip.
        val result = StorageSpeedCheck.evaluate(measured(chunkMillis = 40L, matched = false))

        assertThat(result.action).contains("including everything else in this report")
    }

    @Test
    fun corruption_outranks_a_perfect_speed() {
        // Fast and wrong is worse than slow and right, so the speed must not be allowed to headline.
        val result = StorageSpeedCheck.evaluate(measured(chunkMillis = 10L, matched = false))

        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    // ------------------------------------------------------------------ never measured

    @Test
    fun a_full_phone_is_not_filled_further_and_is_not_accused() {
        val result = StorageSpeedCheck.evaluate(
            StorageSpeedTrace(
                attempt = StorageSpeedAttempt.NOT_ENOUGH_SPACE,
                freeBytes = 200L * 1024 * 1024,
            ),
        )

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
        assertThat(result.action).contains("clear it")
    }

    @Test
    fun a_failed_write_is_reported_as_unmeasured_rather_than_as_a_fault() {
        val result = StorageSpeedCheck.evaluate(StorageSpeedTrace(StorageSpeedAttempt.FAILED))

        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun no_speed_figures_are_shown_when_nothing_was_measured() {
        // "0.0 MB/s" against a phone that was never written to would read as dead storage.
        val labels = StorageSpeedCheck.evaluate(StorageSpeedTrace(StorageSpeedAttempt.FAILED))
            .measurements.map { it.label }

        assertThat(labels).doesNotContain("Write speed")
        assertThat(labels).contains("Free space")
    }

    @Test
    fun the_threshold_sits_below_even_old_respectable_hardware() {
        // A worn eMMC from 2015 manages 20 to 40 MB/s. The bar has to be a genuine outlier rather than a
        // slow afternoon, because storage is not repairable and this verdict tells people to walk away.
        assertThat(StorageSpeedCheck.SLOW_WRITE_MB_PER_SECOND).isLessThan(20.0)
    }

    @Test
    fun every_outcome_tells_the_buyer_what_to_do_next() {
        listOf(
            StorageSpeedTrace(StorageSpeedAttempt.FAILED),
            StorageSpeedTrace(StorageSpeedAttempt.NOT_ENOUGH_SPACE),
            measured(chunkMillis = 600L),
            measured(chunkMillis = 40L, stalls = listOf(3_000L)),
            measured(chunkMillis = 40L, matched = false),
        ).forEach { assertThat(StorageSpeedCheck.evaluate(it).action).isNotEmpty() }
    }
}
