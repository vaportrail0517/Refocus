package com.example.refocus.data.repository

import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceConfigEvent
import com.example.refocus.core.model.ServiceConfigKind
import com.example.refocus.core.model.ServiceConfigState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.SettingsChangedEvent
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionDecisionEvent
import com.example.refocus.core.model.SuggestionShownEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.db.entity.TimelineEventEntity
import com.example.refocus.domain.repository.TimelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

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

    private inline fun <reified T : Enum<T>> safeEnumValueOf(
        value: String?,
        kind: String,
        id: Long,
        field: String,
    ): T? {
        if (value.isNullOrBlank()) return null

        return runCatching { enumValueOf<T>(value) }
            .getOrElse { e ->
                // 古い DB / 将来の enum 変更 / 破損データが混ざっても，全体を落とさない
                RefocusLog.w("TimelineRepository", e) {
                    "Unknown enum value '$value' for $field (kind=$kind, id=$id)"
                }
                null
            }
    }

    private fun TimelineEvent.toEntity(): TimelineEventEntity {
        val baseTimestamp = timestampMillis

        return when (this) {
            is ServiceConfigEvent -> TimelineEventEntity(
                id = id ?: 0L,
                timestampMillis = baseTimestamp,
                kind = KIND_SERVICE_CONFIG,
                extraKey = config.name,
                extraValue = state.name,
                extra = meta,
            )

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
            KIND_SERVICE_CONFIG -> {
                val config = safeEnumValueOf<ServiceConfigKind>(
                    value = extraKey,
                    kind = kind,
                    id = id,
                    field = "ServiceConfigKind",
                ) ?: return null
                val state = safeEnumValueOf<ServiceConfigState>(
                    value = extraValue,
                    kind = kind,
                    id = id,
                    field = "ServiceConfigState",
                ) ?: return null
                ServiceConfigEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    config = config,
                    state = state,
                    meta = extra,
                )
            }

            KIND_SERVICE -> {
                val state = safeEnumValueOf<ServiceState>(
                    value = serviceState,
                    kind = kind,
                    id = id,
                    field = "ServiceState",
                ) ?: return null
                ServiceLifecycleEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    state = state,
                )
            }

            KIND_PERMISSION -> {
                val perm = safeEnumValueOf<PermissionKind>(
                    value = permissionKind,
                    kind = kind,
                    id = id,
                    field = "PermissionKind",
                ) ?: return null
                val st = safeEnumValueOf<PermissionState>(
                    value = permissionState,
                    kind = kind,
                    id = id,
                    field = "PermissionState",
                ) ?: return null
                PermissionEvent(
                    id = id,
                    timestampMillis = timestampMillis,
                    permission = perm,
                    state = st,
                )
            }

            KIND_SCREEN -> {
                val st = safeEnumValueOf<ScreenState>(
                    value = screenState,
                    kind = kind,
                    id = id,
                    field = "ScreenState",
                ) ?: return null
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
                val dec = safeEnumValueOf<SuggestionDecision>(
                    value = suggestionDecision,
                    kind = kind,
                    id = id,
                    field = "SuggestionDecision",
                ) ?: return null
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
        private const val KIND_SERVICE_CONFIG = "ServiceConfig"
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
