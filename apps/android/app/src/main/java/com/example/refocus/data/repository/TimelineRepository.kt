package com.example.refocus.data.repository

import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.SettingsChangedEvent
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionDecisionEvent
import com.example.refocus.core.model.SuggestionShownEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.db.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

interface TimelineRepository {

    suspend fun append(event: TimelineEvent): Long

    suspend fun getEvents(
        startMillis: Long,
        endMillis: Long,
    ): List<TimelineEvent>

    suspend fun getEventsForDate(
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TimelineEvent>

    /**
     * 指定区間のイベントを Flow で購読する。
     *
     * 大量データでの UI 劣化を避けるため，「全件購読」を基本禁止にし，
     * 画面ごとに必要な期間だけ購読する。
     */
    fun observeEventsBetween(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TimelineEvent>>

    /**
     * ウィンドウ購読の起点より前の状態復元に使う「種イベント」を返す。
     *
     * 例：
     * - ServiceLifecycle の直前状態
     * - Screen の直前状態
     * - ForegroundApp の直前状態
     * - Permission の直前状態（permission kind ごと）
     */
    suspend fun getSeedEventsBefore(beforeMillis: Long): List<TimelineEvent>

    /**
     * 互換用（既存コードが残っている間だけ）。
     * 新規コードでは observeEventsBetween を使う。
     */
    @Deprecated("全件購読は性能劣化の原因になるため，observeEventsBetween を使う")
    fun observeEvents(): Flow<List<TimelineEvent>>
}

class TimelineRepositoryImpl(
    private val dao: TimelineEventDao,
) : TimelineRepository {

    override suspend fun append(event: TimelineEvent): Long {
        val entity = event.toEntity()
        return dao.insert(entity)
    }

    override suspend fun getEvents(
        startMillis: Long,
        endMillis: Long,
    ): List<TimelineEvent> {
        return dao.getEventsBetween(startMillis, endMillis)
            .mapNotNull { it.toDomain() }
    }

    override suspend fun getEventsForDate(
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TimelineEvent> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return getEvents(start, end)
    }

    override fun observeEventsBetween(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TimelineEvent>> {
        return dao.observeEventsBetween(startMillis, endMillis)
            .map { list -> list.mapNotNull { it.toDomain() } }
    }

    override suspend fun getSeedEventsBefore(beforeMillis: Long): List<TimelineEvent> {
        // 状態復元に必要な最小限の「直前イベント」を拾う。
        // 完璧なスナップショットではなく，ウィンドウ購読の欠損を埋めるための保険。
        val seedEntities = mutableListOf<TimelineEventEntity>()

        dao.getLatestEventOfKindBefore(KIND_SERVICE, beforeMillis)?.let { seedEntities += it }
        dao.getLatestEventOfKindBefore(KIND_SCREEN, beforeMillis)?.let { seedEntities += it }
        dao.getLatestEventOfKindBefore(KIND_FOREGROUND_APP, beforeMillis)?.let { seedEntities += it }
        dao.getLatestEventOfKindBefore(KIND_TARGET_APPS_CHANGED, beforeMillis)?.let { seedEntities += it }

        // Permission は permissionKind ごとに直前 1 件が欲しい。
        // SQL 側で group by するより，件数が小さいことを前提に Kotlin 側でユニーク化する。
        val recentPerms = dao.getLatestEventsOfKindBefore(
            kind = KIND_PERMISSION,
            beforeMillis = beforeMillis,
            limit = 32,
        )
        val pickedPermissionKinds = mutableSetOf<String>()
        for (e in recentPerms) {
            val k = e.permissionKind ?: continue
            if (pickedPermissionKinds.add(k)) {
                seedEntities += e
            }
        }

        return seedEntities
            .mapNotNull { it.toDomain() }
            .sortedBy { it.timestampMillis }
    }

    @Deprecated("全件購読は性能劣化の原因になるため，observeEventsBetween を使う")
    override fun observeEvents(): Flow<List<TimelineEvent>> {
        return dao.observeAllEvents()
            .map { list -> list.mapNotNull { it.toDomain() } }
    }

    // --- Entity ↔ Domain 変換 ---

    private fun TimelineEvent.toEntity(): TimelineEventEntity {
        val baseTimestamp = timestampMillis

        return when (this) {
            is ServiceLifecycleEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_SERVICE,
                serviceState = state.name,
            )

            is PermissionEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_PERMISSION,
                permissionKind = permission.name,
                permissionState = state.name,
            )

            is ScreenEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_SCREEN,
                screenState = state.name,
            )

            is ForegroundAppEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_FOREGROUND_APP,
                packageName = packageName,
            )

            is TargetAppsChangedEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_TARGET_APPS_CHANGED,
                extra = targetPackages.joinToString(","),
            )

            is SuggestionShownEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_SUGGESTION_SHOWN,
                packageName = packageName,
                suggestionId = suggestionId,
            )

            is SuggestionDecisionEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_SUGGESTION_DECISION,
                packageName = packageName,
                suggestionId = suggestionId,
                suggestionDecision = decision.name,
            )

            is SettingsChangedEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_SETTINGS_CHANGED,
                extraKey = key,
                extraValue = newValueDescription,
            )
        }
    }

    private fun TimelineEventEntity.toDomain(): TimelineEvent? {
        return when (kind) {
            KIND_SERVICE -> {
                val state = serviceState?.let { ServiceState.valueOf(it) } ?: return null
                ServiceLifecycleEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    state = state,
                )
            }

            KIND_PERMISSION -> {
                val perm = permissionKind?.let { PermissionKind.valueOf(it) } ?: return null
                val st = permissionState?.let { PermissionState.valueOf(it) } ?: return null
                PermissionEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    permission = perm,
                    state = st,
                )
            }

            KIND_SCREEN -> {
                val st = screenState?.let { ScreenState.valueOf(it) } ?: return null
                ScreenEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    state = st,
                )
            }

            KIND_FOREGROUND_APP -> ForegroundAppEvent(
                id = id,
                timestampMillis = timestampMillis,
                packageName = packageName,
            )

            KIND_TARGET_APPS_CHANGED -> TargetAppsChangedEvent(
                id = id,
                timestampMillis = timestampMillis,
                targetPackages = extra?.split(",")?.filter { it.isNotBlank() }?.toSet()
                    ?: emptySet(),
            )

            KIND_SUGGESTION_SHOWN -> {
                val sid = suggestionId ?: return null
                val pkg = packageName ?: return null
                SuggestionShownEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    packageName = pkg,
                    suggestionId = sid,
                )
            }

            KIND_SUGGESTION_DECISION -> {
                val sid = suggestionId ?: return null
                val pkg = packageName ?: return null
                val dec = suggestionDecision?.let { SuggestionDecision.valueOf(it) } ?: return null
                SuggestionDecisionEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    packageName = pkg,
                    suggestionId = sid,
                    decision = dec,
                )
            }

            KIND_SETTINGS_CHANGED -> {
                // v9 以降は extraKey/extraValue を正として使う．
                // v8 以前のDBを読み込む場合に備え，extra("key=value") 形式もフォールバックする．
                val fallbackKey = extra?.substringBefore("=")
                val fallbackValue = extra?.substringAfter("=", "")

                SettingsChangedEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    key = extraKey ?: fallbackKey ?: "unknown",
                    newValueDescription = extraValue ?: fallbackValue,
                )
            }

            else -> null
        }
    }

    companion object {
        private const val KIND_SERVICE = "ServiceLifecycle"
        private const val KIND_PERMISSION = "Permission"
        private const val KIND_SCREEN = "Screen"
        private const val KIND_FOREGROUND_APP = "ForegroundApp"
        private const val KIND_TARGET_APPS_CHANGED = "TargetAppsChanged"
        private const val KIND_SUGGESTION_SHOWN = "SuggestionShown"
        private const val KIND_SUGGESTION_DECISION = "SuggestionDecision"
        private const val KIND_SETTINGS_CHANGED = "SettingsChanged"
    }
}
