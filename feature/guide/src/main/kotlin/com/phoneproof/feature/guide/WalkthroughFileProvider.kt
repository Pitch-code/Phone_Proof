package com.phoneproof.feature.guide

import androidx.core.content.FileProvider

/**
 * A `FileProvider` of its own, purely so two of them can coexist.
 *
 * `feature:reports` already declares one for sharing a report PDF. Declaring a second with the same
 * `android:name` fails the manifest merge outright — the merger finds two `<provider>` entries for
 * `androidx.core.content.FileProvider` with different authorities and different path files and refuses to
 * choose. Subclassing gives this one a distinct class name, which is the documented way out.
 *
 * The alternative was to reuse the reports provider and add `walkthrough/` to its paths file, and that is
 * worse for a reason worth writing down: its paths are scoped to `cacheDir/shared`, deliberately, so that
 * sharing one report cannot expose every report. Widening that file to cover walkthrough photographs would
 * mean one provider whose grant reaches two unrelated kinds of private data. Two narrow providers are
 * safer than one broad one.
 */
class WalkthroughFileProvider : FileProvider()
