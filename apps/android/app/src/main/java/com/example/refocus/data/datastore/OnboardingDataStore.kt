package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATASTORE_NAME = "refocus_onboarding"

private val Context.dataStore by preferencesDataStore(
    name = DATASTORE_NAME
)

class OnboardingDataStore(
    private val context: Context
) {
    private object Keys {
        val COMPLETED = booleanPreferencesKey("completed")
    }

    val completedFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs: Preferences ->
                prefs[Keys.COMPLETED] ?: false
            }

    suspend fun setCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COMPLETED] = completed
        }
    }
}
