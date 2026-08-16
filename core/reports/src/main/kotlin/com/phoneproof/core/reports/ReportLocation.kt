package com.phoneproof.core.reports

import java.io.File

/**
 * Where saved reports live, named once.
 *
 * This used to be a string literal in `feature:scan` and another in `feature:reports`, kept honest by
 * a test that asserted the two matched. That was a reasonable trade while there were exactly two
 * copies — a feature module depending on another feature module is how a module graph turns into a
 * knot — but the guided run made a third writer, and a convention held together by counting copies
 * does not survive a third one.
 *
 * Both writers and the reader already depend on this module, so it is the natural home. Takes a
 * `File` rather than a `Context` to keep `core:reports` pure Kotlin, which is what makes its tests run
 * in milliseconds.
 */
const val REPORTS_DIRECTORY_NAME: String = "reports"

fun reportStore(filesDir: File, retain: Int = ReportStore.FREE_TIER_RETAIN): ReportStore =
    ReportStore(File(filesDir, REPORTS_DIRECTORY_NAME), retain = retain)
