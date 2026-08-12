package com.phoneproof.feature.reports

import android.content.Context
import com.phoneproof.core.reports.ReportStore
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The one place the report directory is named.
 *
 * Both the scan (which writes) and the history screen (which reads) have to agree on this path, and
 * a second literal somewhere else would mean saved reports silently never appearing.
 */
fun reportStore(context: Context, retain: Int = ReportStore.FREE_TIER_RETAIN): ReportStore =
    ReportStore(File(context.filesDir, REPORTS_DIRECTORY), retain = retain)

const val REPORTS_DIRECTORY: String = "reports"

/**
 * A date a person reads, in their own locale and time zone.
 *
 * Uses the platform's own formatter rather than a fixed pattern so a buyer in India sees the order
 * and clock they expect. Passed into the screens as a lambda, which keeps them free of a
 * locale-dependent call that would make a screenshot render differently depending on the machine.
 */
fun formatReportDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
