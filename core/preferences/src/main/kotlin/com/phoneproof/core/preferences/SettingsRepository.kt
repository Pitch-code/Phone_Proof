package com.phoneproof.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.passes.InspectionPass
import com.phoneproof.core.preferences.passes.effectiveEntitlement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "phoneproof")

/**
 * Emits immediately and then once a minute, for as long as anyone is listening.
 *
 * Folded into [SettingsRepository.effectiveEntitlement] so that a pass expiring by the clock is actually
 * noticed. Without it, a screen left open would keep a lapsed pass alive for as long as it stayed open.
 *
 * Emits first, before any delay, so the entitlement is available on the first frame rather than a minute
 * into the screen's life.
 */
private fun everyMinute(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(60_000L)
    }
}

/**
 * The user's choices, on this device only.
 *
 * There is no account and no sync, so this is the whole of the app's persisted state. It stores a
 * theme preference — nothing about the phones that have been tested, and nothing identifying.
 */
class SettingsRepository(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .catch { error ->
            // A corrupt preferences file must not stop the app launching. Fall back to the default
            // and record it, rather than crashing on a cosmetic setting.
            Diagnostics.error(TAG, "reading preferences failed, using defaults", error)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { preferences ->
            val stored = preferences[THEME_KEY]
            // Light when nothing has been chosen. This is the one place that decides it for a fresh
            // install, so the app-wide default lives here rather than being repeated per screen.
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.LIGHT
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        runCatching {
            context.dataStore.edit { it[THEME_KEY] = mode.name }
        }.onFailure { Diagnostics.error(TAG, "saving theme failed", it) }
    }

    /**
     * What this install has paid for.
     *
     * Read from local storage, which is honest about what it is: a switch that decides what to show,
     * not proof of a purchase. It has to be replaced by a verified Play Billing purchase before
     * anything is sold, because a locally stored entitlement is trivially editable on a rooted phone.
     */
    val entitlement: Flow<Entitlement> = context.dataStore.data
        .catch { error ->
            Diagnostics.error(TAG, "reading entitlement failed, treating as free", error)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { preferences ->
            val stored = preferences[ENTITLEMENT_KEY]
            Entitlement.entries.firstOrNull { it.name == stored } ?: Entitlement.FREE
        }

    suspend fun setEntitlement(entitlement: Entitlement) {
        runCatching {
            context.dataStore.edit { it[ENTITLEMENT_KEY] = entitlement.name }
        }.onFailure { Diagnostics.error(TAG, "saving entitlement failed", it) }
    }

    /**
     * The inspection pass running on this phone, if any.
     *
     * Stored as an absolute instant rather than a countdown, so nothing has to be ticking for it to expire.
     * The app can be killed, the phone rebooted, a week can pass, and the answer is still a comparison.
     */
    val inspectionPass: Flow<InspectionPass?> = context.dataStore.data
        .catch { error ->
            Diagnostics.error(TAG, "reading the pass failed, treating as none", error)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { preferences ->
            val code = preferences[PASS_CODE_KEY]
            val expiresAt = preferences[PASS_EXPIRES_KEY]
            if (code.isNullOrBlank() || expiresAt == null) null
            else InspectionPass(code = code, expiresAtEpochMs = expiresAt)
        }

    /** Records a pass the licence server granted. Replaces any previous one. */
    suspend fun saveInspectionPass(pass: InspectionPass) {
        runCatching {
            context.dataStore.edit { preferences ->
                preferences[PASS_CODE_KEY] = pass.code
                preferences[PASS_EXPIRES_KEY] = pass.expiresAtEpochMs
            }
        }.onFailure { Diagnostics.error(TAG, "saving the pass failed", it) }
    }

    /**
     * What this install can actually do right now: what it owns, **or** a pass that is currently running.
     *
     * This is the flow screens should read. [entitlement] alone is what the Google account owns, which is
     * the wrong question on a phone the buyer does not own — and that phone is the whole point of the app.
     *
     * ## Why it re-emits on a timer
     *
     * A pass expires by the clock, and a `Flow` built only from stored values would not notice: someone
     * could leave a screen open past the expiry and keep a paid feature indefinitely. So a minute tick is
     * folded in, which is frequent enough that nobody keeps a meaningful amount of extra time and rare
     * enough to cost nothing. It only runs while something is collecting, which with
     * `collectAsStateWithLifecycle` means only while a screen is on top.
     *
     * ## What this deliberately does not decide
     *
     * **Report retention.** A pass unlocks *measuring*, not storage promises. If it granted unlimited
     * history, then the moment it lapsed the next save would prune back to two and delete reports the buyer
     * made while paid — losing their evidence as a side effect of a clock. Retention therefore stays on
     * [entitlement], and the two call sites that need it read that instead.
     */
    val effectiveEntitlement: Flow<Entitlement> =
        combine(entitlement, inspectionPass, everyMinute()) { owned, pass, _ ->
            effectiveEntitlement(owned, pass, System.currentTimeMillis())
        }

    /**
     * How many scans this install has used.
     *
     * Counts completed scans, not attempts, so a scan that failed to read anything does not consume
     * the allowance — a buyer must never lose one of two chances to a bug in this app.
     *
     * Stored locally, which is what it is: a counter someone with a rooted phone can reset. Fine for
     * a free allowance, and it has to move behind a verified Play purchase before it guards anything
     * that costs money.
     */
    val scansUsed: Flow<Int> = context.dataStore.data
        .catch { error ->
            Diagnostics.error(TAG, "reading scan count failed, treating as none used", error)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { preferences -> preferences[SCANS_USED_KEY] ?: 0 }

    suspend fun recordScanUsed() {
        runCatching {
            context.dataStore.edit { preferences ->
                preferences[SCANS_USED_KEY] = (preferences[SCANS_USED_KEY] ?: 0) + 1
            }
        }.onFailure { Diagnostics.error(TAG, "saving scan count failed", it) }
    }

    /** A shop's own details for the header of a printed report. */
    val shopBranding: Flow<ShopBrandingPreference> = context.dataStore.data
        .catch { error ->
            Diagnostics.error(TAG, "reading branding failed, using none", error)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { preferences ->
            ShopBrandingPreference(
                name = preferences[SHOP_NAME_KEY],
                contact = preferences[SHOP_CONTACT_KEY],
                logoPath = preferences[SHOP_LOGO_KEY],
            )
        }

    suspend fun setShopBranding(name: String?, contact: String?) {
        runCatching {
            context.dataStore.edit { preferences ->
                // Blank is removal rather than an empty string. Storing "" would put an empty line
                // in the report header for anyone who cleared the field.
                if (name.isNullOrBlank()) {
                    preferences.remove(SHOP_NAME_KEY)
                } else {
                    preferences[SHOP_NAME_KEY] = name.trim()
                }
                if (contact.isNullOrBlank()) {
                    preferences.remove(SHOP_CONTACT_KEY)
                } else {
                    preferences[SHOP_CONTACT_KEY] = contact.trim()
                }
            }
        }.onFailure { Diagnostics.error(TAG, "saving branding failed", it) }
    }

    suspend fun setShopLogoPath(path: String?) {
        runCatching {
            context.dataStore.edit { preferences ->
                if (path.isNullOrBlank()) {
                    preferences.remove(SHOP_LOGO_KEY)
                } else {
                    preferences[SHOP_LOGO_KEY] = path
                }
            }
        }.onFailure { Diagnostics.error(TAG, "saving logo path failed", it) }
    }

    private companion object {
        const val TAG = "SettingsRepository"

        /**
         * Stored by name rather than ordinal. An ordinal breaks silently the moment someone
         * reorders the enum, and the failure would be a user's saved theme quietly changing.
         */
        val THEME_KEY = stringPreferencesKey("theme_mode")

        /** Also by name, for the same reason. */
        val ENTITLEMENT_KEY = stringPreferencesKey("entitlement")

        val SHOP_NAME_KEY = stringPreferencesKey("shop_name")
        val SHOP_CONTACT_KEY = stringPreferencesKey("shop_contact")
        val SHOP_LOGO_KEY = stringPreferencesKey("shop_logo_path")
        val SCANS_USED_KEY = intPreferencesKey("scans_used")

        /** The pass a redeemed code granted, and when it stops. See [inspectionPass]. */
        val PASS_CODE_KEY = stringPreferencesKey("pass_code")
        val PASS_EXPIRES_KEY = longPreferencesKey("pass_expires_at")
    }
}

/**
 * Stored branding, kept separate from `core:reports`' own `ShopBranding`.
 *
 * `core:preferences` deliberately does not depend on `core:reports` — persistence should not pull in
 * the report model — so the two are mapped where they meet, in the reports feature.
 */
data class ShopBrandingPreference(
    val name: String? = null,
    val contact: String? = null,
    val logoPath: String? = null,
)
