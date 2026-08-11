package com.phoneproof.core.diagnostics

/**
 * A bounded, in-memory record of what the app did and what went wrong.
 *
 * The reason this exists: during testing the only channel for "something broke" was a person
 * describing a symptom from memory, which is slow and lossy. A log the tester can copy and paste
 * turns a vague report into an exact one.
 *
 * Bounded on purpose. An unbounded log on a phone eventually becomes a memory problem, and the
 * oldest entries are the least useful. [droppedCount] is tracked so a truncated log announces the
 * fact rather than quietly pretending to be complete — a log that hides its own gaps is worse than
 * no log, because it sends you looking in the wrong place.
 *
 * Thread-safe: crashes arrive on whichever thread died, not the main one.
 */
class DiagnosticsRecorder(
    val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    private val lock = Any()
    private val buffer = ArrayDeque<DiagEntry>(capacity)
    private var dropped = 0

    /** How many entries were discarded to stay within [capacity]. */
    val droppedCount: Int get() = synchronized(lock) { dropped }

    val size: Int get() = synchronized(lock) { buffer.size }

    fun record(entry: DiagEntry) {
        synchronized(lock) {
            while (buffer.size >= capacity) {
                buffer.removeFirst()
                dropped++
            }
            buffer.addLast(entry)
        }
    }

    fun record(
        level: DiagLevel,
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        record(
            DiagEntry(
                timestampMillis = clock(),
                level = level,
                tag = tag,
                message = message,
                stackTrace = error?.let(::renderStackTrace),
            ),
        )
    }

    fun info(tag: String, message: String) = record(DiagLevel.INFO, tag, message)

    fun warn(tag: String, message: String, error: Throwable? = null) =
        record(DiagLevel.WARN, tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) =
        record(DiagLevel.ERROR, tag, message, error)

    fun crash(tag: String, message: String, error: Throwable) =
        record(DiagLevel.CRASH, tag, message, error)

    /** Oldest first. A copy, so a caller iterating it cannot be tripped up by a concurrent write. */
    fun entries(): List<DiagEntry> = synchronized(lock) { buffer.toList() }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            dropped = 0
        }
    }

    companion object {
        /**
         * Enough to cover a full inspection run several times over while staying small enough that
         * the whole log can be pasted into a message.
         */
        const val DEFAULT_CAPACITY: Int = 300

        private fun renderStackTrace(error: Throwable): String = buildString {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSE_DEPTH) {
                if (depth > 0) append("Caused by: ")
                append(current::class.java.name)
                current.message?.let { append(": ").append(it) }
                appendLine()
                current.stackTrace.take(MAX_FRAMES).forEach { appendLine("    at $it") }
                val next = current.cause
                current = if (next === current) null else next
                depth++
            }
        }.trimEnd()

        /** Deep enough to find the cause, shallow enough to stay pasteable. */
        private const val MAX_FRAMES = 12
        private const val MAX_CAUSE_DEPTH = 4
    }
}
