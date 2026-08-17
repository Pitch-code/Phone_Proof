package com.phoneproof.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this phone's owner wants animation at all.
 *
 * Android exposes "remove animations" as `ANIMATOR_DURATION_SCALE`, and a person who has switched it
 * off has usually done so for vestibular or photosensitivity reasons. Every looping animation in this
 * app is required to honour it — the guide diagrams already did, privately, and this is that check
 * moved somewhere the rest of the app can reach.
 *
 * Read once and remembered: it is a settings lookup, not something to do on every frame.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.animationsEnabled() }
}

/** Defaults to true, because failing to read the setting must not silently disable motion for everyone. */
internal fun Context.animationsEnabled(): Boolean = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}.getOrDefault(true)
