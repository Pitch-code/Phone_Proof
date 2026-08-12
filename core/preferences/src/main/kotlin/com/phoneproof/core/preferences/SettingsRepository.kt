package com.phoneproof.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.diagnostics.Diagnostics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "phoneproof")

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
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
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
