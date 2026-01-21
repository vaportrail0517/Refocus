package com.example.refocus.domain.repository

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionAction
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import kotlinx.coroutines.flow.Flow

/**
 * 「提案（やりたいこと）」に関する永続化の抽象。
 */
interface SuggestionsRepository {
    fun observeSuggestions(): Flow<List<Suggestion>>

    suspend fun getSuggestionsSnapshot(): List<Suggestion>

    suspend fun addSuggestion(
        title: String,
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
        action: SuggestionAction = SuggestionAction.None,
    ): Suggestion

    suspend fun updateSuggestion(
        id: Long,
        newTitle: String,
    )

    suspend fun updateSuggestionTags(
        id: Long,
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    )

    suspend fun updateSuggestionAction(
        id: Long,
        action: SuggestionAction,
    )

    suspend fun deleteSuggestion(id: Long)
}
