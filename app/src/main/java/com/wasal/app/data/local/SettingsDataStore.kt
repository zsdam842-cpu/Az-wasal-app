package com.wasal.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(
    name = "wasal_settings"
)

class SettingsDataStore(
    private val context: Context
) {
    private val notificationsKey =
        booleanPreferencesKey("notifications_enabled")

    private val darkModeKey =
        booleanPreferencesKey("dark_mode")

    private val useSystemThemeKey =
        booleanPreferencesKey("use_system_theme")

    val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[notificationsKey] ?: true
        }

    val darkMode: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[darkModeKey] ?: true
        }

    val useSystemTheme: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[useSystemThemeKey] ?: false
        }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[notificationsKey] = enabled
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[darkModeKey] = enabled
        }
    }

    suspend fun setUseSystemTheme(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[useSystemThemeKey] = enabled
        }
    }
}
