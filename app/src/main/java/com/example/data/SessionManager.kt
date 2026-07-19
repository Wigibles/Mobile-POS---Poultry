package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

// Device-local login session. Deliberately NOT synced to Firestore — each device's
// logged-in user is independent, unlike the shared PINs in AuthSettings.
class SessionManager(private val context: Context) {

    private val roleKey = stringPreferencesKey("role")
    private val cashierNameKey = stringPreferencesKey("cashier_name")

    val sessionFlow: Flow<Pair<Role?, String?>> = context.sessionDataStore.data.map { prefs ->
        val role = prefs[roleKey]?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        role to prefs[cashierNameKey]
    }

    suspend fun saveSession(role: Role, cashierName: String?) {
        context.sessionDataStore.edit { prefs ->
            prefs[roleKey] = role.name
            if (cashierName != null) prefs[cashierNameKey] = cashierName else prefs.remove(cashierNameKey)
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
