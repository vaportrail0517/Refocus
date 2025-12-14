package com.example.refocus.data.repository

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionMode
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.db.entity.SuggestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 「提案（やりたいこと）」に関するリポジトリ。
 * バックエンドは Room の suggestions テーブル。
 */
class SuggestionsRepository(
    private val suggestionDao: SuggestionDao,
    private val timeSource: TimeSource,
) {

    /**
     * 複数のやりたいことをそのまま流す Flow。
     * 空タイトル（空白のみ含む）ものは除外しておく。
     */
    fun observeSuggestions(): Flow<List<Suggestion>> =
        suggestionDao.observeAll()
            .map { entities ->
                entities
                    .map { it.toModel() }
                    .filter { it.title.isNotBlank() }
            }

    /**
     * ワンショットで suggestions を取りたい箇所用（例: Overlay）。
     * Flow を立ち上げずに一回だけ Room を叩く。
     */
    suspend fun getSuggestionsSnapshot(): List<Suggestion> =
        suggestionDao.getAll()
            .map { it.toModel() }
            .filter { it.title.isNotBlank() }

    /**
     * 新しい Suggestion を追加して返す。
     * title は trim した上で、空白のみ/空文字は禁止（= 保存しない）。
     */
    suspend fun addSuggestion(
        title: String,
        timeSlot: SuggestionTimeSlot,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ): Suggestion {
        val normalizedTitle = normalizeTitle(title)
        require(normalizedTitle.isNotEmpty()) { "Suggestion title must not be blank." }
        val now = timeSource.nowMillis()
        val entity = SuggestionEntity(
            id = 0L,
            title = normalizedTitle,
            createdAtMillis = now,
            kind = SuggestionMode.Generic.name,
            timeSlot = timeSlot.name,
            durationTag = durationTag.name,
            priority = priority.name,
        )
        val newId = suggestionDao.insert(entity)
        return entity.copy(id = newId).toModel()
    }

    suspend fun updateSuggestion(id: Long, newTitle: String) {
        val normalizedTitle = normalizeTitle(newTitle)
        if (normalizedTitle.isEmpty()) return
        suggestionDao.updateTitle(id = id, title = normalizedTitle)
    }

    suspend fun updateSuggestionTags(
        id: Long,
        timeSlot: SuggestionTimeSlot,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) {
        suggestionDao.updateTags(
            id = id,
            timeSlot = timeSlot.name,
            durationTag = durationTag.name,
            priority = priority.name,
        )
    }

    suspend fun deleteSuggestion(id: Long) {
        suggestionDao.deleteById(id)
    }

    private fun normalizeTitle(title: String): String = title.trim()

    private fun SuggestionEntity.toModel(): Suggestion {
        val kindEnum = runCatching { SuggestionMode.valueOf(kind) }
            .getOrDefault(SuggestionMode.Generic)
        val slotEnum = runCatching { SuggestionTimeSlot.valueOf(timeSlot) }
            .getOrDefault(SuggestionTimeSlot.Anytime)
        val durationEnum = runCatching { SuggestionDurationTag.valueOf(durationTag) }
            .getOrDefault(SuggestionDurationTag.Medium)
        val priorityEnum = runCatching { SuggestionPriority.valueOf(priority) }
            .getOrDefault(SuggestionPriority.Normal)
        return Suggestion(
            id = id,
            title = title,
            createdAtMillis = createdAtMillis,
            kind = kindEnum,
            timeSlot = slotEnum,
            durationTag = durationEnum,
            priority = priorityEnum,
        )
    }
}
