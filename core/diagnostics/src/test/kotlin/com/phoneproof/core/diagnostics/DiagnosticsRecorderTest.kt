package com.phoneproof.core.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiagnosticsRecorderTest {

    private var now = 1_000L
    private fun recorder(capacity: Int = 5) =
        DiagnosticsRecorder(capacity = capacity, clock = { now })

    @Test
    fun `capacity must be positive`() {
        assertThat(runCatching { DiagnosticsRecorder(capacity = 0) }.isFailure).isTrue()
    }

    @Test
    fun `entries come back oldest first`() {
        val r = recorder()
        r.info("a", "first")
        r.info("b", "second")
        assertThat(r.entries().map { it.message }).containsExactly("first", "second").inOrder()
    }

    @Test
    fun `the buffer is bounded and drops the oldest entries`() {
        val r = recorder(capacity = 3)
        repeat(5) { r.info("tag", "msg$it") }
        assertThat(r.size).isEqualTo(3)
        assertThat(r.entries().map { it.message }).containsExactly("msg2", "msg3", "msg4").inOrder()
    }

    @Test
    fun `dropped entries are counted rather than silently discarded`() {
        // A truncated log that looks complete sends the reader hunting in the wrong place.
        val r = recorder(capacity = 2)
        repeat(6) { r.info("tag", "msg$it") }
        assertThat(r.droppedCount).isEqualTo(4)
    }

    @Test
    fun `levels are recorded as given`() {
        val r = recorder()
        r.info("t", "i"); r.warn("t", "w"); r.error("t", "e")
        r.crash("t", "c", IllegalStateException("boom"))
        assertThat(r.entries().map { it.level }).containsExactly(
            DiagLevel.INFO, DiagLevel.WARN, DiagLevel.ERROR, DiagLevel.CRASH,
        ).inOrder()
    }

    @Test
    fun `a throwable is rendered into a readable stack trace`() {
        val r = recorder()
        r.error("t", "failed", IllegalArgumentException("bad input"))
        val trace = r.entries().single().stackTrace
        assertThat(trace).isNotNull()
        assertThat(trace).contains("IllegalArgumentException")
        assertThat(trace).contains("bad input")
        assertThat(trace).contains("at ")
    }

    @Test
    fun `nested causes are included`() {
        val r = recorder()
        val root = IllegalStateException("root cause")
        r.error("t", "wrapped", RuntimeException("outer", root))
        val trace = r.entries().single().stackTrace!!
        assertThat(trace).contains("outer")
        assertThat(trace).contains("Caused by:")
        assertThat(trace).contains("root cause")
    }

    @Test
    fun `a self referencing cause does not loop forever`() {
        val r = recorder()
        // A throwable whose cause is itself would hang a naive walk.
        val evil = object : RuntimeException("self") {
            override val cause: Throwable get() = this
        }
        r.error("t", "self referencing", evil)
        assertThat(r.entries().single().stackTrace).contains("self")
    }

    @Test
    fun `entries with no throwable carry no stack trace`() {
        val r = recorder()
        r.info("t", "plain")
        assertThat(r.entries().single().stackTrace).isNull()
    }

    @Test
    fun `timestamps come from the injected clock`() {
        val r = recorder()
        now = 42L
        r.info("t", "at 42")
        assertThat(r.entries().single().timestampMillis).isEqualTo(42L)
    }

    @Test
    fun `clear resets both the buffer and the dropped count`() {
        val r = recorder(capacity = 2)
        repeat(5) { r.info("t", "m$it") }
        r.clear()
        assertThat(r.size).isEqualTo(0)
        assertThat(r.droppedCount).isEqualTo(0)
        assertThat(r.entries()).isEmpty()
    }

    @Test
    fun `entries returns a snapshot that later writes cannot mutate`() {
        val r = recorder()
        r.info("t", "one")
        val snapshot = r.entries()
        r.info("t", "two")
        assertThat(snapshot).hasSize(1)
    }

    @Test
    fun `a blank tag is rejected`() {
        assertThat(
            runCatching { DiagEntry(1L, DiagLevel.INFO, " ", "msg") }.isFailure,
        ).isTrue()
    }

    @Test
    fun `concurrent writes do not lose or corrupt entries`() {
        // Crashes arrive on whichever thread died, so this has to hold under contention.
        val r = DiagnosticsRecorder(capacity = 1000)
        val threads = 8
        val perThread = 100
        val pool = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                repeat(perThread) { i -> r.error("t$t", "m$i", RuntimeException("e")) }
                latch.countDown()
            }
        }
        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        assertThat(r.size).isEqualTo(threads * perThread)
        assertThat(r.entries()).hasSize(threads * perThread)
    }
}
