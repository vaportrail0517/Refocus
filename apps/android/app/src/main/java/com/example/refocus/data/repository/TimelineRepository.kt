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
                extra = "$key=${newValueDescription.orEmpty()}",
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

            KIND_SETTINGS_CHANGED -> SettingsChangedEvent(
                id = id,
                timestampMillis = timestampMillis,
                key = extra?.substringBefore("=") ?: "unknown",
                newValueDescription = extra?.substringAfter("=", ""),
            )

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
