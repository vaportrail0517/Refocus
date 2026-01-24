package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATASTORE_NAME = "refocus_hidden_apps"

private val Context.dataStore by preferencesDataStore(
    name = DATASTORE_NAME,
)

class HiddenAppsDataStore(
    private val context: Context,
) {
    private object Keys {
        val HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
    }

    val hiddenPackagesFlow: Flow<Set<String>> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.map { prefs: Preferences ->
                prefs[Keys.HIDDEN_PACKAGES] ?: emptySet()
            }

    suspend fun updateHiddenApps(newHiddenApps: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDDEN_PACKAGES] = newHiddenApps
        }
    }
}
