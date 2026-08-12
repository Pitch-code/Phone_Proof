package com.phoneproof.feature.guide

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun GuideRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // rememberSaveable, so rotating the phone mid-step does not collapse the card being read. Easy
    // to get wrong and irritating in exactly the situation this screen is used in: one hand on the
    // phone, someone waiting.
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    val animate = remember(context) { context.animationsEnabled() }

    GuideScreen(
        steps = GuideSteps,
        expandedId = expandedId,
        animate = animate,
        onToggle = { id -> expandedId = if (expandedId == id) null else id },
        modifier = modifier,
    )
}

/**
 * Whether the system has animations switched on.
 *
 * Read from the platform rather than offered as an in-app toggle. Someone who has turned animations
 * off system-wide — for motion sensitivity, or because the phone is slow — has already answered this
 * question, and asking again in every app is how that setting gets ignored.
 *
 * Defaults to true when the value cannot be read: the diagrams are the point of this screen, and
 * failing to read one setting is not a reason to show eight static drawings to everyone.
 */
private fun Context.animationsEnabled(): Boolean = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}.getOrDefault(true)
