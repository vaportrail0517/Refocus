package com.example.refocus.data.repository

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionMode
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.db.entity.SuggestionEntity
import com.example.refocus.domain.repository.SuggestionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 「提案（やりたいこと）」に関するリポジトリ。
 * バックエンドは Room の suggestions テーブル。
 */
class SuggestionsRepositoryImpl(
    private val suggestionDao: SuggestionDao,
    private val timeSource: TimeSource,
) : SuggestionsRepository {

    /**
     * 複数のやりたいことをそのまま流す Flow。
     * 空タイトル（空白のみ含む）ものは除外しておく。
     */
    override fun observeSuggestions(): Flow<List<Suggestion>> =
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
    override suspend fun getSuggestionsSnapshot(): List<Suggestion> =
        suggestionDao.getAll()
            .map { it.toModel() }
            .filter { it.title.isNotBlank() }

    /**
     * 新しい Suggestion を追加して返す。
     * title は trim した上で、空白のみ/空文字は禁止（= 保存しない）。
     */
    override suspend fun addSuggestion(
        title: String,
        timeSlots: Set<SuggestionTimeSlot>,
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
            timeSlots = serializeTimeSlots(timeSlots),
            durationTag = durationTag.name,
            priority = priority.name,
        )
        val newId = suggestionDao.insert(entity)
        return entity.copy(id = newId).toModel()
    }

    override suspend fun updateSuggestion(id: Long, newTitle: String) {
        val normalizedTitle = normalizeTitle(newTitle)
        if (normalizedTitle.isEmpty()) return
        suggestionDao.updateTitle(id = id, title = normalizedTitle)
    }

    override suspend fun updateSuggestionTags(
        id: Long,
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) {
        suggestionDao.updateTags(
            id = id,
            timeSlots = serializeTimeSlots(timeSlots),
            durationTag = durationTag.name,
            priority = priority.name,
        )
    }

    override suspend fun deleteSuggestion(id: Long) {
        suggestionDao.deleteById(id)
    }

    private fun normalizeTitle(title: String): String = title.trim()

    /**
     * timeSlots 正規化ルール:
     * - 空なら {Anytime}
     * - Anytime が含まれていたら {Anytime}
     */
    private fun normalizeTimeSlots(slots: Set<SuggestionTimeSlot>): Set<SuggestionTimeSlot> {
        if (slots.isEmpty()) return setOf(SuggestionTimeSlot.Anytime)
        if (slots.contains(SuggestionTimeSlot.Anytime)) return setOf(SuggestionTimeSlot.Anytime)
        return slots
    }

    /**
     * 例: "Morning|Afternoon|Night"
     */
    private fun serializeTimeSlots(slots: Set<SuggestionTimeSlot>): String {
        val normalized = normalizeTimeSlots(slots)
        return normalized.joinToString(separator = "|") { it.name }
    }

    private fun deserializeTimeSlots(raw: String): Set<SuggestionTimeSlot> {
        val tokens = raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val parsed = tokens.mapNotNull { token ->
            runCatching { SuggestionTimeSlot.valueOf(token) }.getOrNull()
        }.toSet()
        return normalizeTimeSlots(parsed)
    }

    private fun SuggestionEntity.toModel(): Suggestion {
        val kindEnum = runCatching { SuggestionMode.valueOf(kind) }
            .getOrDefault(SuggestionMode.Generic)
        val durationEnum = runCatching { SuggestionDurationTag.valueOf(durationTag) }
            .getOrDefault(SuggestionDurationTag.Medium)
        val priorityEnum = runCatching { SuggestionPriority.valueOf(priority) }
            .getOrDefault(SuggestionPriority.Normal)
        return Suggestion(
            id = id,
            title = title,
            createdAtMillis = createdAtMillis,
            kind = kindEnum,
            timeSlots = deserializeTimeSlots(timeSlots),
            durationTag = durationEnum,
            priority = priorityEnum,
        )
    }
}
