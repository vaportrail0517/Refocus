package com.example.refocus.data.repository

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionKind
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
     * 空タイトルのものは除外しておく（編集中プレースホルダは含めない）。
     */
    fun observeSuggestions(): Flow<List<Suggestion>> =
        suggestionDao.observeAll()
            .map { entities ->
                entities
                    .map { it.toModel() }
            }

    /**
     * 互換性のための helper。
     * 先頭の1件だけを返す（Overlay から利用）。
     */
    fun observeSuggestion(): Flow<Suggestion?> =
        observeSuggestions().map { list -> list.firstOrNull() }

    /**
     * 新しい Suggestion を追加して返す。
     * ここでは title をそのまま保存する（空文字でも保存される）。
     * UI 側で空のまま確定されたときは削除する。
     */
    suspend fun addSuggestion(
        title: String,
        timeSlot: SuggestionTimeSlot,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ): Suggestion {
        val now = timeSource.nowMillis()
        val entity = SuggestionEntity(
            id = 0L,
            title = title,
            createdAtMillis = now,
            kind = SuggestionKind.Generic.name,
            timeSlot = timeSlot.name,
            durationTag = durationTag.name,
            priority = priority.name,
        )
        val newId = suggestionDao.insert(entity)
        return entity.copy(id = newId).toModel()
    }

    suspend fun updateSuggestion(id: Long, newTitle: String) {
        val current = suggestionDao.getAll().firstOrNull { it.id == id } ?: return
        val updated = current.copy(title = newTitle)
        suggestionDao.update(updated)
    }

    suspend fun updateSuggestionTags(
        id: Long,
        timeSlot: SuggestionTimeSlot,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) {
        val current = suggestionDao.getAll().firstOrNull { it.id == id } ?: return
        val updated = current.copy(
            timeSlot = timeSlot.name,
            durationTag = durationTag.name,
            priority = priority.name
        )
        suggestionDao.update(updated)
    }

    suspend fun deleteSuggestion(id: Long) {
        val current = suggestionDao.getAll().firstOrNull { it.id == id } ?: return
        suggestionDao.delete(current)
    }

    private fun SuggestionEntity.toModel(): Suggestion {
        val kindEnum = runCatching { SuggestionKind.valueOf(kind) }
            .getOrDefault(SuggestionKind.Generic)
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
