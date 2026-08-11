package com.phoneproof.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.SettingsRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    versionName: String,
    versionCode: Long,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { SettingsRepository(context) }

    val state by remember(repository, versionName, versionCode) {
        repository.themeMode.map { mode ->
            SettingsUiState(
                themeMode = mode,
                versionName = versionName,
                versionCode = versionCode,
                // Flipped on when Play Billing is wired and the app ships through Play. Until then
                // the UI says so instead of offering a button that cannot work.
                billingAvailable = false,
            )
        }
    }.collectAsStateWithLifecycle(initialValue = SettingsUiState(versionName = versionName, versionCode = versionCode))

    SettingsScreen(
        state = state,
        onThemeSelected = { mode ->
            scope.launch { repository.setThemeMode(mode) }
            Diagnostics.info(TAG, "theme set to ${mode.name}")
        },
        onOpenPrivacyPolicy = { openUrl(context, PRIVACY_POLICY_URL) },
        onShareApp = { shareApp(context) },
        onOpenDiagnostics = onOpenDiagnostics,
        onChoosePlan = { plan ->
            // No purchase flow yet, so record the intent rather than pretending. When billing lands
            // this becomes the launch point, and the log already shows which plan people tap.
            Diagnostics.info(TAG, "plan tapped: ${plan.productId} (billing unavailable)")
        },
        modifier = modifier,
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure { Diagnostics.error(TAG, "could not open $url", it) }
}

private fun shareApp(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Checking a used phone? PhoneProof finds the faults before you pay for them. " +
                    STORE_URL,
            )
        }
        context.startActivity(Intent.createChooser(intent, "Share PhoneProof"))
    }.onFailure { Diagnostics.error(TAG, "share failed", it) }
}

private const val TAG = "SettingsRoute"

/** Hosted on GitHub Pages from docs/ in this repository, so there is no server to run. */
private const val PRIVACY_POLICY_URL = "https://pitch-code.github.io/Phone_Proof/privacy.html"

/** Points at the repository until the app has a Play listing. */
private const val STORE_URL = "https://github.com/Pitch-code/Phone_Proof"
