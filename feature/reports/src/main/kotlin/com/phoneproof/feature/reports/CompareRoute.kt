package com.phoneproof.feature.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.phoneproof.core.reports.Comparison
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.compareReports

/**
 * Comparison.
 *
 * The most recent report is the left-hand side automatically, because the phone someone just tested
 * is the one they are deciding about. They pick what to compare it against.
 */
@Composable
fun CompareRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { reportStore(context) }

    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }

    val reports by produceState(initialValue = emptyList<SavedReport>(), pickedId) {
        value = runCatching { store.list() }.getOrDefault(emptyList())
    }

    val newest = reports.firstOrNull()
    val other = reports.firstOrNull { it.id == pickedId }

    val comparison: Comparison? = if (newest != null && other != null && newest.id != other.id) {
        compareReports(left = newest, right = other)
    } else {
        null
    }

    CompareScreen(
        comparison = comparison,
        // Everything except the newest, which is already the left-hand side. Offering it as the
        // right-hand side too would let someone compare a phone with itself.
        candidates = reports.drop(1),
        onPick = { pickedId = it },
        modifier = modifier,
    )
}
