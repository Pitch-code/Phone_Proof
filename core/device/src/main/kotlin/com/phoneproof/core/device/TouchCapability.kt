package com.phoneproof.core.device

import android.content.Context
import android.content.pm.PackageManager
import com.phoneproof.core.diagnostics.Diagnostics

/**
 * How many independent touch points this phone says it supports.
 *
 * Android states this as feature flags rather than a number, and the flags are cumulative promises:
 *
 *  - `MULTITOUCH_JAZZHAND` — five or more independent points.
 *  - `MULTITOUCH_DISTINCT` — two points, tracked independently.
 *  - `MULTITOUCH` — two points, but the platform will not promise it can tell them apart.
 *
 * Returned as a number so the pure check can compare it against what the glass actually did. This is the
 * phone's own claim, which is what makes the comparison fair: a handset that advertises two points and
 * delivers two is working exactly as sold, and the app has no business failing it for not being a
 * flagship.
 *
 * Null when the phone claims nothing useful — an honest gap rather than a zero, because zero would read as
 * "no touchscreen".
 */
fun claimedTouchPoints(context: Context): Int? = runCatching {
    val packageManager = context.packageManager
    when {
        packageManager.hasSystemFeature(
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND,
        ) -> 5

        packageManager.hasSystemFeature(
            PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT,
        ) -> 2

        packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> 2

        else -> null
    }
}.onFailure { Diagnostics.error("TouchCapability", "could not read touch features", it) }.getOrNull()
