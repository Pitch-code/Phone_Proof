package com.phoneproof.core.diagnostics

enum class DiagLevel(val label: String) {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),

    /** An uncaught exception. The app is about to die. */
    CRASH("CRASH"),
}

/**
 * One recorded event.
 *
 * Nothing here is collected automatically from the system. Entries exist only because code
 * explicitly recorded them, which is what keeps the log free of anything personal — and there is
 * no device identifier to leak in the first place, since IMEI and serial are unreadable on
 * Android 10+.
 */
data class DiagEntry(
    val timestampMillis: Long,
    val level: DiagLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
) {
    init {
        require(tag.isNotBlank()) { "tag must not be blank" }
    }
}
