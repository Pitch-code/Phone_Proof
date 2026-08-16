package com.phoneproof.feature.reports

import java.text.DateFormat
import java.util.Date

/**
 * A date a person reads, in their own locale and time zone.
 *
 * Uses the platform's own formatter rather than a fixed pattern so a buyer in India sees the order
 * and clock they expect. Passed into the screens as a lambda, which keeps them free of a
 * locale-dependent call that would make a screenshot render differently depending on the machine.
 */
fun formatReportDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
