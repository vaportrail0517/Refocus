package com.example.refocus.data.db.mapper

import com.example.refocus.core.logging.RefocusLog
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
import com.example.refocus.core.model.UiInterruptionEvent
import com.example.refocus.core.model.UiInterruptionSource
import com.example.refocus.core.model.UiInterruptionState
import com.example.refocus.data.db.entity.TimelineEventEntity

internal class TimelineEventEntityMapper {
    fun toEntity(event: TimelineEvent): TimelineEventEntity {
        val baseTimestamp = event.timestampMillis

        return when (event) {
            is ServiceConfigEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_SERVICE_CONFIG,
                    extraKey = event.config.name,
                    extraValue = event.state.name,
                    extra = event.meta,
                )

            is ServiceLifecycleEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_SERVICE,
                    serviceState = event.state.name,
                )

            is PermissionEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_PERMISSION,
                    permissionKind = event.permission.name,
                    permissionState = event.state.name,
                )

            is ScreenEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_SCREEN,
                    screenState = event.state.name,
                )

            is ForegroundAppEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_FOREGROUND_APP,
                    packageName = event.packageName,
                )

            is TargetAppsChangedEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_TARGET_APPS_CHANGED,
                    extra = event.targetPackages.joinToString(","),
                )

            is SuggestionShownEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_SUGGESTION_SHOWN,
                    packageName = event.packageName,
                    suggestionId = event.suggestionId,
                )

            is SuggestionDecisionEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_SUGGESTION_DECISION,
                    packageName = event.packageName,
                    suggestionId = event.suggestionId,
                    suggestionDecision = event.decision.name,
                )

            is UiInterruptionEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_UI_INTERRUPTION,
                    packageName = event.packageName,
                    extraKey = event.source.name,
                    extraValue = event.state.name,
                )

            is SettingsChangedEvent ->
                TimelineEventEntity(
                    id = event.id ?: 0L,
                    timestampMillis = baseTimestamp,
                    kind = KIND_SETTINGS_CHANGED,
                    extraKey = event.key,
                    extraValue = event.newValueDescription,
                )
        }
    }

    fun toDomain(entity: TimelineEventEntity): TimelineEvent? {
        return when (entity.kind) {
            KIND_SERVICE_CONFIG -> {
                val config =
                    safeEnumValueOf<ServiceConfigKind>(
                        value = entity.extraKey,
                        kind = entity.kind,
                        id = entity.id,
                        field = "ServiceConfigKind",
                    ) ?: return null
                val state =
                    safeEnumValueOf<ServiceConfigState>(
                        value = entity.extraValue,
                        kind = entity.kind,
                        id = entity.id,
                        field = "ServiceConfigState",
                    ) ?: return null
                ServiceConfigEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    config = config,
                    state = state,
                    meta = entity.extra,
                )
            }

            KIND_SERVICE -> {
                val state =
                    safeEnumValueOf<ServiceState>(
                        value = entity.serviceState,
                        kind = entity.kind,
                        id = entity.id,
                        field = "ServiceState",
                    ) ?: return null
                ServiceLifecycleEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    state = state,
                )
            }

            KIND_PERMISSION -> {
                val perm =
                    safeEnumValueOf<PermissionKind>(
                        value = entity.permissionKind,
                        kind = entity.kind,
                        id = entity.id,
                        field = "PermissionKind",
                    ) ?: return null
                val st =
                    safeEnumValueOf<PermissionState>(
                        value = entity.permissionState,
                        kind = entity.kind,
                        id = entity.id,
                        field = "PermissionState",
                    ) ?: return null
                PermissionEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    permission = perm,
                    state = st,
                )
            }

            KIND_SCREEN -> {
                val st =
                    safeEnumValueOf<ScreenState>(
                        value = entity.screenState,
                        kind = entity.kind,
                        id = entity.id,
                        field = "ScreenState",
                    ) ?: return null
                ScreenEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    state = st,
                )
            }

            KIND_FOREGROUND_APP ->
                ForegroundAppEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    packageName = entity.packageName,
                )

            KIND_TARGET_APPS_CHANGED ->
                TargetAppsChangedEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    targetPackages =
                        entity.extra
                            ?.split(",")
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            ?: emptySet(),
                )

            KIND_SUGGESTION_SHOWN -> {
                val sid = entity.suggestionId ?: return null
                val pkg = entity.packageName ?: return null
                SuggestionShownEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    packageName = pkg,
                    suggestionId = sid,
                )
            }

            KIND_SUGGESTION_DECISION -> {
                val sid = entity.suggestionId ?: return null
                val pkg = entity.packageName ?: return null
                val dec =
                    safeEnumValueOf<SuggestionDecision>(
                        value = entity.suggestionDecision,
                        kind = entity.kind,
                        id = entity.id,
                        field = "SuggestionDecision",
                    ) ?: return null
                SuggestionDecisionEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    packageName = pkg,
                    suggestionId = sid,
                    decision = dec,
                )
            }

            KIND_SETTINGS_CHANGED -> {
                // v9 以降は extraKey/extraValue を正として使う．
                // v8 以前のDBを読み込む場合に備え，extra("key=value") 形式もフォールバックする．
                val fallbackKey = entity.extra?.substringBefore("=")
                val fallbackValue = entity.extra?.substringAfter("=", "")

                SettingsChangedEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    key = entity.extraKey ?: fallbackKey ?: "unknown",
                    newValueDescription = entity.extraValue ?: fallbackValue,
                )
            }

            KIND_UI_INTERRUPTION -> {
                val pkg = entity.packageName ?: return null
                val source =
                    safeEnumValueOf<UiInterruptionSource>(
                        value = entity.extraKey,
                        kind = entity.kind,
                        id = entity.id,
                        field = "UiInterruptionSource",
                    ) ?: return null
                val state =
                    safeEnumValueOf<UiInterruptionState>(
                        value = entity.extraValue,
                        kind = entity.kind,
                        id = entity.id,
                        field = "UiInterruptionState",
                    ) ?: return null

                UiInterruptionEvent(
                    id = entity.id,
                    timestampMillis = entity.timestampMillis,
                    packageName = pkg,
                    source = source,
                    state = state,
                )
            }

            else -> null
        }
    }

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

    internal companion object {
        const val KIND_SERVICE_CONFIG = "ServiceConfig"
        const val KIND_SERVICE = "ServiceLifecycle"
        const val KIND_PERMISSION = "Permission"
        const val KIND_SCREEN = "Screen"
        const val KIND_FOREGROUND_APP = "ForegroundApp"
        const val KIND_TARGET_APPS_CHANGED = "TargetAppsChanged"
        const val KIND_SUGGESTION_SHOWN = "SuggestionShown"
        const val KIND_SUGGESTION_DECISION = "SuggestionDecision"
        const val KIND_SETTINGS_CHANGED = "SettingsChanged"
        const val KIND_UI_INTERRUPTION = "UiInterruption"
    }
}
