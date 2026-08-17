package com.phoneproof.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.Entitlement
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { SettingsRepository(context) }

    val state by remember(repository, versionName, versionCode) {
        combine(
            repository.themeMode,
            repository.entitlement,
            repository.shopBranding,
            repository.scansUsed,
        ) { mode, entitlement, branding, scansUsed ->
            SettingsUiState(
                themeMode = mode,
                versionName = versionName,
                versionCode = versionCode,
                // Flipped on when Play Billing is wired and the app ships through Play. Until then
                // the UI says so instead of offering a button that cannot work.
                billingAvailable = false,
                entitlement = entitlement,
                // Derived from the entitlement rather than left null, which is what it was since the
                // Settings screen was written. The render of the Shop tier made the consequence
                // obvious: entitlement was SHOP while the Shop card still read "Unavailable",
                // telling a paying customer they had not bought the thing they were using.
                ownedPlan = when (entitlement) {
                    Entitlement.PREMIUM -> PremiumPlan.PREMIUM
                    Entitlement.SHOP -> PremiumPlan.SHOP
                    Entitlement.FREE -> null
                },
                shopName = branding.name,
                shopContact = branding.contact,
                shopLogoPath = branding.logoPath,
                freeScansLeft = if (entitlement.hasUnlimitedScans) {
                    null
                } else {
                    (Entitlement.FREE_SCAN_LIMIT - scansUsed).coerceAtLeast(0)
                },
            )
        }
    }.collectAsStateWithLifecycle(initialValue = SettingsUiState(versionName = versionName, versionCode = versionCode))

    // The text the shop is typing, owned here — not read back out of DataStore.
    //
    // This is the fix for two reported bugs that were the same bug. Both branding fields took their
    // `value` straight from the persisted flow, so every keystroke went: onValueChange -> launch ->
    // dataStore.edit (a serialised disk write) -> data emits -> combine -> recomposition -> the field's
    // value is replaced. Each replacement resets the text field's buffer and restarts the IME, which
    // is why characters landed at stale offsets and appeared to swap places, and why the keyboard
    // dropped back to its letters page after every digit.
    //
    // Two further faults fell out of the same round trip. The repository `trim()`s on write, so a
    // trailing space came back deleted and the cursor jumped a character back — a space could never
    // be typed in the middle of "Krishna Mobiles". And each callback rebuilt *both* fields from the
    // last collected state, so typing a name while a contact write was still in flight could revert
    // the other field.
    //
    // Null means "not touched this session", so the persisted value shows. After the first keystroke
    // the local value wins and DataStore becomes write-only. rememberSaveable, so a rotation or the
    // keyboard resizing the window does not discard half-typed text.
    var typedShopName by rememberSaveable { mutableStateOf<String?>(null) }
    var typedShopContact by rememberSaveable { mutableStateOf<String?>(null) }

    val shopName = typedShopName ?: state.shopName
    val shopContact = typedShopContact ?: state.shopContact

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
        // The branding fields come from local state; everything else from the persisted flow.
        state = state.copy(shopName = shopName, shopContact = shopContact),
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
            // Local state first and synchronously, so the field redraws from the keystroke rather
            // than from the disk. The write still happens per keystroke, which is cheap enough for
            // two short strings, but nothing waits on it and nothing reads its result back.
            typedShopName = name
            scope.launch { repository.setShopBranding(name, shopContact) }
        },
        onShopContactChanged = { contact ->
            typedShopContact = contact
            scope.launch { repository.setShopBranding(shopName, contact) }
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
