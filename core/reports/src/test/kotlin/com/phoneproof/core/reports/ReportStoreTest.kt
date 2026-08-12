package com.phoneproof.core.reports

import com.google.common.truth.Truth.assertThat
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(retain: Int = ReportStore.FREE_TIER_RETAIN) =
        ReportStore(File(temp.root, "reports"), retain = retain)

    private fun pass(id: String = "hardware.storage") = CheckResult(
        id = id,
        title = "Storage",
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
        falsePositiveCauses = listOf("A screen protector can mask touches."),
    )

    private fun report(
        at: Long,
        results: List<CheckResult> = listOf(pass()),
    ) = SavedReport(
        id = ReportStore.newId(at, "abc"),
        createdAtEpochMs = at,
        deviceLabel = "realme RMX5110",
        androidLabel = "Android 16 (API 36)",
        results = results,
    )

    @Test
    fun `a saved report comes back with its contents intact`() = runTest {
        val subject = store()
        val original = report(1_000, listOf(pass(), fail()))

        subject.save(original)

        assertThat(subject.list()).containsExactly(original)
    }

    @Test
    fun `the directory does not have to exist beforehand`() = runTest {
        val subject = ReportStore(File(temp.root, "nested/deeper/reports"))

        subject.save(report(1_000))

        assertThat(subject.list()).hasSize(1)
    }

    @Test
    fun `listing an empty or missing directory returns nothing rather than failing`() = runTest {
        assertThat(store().list()).isEmpty()
    }

    @Test
    fun `reports come back newest first`() = runTest {
        val subject = store(retain = 10)
        subject.save(report(1_000))
        subject.save(report(3_000))
        subject.save(report(2_000))

        assertThat(subject.list().map { it.createdAtEpochMs })
            .containsExactly(3_000L, 2_000L, 1_000L)
            .inOrder()
    }

    @Test
    fun `the free tier keeps the newest two and prunes the rest`() = runTest {
        val subject = store()
        subject.save(report(1_000))
        subject.save(report(2_000))
        val pruned = subject.save(report(3_000))

        assertThat(pruned).isEqualTo(1)
        assertThat(subject.list().map { it.createdAtEpochMs }).containsExactly(3_000L, 2_000L)
    }

    @Test
    fun `pruning removes the oldest, not the most recent`() = runTest {
        // The failure that matters: pruning newest-first would throw away the report the buyer just
        // took while they were still standing in front of the seller.
        val subject = store()
        subject.save(report(5_000))
        subject.save(report(1_000))
        subject.save(report(9_000))

        assertThat(subject.list().map { it.createdAtEpochMs }).containsExactly(9_000L, 5_000L)
    }

    @Test
    fun `a premium retention keeps everything`() = runTest {
        val subject = store(retain = ReportStore.PREMIUM_RETAIN)
        repeat(25) { subject.save(report(it.toLong() + 1)) }

        assertThat(subject.list()).hasSize(25)
    }

    @Test
    fun `saving under an existing id replaces it instead of duplicating`() = runTest {
        val subject = store()
        val first = report(1_000)
        subject.save(first)
        subject.save(first.copy(deviceLabel = "realme P4 5G"))

        assertThat(subject.list()).hasSize(1)
        assertThat(subject.list().single().deviceLabel).isEqualTo("realme P4 5G")
    }

    @Test
    fun `a corrupt file is skipped, and the readable reports still open`() = runTest {
        val subject = store(retain = 10)
        subject.save(report(1_000))

        File(temp.root, "reports/9999-broken.json").writeText("{ this is not json")

        // The history screen must still work. Throwing here would take out the whole screen because
        // one file went bad.
        assertThat(subject.list()).hasSize(1)
        assertThat(subject.unreadableCount()).isEqualTo(1)
    }

    @Test
    fun `a file that breaks the model's own rules counts as unreadable`() = runTest {
        // A FAIL with no consequence, action or false-positive causes. CheckResult's init block
        // rejects it, so decoding fails and the file is treated as damaged rather than surfacing a
        // bare accusation the product forbids.
        val subject = store(retain = 10)
        File(temp.root, "reports").mkdirs()
        File(temp.root, "reports/1000-bare.json").writeText(
            """
            {"id":"1000-bare","createdAtEpochMs":1000,"deviceLabel":"x","androidLabel":"y",
             "results":[{"id":"a.b","title":"T","outcome":"FAIL","confidence":"HIGH",
             "headline":"Bad"}]}
            """.trimIndent(),
        )

        assertThat(subject.list()).isEmpty()
        assertThat(subject.unreadableCount()).isEqualTo(1)
    }

    @Test
    fun `an unknown field does not make an older report unreadable`() = runTest {
        // Guards the forward-compatibility promise: adding a field must not orphan saved reports.
        val subject = store(retain = 10)
        File(temp.root, "reports").mkdirs()
        File(temp.root, "reports/1000-future.json").writeText(
            """
            {"id":"1000-future","createdAtEpochMs":1000,"deviceLabel":"realme","androidLabel":"16",
             "somethingAddedLater":42,
             "results":[{"id":"a.b","title":"T","outcome":"PASS","confidence":"HIGH",
             "headline":"Fine"}]}
            """.trimIndent(),
        )

        assertThat(subject.list()).hasSize(1)
        assertThat(subject.unreadableCount()).isEqualTo(0)
    }

    @Test
    fun `no temp files are left behind after a save`() = runTest {
        // The write is staged through a .tmp file; leaving one would eventually look like a corrupt
        // report to anyone listing the directory.
        val subject = store()
        subject.save(report(1_000))

        val names = File(temp.root, "reports").listFiles()!!.map { it.name }
        assertThat(names).hasSize(1)
        assertThat(names.single()).endsWith(".json")
    }

    @Test
    fun `find returns the requested report and null for anything else`() = runTest {
        val subject = store()
        val saved = report(1_000)
        subject.save(saved)

        assertThat(subject.find(saved.id)).isEqualTo(saved)
        assertThat(subject.find("nope")).isNull()
    }

    @Test
    fun `delete removes only the named report`() = runTest {
        val subject = store(retain = 10)
        val keep = report(1_000)
        val drop = report(2_000)
        subject.save(keep)
        subject.save(drop)

        assertThat(subject.delete(drop.id)).isTrue()
        assertThat(subject.list()).containsExactly(keep)
    }

    @Test
    fun `clear removes everything`() = runTest {
        val subject = store(retain = 10)
        subject.save(report(1_000))
        subject.save(report(2_000))

        subject.clear()

        assertThat(subject.list()).isEmpty()
    }

    @Test
    fun `ids sort chronologically as text so filenames order themselves`() {
        val early = ReportStore.newId(1_000, "a")
        val late = ReportStore.newId(2_000, "a")

        assertThat(early < late).isTrue()
    }

    @Test
    fun `an id containing path characters cannot escape the directory`() = runTest {
        // A device label or suffix reaching the id must never be able to write outside the folder.
        val subject = store()
        val nasty = report(1_000).copy(id = "../../etc/passwd")

        subject.save(nasty)

        val files = File(temp.root, "reports").listFiles()!!
        assertThat(files).hasLength(1)
        assertThat(files.single().name).doesNotContain("/")
        assertThat(File(temp.root, "reports").canonicalPath)
            .isEqualTo(files.single().parentFile.canonicalPath)
    }

    @Test
    fun `retention below one is rejected rather than silently keeping nothing`() {
        val thrown = runCatching { ReportStore(temp.root, retain = 0) }
        assertThat(thrown.isFailure).isTrue()
    }
}
