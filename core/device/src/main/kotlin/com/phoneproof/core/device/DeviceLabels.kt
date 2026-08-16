package com.phoneproof.core.device

import android.os.Build

/**
 * How this handset is named in a saved report.
 *
 * Lived privately in `feature:scan` while the scan was the only thing that wrote a report. The guided
 * run writes one too, and copying nine lines of string-tidying into a second module is how two reports
 * of the same phone end up labelled differently.
 *
 * "realme RMX5110", or just the model when the manufacturer's name is already the start of it —
 * without that check, a Samsung comes out as "samsung Samsung Galaxy A15".
 */
fun deviceLabel(): String {
    val manufacturer = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    return when {
        model.isEmpty() -> manufacturer.ifEmpty { "Unknown phone" }
        manufacturer.isEmpty() -> model
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "$manufacturer $model"
    }
}

/**
 * "Android 16 (API 36)".
 *
 * Both halves are kept. The marketing version is the one a buyer recognises; the API level is the one
 * that explains why a check came back UNKNOWN, and a report read months later needs to answer that
 * without anyone having to remember which Android version introduced what.
 */
fun androidLabel(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
