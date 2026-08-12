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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.SettingsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsRoute(
    versionName: String,
    versionCode: Long,
    onOpenDiagnostics: () -> Unit,
    /** True only for a debug build. See [SettingsUiState.showTestingControls]. */
    showTestingControls: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { SettingsRepository(context) }

    val state by remember(repository, versionName, versionCode, showTestingControls) {
        combine(
            repository.themeMode,
            repository.entitlement,
            repository.shopBranding,
        ) { mode, entitlement, branding ->
            SettingsUiState(
                themeMode = mode,
                versionName = versionName,
                versionCode = versionCode,
                // Flipped on when Play Billing is wired and the app ships through Play. Until then
                // the UI says so instead of offering a button that cannot work.
                billingAvailable = false,
                entitlement = entitlement,
                shopName = branding.name,
                shopContact = branding.contact,
                shopLogoPath = branding.logoPath,
                showTestingControls = showTestingControls,
            )
        }
    }.collectAsStateWithLifecycle(initialValue = SettingsUiState(versionName = versionName, versionCode = versionCode))

    // PickVisualMedia rather than an open-document intent or a storage permission. It runs in the
    // system photo picker, so the app never gains access to the gallery — only to the one image the
    // shop chose. On a permission-free app this matters: asking for storage access to place a logo
    // would be wildly disproportionate.
    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = copyLogoIntoAppStorage(context, uri)
                repository.setShopLogoPath(path)
                Diagnostics.info(TAG, "shop logo set: ${path != null}")
            }
        }
    }

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
        onShopNameChanged = { name ->
            scope.launch { repository.setShopBranding(name, state.shopContact) }
        },
        onShopContactChanged = { contact ->
            scope.launch { repository.setShopBranding(state.shopName, contact) }
        },
        onPickLogo = {
            logoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onRemoveLogo = {
            scope.launch {
                // The stored copy is deleted, not just forgotten. Leaving an orphaned image in app
                // storage after someone asked to remove their logo would be careless with a file
                // they explicitly took back.
                state.shopLogoPath?.let { runCatching { File(it).delete() } }
                repository.setShopLogoPath(null)
            }
        },
        onEntitlementSelected = { tier ->
            scope.launch { repository.setEntitlement(tier) }
            Diagnostics.info(TAG, "entitlement set to ${tier.name} (testing control)")
        },
        modifier = modifier,
    )
}

/**
 * Copies a picked image into app storage and returns its path.
 *
 * Copied rather than referenced by URI. A content URI from the photo picker is a temporary grant: it
 * stops working after a restart, and the shop's logo would silently vanish from their reports days
 * later with nothing to explain it.
 *
 * @return the new path, or null if the copy failed.
 */
private suspend fun copyLogoIntoAppStorage(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, "branding").apply { mkdirs() }
            val target = File(directory, "shop-logo.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.onFailure { Diagnostics.error(TAG, "copying the logo failed", it) }.getOrNull()
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
