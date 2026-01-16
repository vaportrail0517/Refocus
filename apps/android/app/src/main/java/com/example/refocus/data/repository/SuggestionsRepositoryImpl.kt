package com.example.refocus.data.repository

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionAction
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
        suggestionDao
            .observeAll()
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
        suggestionDao
            .getAll()
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
        action: SuggestionAction,
    ): Suggestion {
        val normalizedTitle = normalizeTitle(title)
        require(normalizedTitle.isNotEmpty()) { "Suggestion title must not be blank." }
        val now = timeSource.nowMillis()
        val (actionType, actionValue, actionDisplay) = serializeAction(action)
        val entity =
            SuggestionEntity(
                id = 0L,
                title = normalizedTitle,
                createdAtMillis = now,
                kind = SuggestionMode.Generic.name,
                timeSlots = serializeTimeSlots(timeSlots),
                durationTag = durationTag.name,
                priority = priority.name,
                actionType = actionType,
                actionValue = actionValue,
                actionDisplay = actionDisplay,
            )
        val newId = suggestionDao.insert(entity)
        return entity.copy(id = newId).toModel()
    }

    override suspend fun updateSuggestion(
        id: Long,
        newTitle: String,
    ) {
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

    override suspend fun updateSuggestionAction(
        id: Long,
        action: SuggestionAction,
    ) {
        val (type, value, display) = serializeAction(action)
        suggestionDao.updateAction(
            id = id,
            actionType = type,
            actionValue = value,
            actionDisplay = display,
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

    private companion object {
        private const val ACTION_TYPE_NONE = "NONE"
        private const val ACTION_TYPE_URL = "URL"
        private const val ACTION_TYPE_APP = "APP"
    }

    private fun serializeAction(action: SuggestionAction): Triple<String, String?, String?> =
        when (action) {
            SuggestionAction.None -> Triple(ACTION_TYPE_NONE, null, null)
            is SuggestionAction.Url -> Triple(ACTION_TYPE_URL, action.url, action.display)
            is SuggestionAction.App -> Triple(ACTION_TYPE_APP, action.packageName, action.display)
        }

    private fun deserializeAction(
        actionType: String,
        actionValue: String?,
        actionDisplay: String?,
    ): SuggestionAction =
        when (actionType) {
            ACTION_TYPE_URL ->
                actionValue
                    ?.takeIf { it.isNotBlank() }
                    ?.let { SuggestionAction.Url(url = it, display = actionDisplay) }
                    ?: SuggestionAction.None

            ACTION_TYPE_APP ->
                actionValue
                    ?.takeIf { it.isNotBlank() }
                    ?.let { SuggestionAction.App(packageName = it, display = actionDisplay) }
                    ?: SuggestionAction.None

            else -> SuggestionAction.None
        }

    private fun deserializeTimeSlots(raw: String): Set<SuggestionTimeSlot> {
        val tokens = raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val parsed =
            tokens
                .mapNotNull { token ->
                    runCatching { SuggestionTimeSlot.valueOf(token) }.getOrNull()
                }.toSet()
        return normalizeTimeSlots(parsed)
    }

    private fun SuggestionEntity.toModel(): Suggestion {
        val kindEnum =
            runCatching { SuggestionMode.valueOf(kind) }
                .getOrDefault(SuggestionMode.Generic)
        val durationEnum =
            runCatching { SuggestionDurationTag.valueOf(durationTag) }
                .getOrDefault(SuggestionDurationTag.Medium)
        val priorityEnum =
            runCatching { SuggestionPriority.valueOf(priority) }
                .getOrDefault(SuggestionPriority.Normal)
        val actionModel = deserializeAction(
            actionType = actionType,
            actionValue = actionValue,
            actionDisplay = actionDisplay,
        )
        return Suggestion(
            id = id,
            title = title,
            createdAtMillis = createdAtMillis,
            kind = kindEnum,
            timeSlots = deserializeTimeSlots(timeSlots),
            durationTag = durationEnum,
            priority = priorityEnum,
            action = actionModel,
        )
    }
}
