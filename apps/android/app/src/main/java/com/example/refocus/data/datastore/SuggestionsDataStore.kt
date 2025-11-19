package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.refocus.core.model.Suggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATASTORE_NAME = "refocus_suggestions"

private val Context.dataStore by preferencesDataStore(
    name = DATASTORE_NAME
)

/**
 * とりあえず「最新の提案を1件だけ」保存する DataStore。
 * 将来リスト化したくなったら Room に切り替える予定。
 */
class SuggestionsDataStore(
    private val context: Context
) {
    private object Keys {
        val TITLE = stringPreferencesKey("suggestion_title")
        val CREATED_AT = longPreferencesKey("suggestion_created_at")
    }

    val suggestionFlow: Flow<Suggestion?> = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs: Preferences ->
            val title = prefs[Keys.TITLE] ?: return@map null
            if (title.isBlank()) return@map null
            val createdAt = prefs[Keys.CREATED_AT] ?: 0L
            Suggestion(
                title = title,
                createdAtMillis = createdAt
            )
        }

    suspend fun setSuggestion(
        title: String,
        createdAtMillis: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TITLE] = title
            prefs[Keys.CREATED_AT] = createdAtMillis
        }
    }

    suspend fun clearSuggestion() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.TITLE)
            prefs.remove(Keys.CREATED_AT)
        }
    }
}
