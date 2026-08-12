package com.phoneproof.core.reports

import com.phoneproof.core.model.CheckOutcome

/**
 * Renders a report as plain text for sharing.
 *
 * Plain text, not HTML or PDF, because of where this gets sent: WhatsApp, SMS and a paste into a
 * marketplace chat. It has to survive being quoted, forwarded and read on a cheap phone with no
 * attachment preview. PDF is a paid feature and a separate job.
 *
 * Word markers rather than coloured symbols: a red circle conveys nothing once the text is
 * forwarded as a quote, and screen readers announce "PASS" usefully where they announce an emoji
 * as noise.
 */
fun SavedReport.asPlainText(dateLabel: String): String = buildString {
    appendLine("PhoneProof report")
    appendLine("$deviceLabel · $androidLabel")
    appendLine(dateLabel)
    appendLine()

    appendLine(summaryLine())
    appendLine()

    results.forEach { result ->
        appendLine("[${result.outcome.marker}] ${result.title}")
        appendLine("  ${result.headline}")
        result.consequence?.let { appendLine("  $it") }
        result.action?.let { appendLine("  → $it") }
        result.measurements.forEach { m ->
            appendLine("  ${m.label}: ${m.display}")
        }
        appendLine()
    }

    // Named so a seller cannot pass an edited screenshot off as the app's own verdict, and so a
    // buyer who receives one forwarded knows what produced it.
    appendLine("Measured on the device by PhoneProof. Nothing was uploaded.")
}

/** One line a buyer can read out loud, or paste on its own. */
fun SavedReport.summaryLine(): String {
    val parts = buildList {
        if (problemCount > 0) add("$problemCount to check")
        if (unknownCount > 0) add("$unknownCount could not be read")
        if (passCount > 0) add("$passCount fine")
    }
    if (parts.isEmpty()) return "No checks were run."
    return parts.joinToString(", ")
}

private val CheckOutcome.marker: String
    get() = when (this) {
        CheckOutcome.PASS -> "PASS"
        CheckOutcome.CAUTION -> "CHECK"
        CheckOutcome.FAIL -> "FAIL"
        CheckOutcome.UNKNOWN -> "UNKNOWN"
    }
