package com.example.refocus.domain.timeline

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
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.repository.TimelineRepository

/**
 * System 層からのイベントを一元的に記録するためのユーティリティ。
 *
 * - OverlayService / PermissionHelper / AppSelect / SuggestionOverlay などは
 *   ここを経由して TimelineRepository にイベントを追加する。
 */
class EventRecorder(
    private val timeSource: TimeSource,
    private val timelineRepository: TimelineRepository,
) {

    private fun now(): Long = timeSource.nowMillis()

    suspend fun onServiceStarted() {
        timelineRepository.append(
            ServiceLifecycleEvent(
                timestampMillis = now(),
                state = ServiceState.Started,
            )
        )
    }

    suspend fun onServiceStopped() {
        timelineRepository.append(
            ServiceLifecycleEvent(
                timestampMillis = now(),
                state = ServiceState.Stopped,
            )
        )
    }

    suspend fun onServiceConfigChanged(
        config: ServiceConfigKind,
        state: ServiceConfigState,
        meta: String? = null,
    ) {
        timelineRepository.append(
            ServiceConfigEvent(
                timestampMillis = now(),
                config = config,
                state = state,
                meta = meta,
            )
        )
    }

    suspend fun onPermissionChanged(
        permission: PermissionKind,
        state: PermissionState,
    ) {
        timelineRepository.append(
            PermissionEvent(
                timestampMillis = now(),
                permission = permission,
                state = state,
            )
        )
    }

    suspend fun onScreenOn() {
        timelineRepository.append(
            ScreenEvent(
                timestampMillis = now(),
                state = ScreenState.On,
            )
        )
    }

    suspend fun onScreenOff() {
        timelineRepository.append(
            ScreenEvent(
                timestampMillis = now(),
                state = ScreenState.Off,
            )
        )
    }

    suspend fun onForegroundAppChanged(packageName: String?) {
        timelineRepository.append(
            ForegroundAppEvent(
                timestampMillis = now(),
                packageName = packageName,
            )
        )
    }

    suspend fun onTargetAppsChanged(targetPackages: Set<String>) {
        timelineRepository.append(
            TargetAppsChangedEvent(
                timestampMillis = now(),
                targetPackages = targetPackages,
            )
        )
    }

    suspend fun onSuggestionShown(
        packageName: String,
        suggestionId: Long,
    ) {
        timelineRepository.append(
            SuggestionShownEvent(
                timestampMillis = now(),
                packageName = packageName,
                suggestionId = suggestionId,
            )
        )
    }

    suspend fun onSuggestionDecision(
        packageName: String,
        suggestionId: Long,
        decision: SuggestionDecision,
    ) {
        timelineRepository.append(
            SuggestionDecisionEvent(
                timestampMillis = now(),
                packageName = packageName,
                suggestionId = suggestionId,
                decision = decision,
            )
        )
    }

    suspend fun onSettingsChanged(
        key: String,
        newValueDescription: String?,
    ) {
        timelineRepository.append(
            SettingsChangedEvent(
                timestampMillis = now(),
                key = key,
                newValueDescription = newValueDescription,
            )
        )
    }
}
