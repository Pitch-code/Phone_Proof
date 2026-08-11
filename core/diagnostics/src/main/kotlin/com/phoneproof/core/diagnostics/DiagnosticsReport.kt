package com.phoneproof.core.diagnostics

/**
 * Facts about the build and handset that make a log actionable.
 *
 * Everything here is either the app's own build info or a coarse hardware descriptor. There is no
 * device identifier and no account data — partly by choice, and partly because Android has not let
 * third-party apps read IMEI or serial since version 10.
 */
data class DiagnosticsEnvironment(
    val appVersion: String,
    val versionCode: Long,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val screen: String? = null,
    val locale: String? = null,
)

/**
 * Renders the log as plain text meant to be copied into a chat message.
 *
 * Plain text rather than a file on purpose: sharing a file needs a `FileProvider`, an extra
 * permission surface and a working share target, and every one of those is another thing that can
 * fail on the exact device that is already misbehaving. Text always works.
 */
object DiagnosticsReport {

    fun format(
        environment: DiagnosticsEnvironment,
        entries: List<DiagEntry>,
        droppedCount: Int = 0,
        formatTimestamp: (Long) -> String = { it.toString() },
    ): String = buildString {
        appendLine("PhoneProof diagnostics")
        appendLine("----------------------")
        appendLine("app       ${environment.appVersion} (${environment.versionCode})")
        appendLine("device    ${environment.manufacturer} ${environment.model}")
        appendLine("android   ${environment.androidRelease} (API ${environment.sdkInt})")
        environment.screen?.let { appendLine("screen    $it") }
        environment.locale?.let { appendLine("locale    $it") }
        appendLine("entries   ${entries.size}")
        if (droppedCount > 0) {
            // Announced rather than hidden: a truncated log that looks complete sends the reader
            // looking in the wrong place.
            appendLine("dropped   $droppedCount older entries were discarded")
        }
        appendLine()

        if (entries.isEmpty()) {
            appendLine("No events recorded.")
            return@buildString
        }

        entries.forEach { entry ->
            appendLine(
                "${formatTimestamp(entry.timestampMillis)}  " +
                    "${entry.level.label.padEnd(5)}  ${entry.tag}  ${entry.message}",
            )
            entry.stackTrace?.lines()?.forEach { appendLine("    $it") }
        }
    }.trimEnd() + "\n"
}
