package com.phoneproof.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phoneproof.core.billing.BillingProducts
import com.phoneproof.core.billing.EntitlementSync
import com.phoneproof.core.billing.PlayBilling
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
    onOpenRedeem: () -> Unit,
    /** True when the buyer arrived from the "tap here to see the plans" line on Home. */
    focusPlans: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { SettingsRepository(context) }

    // Billing, and the three pieces of state the screen needs from it.
    //
    // `connected` is what decides whether a buy button is offered at all: on a sideloaded build Play
    // cannot answer, and a button that opens nothing is worse than a sentence explaining why.
    val billing = remember(context) { PlayBilling(context) }
    val connected by billing.connected.collectAsStateWithLifecycle()
    var prices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pending by remember { mutableStateOf<List<String>>(emptyList()) }

    // Prices come from Play, never from the constants in PremiumPlan: they vary by country, tax and any
    // promotion, and a hardcoded price disagreeing with the checkout sheet is a policy problem as well as
    // a support burden.
    LaunchedEffect(billing) {
        if (billing.connect()) {
            prices = BillingProducts.onSale.mapNotNull { id ->
                billing.priceOf(id)?.let { id to it }
            }.toMap()
        }
        pending = EntitlementSync(billing, repository).sync()
    }

    // A purchase completing, or a pending payment settling later, takes the same path as a cold start.
    val update by billing.purchaseUpdates.collectAsStateWithLifecycle()
    LaunchedEffect(update) {
        if (update != null) pending = EntitlementSync(billing, repository).sync()
    }

    val state by remember(repository, versionName, versionCode, connected, prices, pending) {
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
                // True only when Play actually answered. False on a sideloaded build, on a device with
                // no Play Store and when offline, and the UI then says so rather than offering a button
                // that cannot work.
                billingAvailable = connected,
                playPrices = prices,
                pendingProductIds = pending,
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
        onOpenRedeem = onOpenRedeem,
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
        focusPlans = focusPlans,
        onChoosePlan = { plan ->
            // Play owns the checkout sheet, the price and the payment method. The result does not come
            // back from here — it arrives on purchaseUpdates, which is why a pending UPI payment is
            // handled by exactly the same code as a completed card payment.
            val activity = context as? Activity
            if (activity == null) {
                Diagnostics.warn(TAG, "no activity to launch checkout from")
            } else {
                scope.launch { billing.launchPurchase(activity, plan.productId) }
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
