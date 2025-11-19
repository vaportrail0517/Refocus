package com.example.refocus.data.repository

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.datastore.SuggestionsDataStore
import kotlinx.coroutines.flow.Flow

/**
 * 「提案（やりたいこと）」に関するリポジトリ。
 * 今は DataStore バックエンドだが、将来 Room に差し替えても
 * ViewModel から見えるインターフェースは変えない想定。
 */
class SuggestionsRepository(
    private val dataStore: SuggestionsDataStore,
    private val timeSource: TimeSource,
) {

    fun observeSuggestion(): Flow<Suggestion?> = dataStore.suggestionFlow

    suspend fun setSuggestion(title: String) {
        val now = timeSource.nowMillis()
        dataStore.setSuggestion(
            title = title,
            createdAtMillis = now
        )
    }

    suspend fun clearSuggestion() {
        dataStore.clearSuggestion()
    }
}
