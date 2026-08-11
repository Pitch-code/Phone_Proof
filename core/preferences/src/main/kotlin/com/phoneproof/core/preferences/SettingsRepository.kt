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

    private companion object {
        const val TAG = "SettingsRepository"

        /**
         * Stored by name rather than ordinal. An ordinal breaks silently the moment someone
         * reorders the enum, and the failure would be a user's saved theme quietly changing.
         */
        val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}
