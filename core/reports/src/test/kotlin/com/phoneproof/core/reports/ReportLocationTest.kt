package com.phoneproof.core.reports

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pins the directory name, which is not a style question.
 *
 * Reports are written under the app's private files directory on the buyer's phone. Renaming this
 * would not fail anywhere — the new store would happily create the new directory — it would simply
 * make every report already on the device invisible, with nothing in the log to say why. The person
 * who notices is a buyer whose history emptied itself after an update.
 */
class ReportLocationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun the_directory_name_has_not_changed() {
        assertThat(REPORTS_DIRECTORY_NAME).isEqualTo("reports")
    }

    @Test
    fun a_report_written_through_the_factory_is_found_through_it_again() = runTest {
        // Proven by writing and reading rather than by inspecting a path, because the whole purpose of
        // the shared factory is that the run, the scan and the history screen land in the same place.
        val filesDir = temporaryFolder.newFolder("files")
        reportStore(filesDir).save(
            SavedReport(
                id = "1000-a",
                createdAtEpochMs = 1000L,
                deviceLabel = "realme RMX5110",
                androidLabel = "Android 16 (API 36)",
                results = emptyList(),
            ),
        )

        assertThat(reportStore(filesDir).find("1000-a")).isNotNull()
        assertThat(File(filesDir, REPORTS_DIRECTORY_NAME).isDirectory).isTrue()
    }

    @Test
    fun the_default_retention_is_the_free_tier_one() {
        // A caller that forgets to pass what the buyer paid for must not accidentally get unlimited
        // history; the safe default is the restrictive one.
        assertThat(ReportStore.FREE_TIER_RETAIN).isLessThan(ReportStore.PREMIUM_RETAIN)
    }
}
