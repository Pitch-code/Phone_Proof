package com.phoneproof.core.reports

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Saved reports, one JSON file each, in a directory the caller owns.
 *
 * Files rather than a database. A report is written once and read whole, there is nothing to query,
 * and the free tier keeps two of them — so an ORM and its migrations would be pure cost. It also
 * makes "export my reports" a file copy later on instead of a schema problem.
 *
 * A [File] is taken rather than a `Context` so this can be tested against a temp directory on the
 * JVM. Retention and corrupt-file recovery are the parts most likely to lose someone's data, and
 * they need to be provable without an emulator.
 *
 * @param directory created on demand; it does not have to exist yet.
 * @param retain how many reports to keep, newest first. Defaults to [FREE_TIER_RETAIN].
 */
class ReportStore(
    private val directory: File,
    private val retain: Int = FREE_TIER_RETAIN,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        require(retain >= 1) { "retain must keep at least one report, was $retain" }
    }

    private val json = Json {
        // Set so a report written by an older build still opens after a field is added. Without it,
        // adding one optional field to CheckResult would make every previously saved report
        // unreadable, which is a silent data loss the buyer would discover at the worst moment.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Writes [report], then prunes to [retain].
     *
     * @return how many older reports were deleted to stay within the limit, so the caller can say
     *   so out loud instead of letting the buyer's history quietly shrink.
     */
    suspend fun save(report: SavedReport): Int = withContext(io) {
        directory.mkdirs()
        // Written to a temp file and moved into place. A half-written JSON file from a process death
        // mid-write would otherwise be indistinguishable from a corrupt report on next launch.
        val target = fileFor(report.id)
        val temp = File(directory, "${target.name}.tmp")
        temp.writeText(json.encodeToString(report))
        if (!temp.renameTo(target)) {
            // renameTo can fail on some filesystems; a copy still beats losing the report.
            target.writeText(temp.readText())
            temp.delete()
        }
        prune()
    }

    /** Newest first. Unreadable files are skipped, never thrown. */
    suspend fun list(): List<SavedReport> = withContext(io) { readAll() }

    suspend fun find(id: String): SavedReport? = withContext(io) {
        readAll().firstOrNull { it.id == id }
    }

    suspend fun delete(id: String): Boolean = withContext(io) { fileFor(id).delete() }

    suspend fun clear(): Unit = withContext(io) {
        jsonFiles().forEach { it.delete() }
    }

    /**
     * How many stored files could not be read.
     *
     * Exposed so a corrupt report is surfaced rather than silently disappearing from the list —
     * a buyer who saved three reports and sees two deserves to know one is damaged.
     */
    suspend fun unreadableCount(): Int = withContext(io) {
        jsonFiles().count { file -> decode(file) == null }
    }

    private fun readAll(): List<SavedReport> =
        jsonFiles()
            .mapNotNull { decode(it) }
            .sortedByDescending { it.createdAtEpochMs }

    private fun decode(file: File): SavedReport? = runCatching {
        json.decodeFromString<SavedReport>(file.readText())
    }.getOrNull()
    // Deliberately swallowed. CheckResult's init block enforces invariants such as "no bare FAIL",
    // so decoding doubles as validation: a tampered or truncated file throws here and is treated as
    // unreadable rather than crashing the history screen the buyer just opened.

    private fun jsonFiles(): List<File> =
        directory.listFiles { f: File -> f.isFile && f.name.endsWith(SUFFIX) }?.toList() ?: emptyList()

    private fun fileFor(id: String) = File(directory, "${sanitise(id)}$SUFFIX")

    /** Deletes oldest-first beyond [retain]. Returns how many went. */
    private fun prune(): Int {
        val all = readAll()
        if (all.size <= retain) return 0
        val doomed = all.drop(retain)
        doomed.forEach { fileFor(it.id).delete() }
        return doomed.size
    }

    companion object {
        private const val SUFFIX = ".json"

        /**
         * What the free tier keeps.
         *
         * Two, because that is exactly what the Premium card promises ("keep every report instead
         * of only the last two"). Scanning itself stays unlimited — the paid tier adds history and
         * export, it never rations the measurement the app exists to perform.
         */
        const val FREE_TIER_RETAIN: Int = 2

        /** Effectively unlimited, for a paid entitlement. */
        const val PREMIUM_RETAIN: Int = Int.MAX_VALUE

        /**
         * A sortable, filename-safe id.
         *
         * The timestamp leads so lexical order matches chronological order, and a short suffix
         * keeps two scans finished in the same millisecond from overwriting each other.
         */
        fun newId(epochMs: Long, suffix: String): String = "$epochMs-${sanitise(suffix)}"

        private fun sanitise(raw: String): String =
            raw.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
                .joinToString("")
                .take(80)
                .ifBlank { "report" }
    }
}
