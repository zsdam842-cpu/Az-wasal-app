
package com.wasal.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(
    name = "wasal_auth"
)

class AuthDataStore(private val context: Context) {

    // يتيح للمستودعات استخدام سياق التطبيق للتخزين المحلي.
    internal val appContext: Context
        get() = context.applicationContext

    private val tokenKey =
        stringPreferencesKey("access_token")

    private val userIdKey =
        stringPreferencesKey("user_id")

    private val refreshTokenKey =
        stringPreferencesKey("refresh_token")

    val accessToken: Flow<String?> =
        context.authDataStore.data.map { preferences ->
            preferences[tokenKey]
        }

    val userId: Flow<String?> =
        context.authDataStore.data.map { preferences ->
            preferences[userIdKey]
        }

    val refreshToken: Flow<String?> =
        context.authDataStore.data.map { preferences ->
            preferences[refreshTokenKey]
        }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String
    ) {
        context.authDataStore.edit { preferences ->
            preferences[tokenKey] = accessToken
            preferences[refreshTokenKey] = refreshToken
            preferences[userIdKey] = userId
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
