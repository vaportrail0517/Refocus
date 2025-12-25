package com.example.refocus.feature.history.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ServiceConfigEvent
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.SettingsChangedEvent
import com.example.refocus.core.model.SuggestionDecisionEvent
import com.example.refocus.core.model.SuggestionShownEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.gateway.AppLabelProvider
import com.example.refocus.domain.repository.AppCatalogRepository
import com.example.refocus.domain.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TimelineHistoryViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val appCatalogRepository: AppCatalogRepository,
    private val appLabelProvider: AppLabelProvider,
    private val timeSource: TimeSource,
) : ViewModel() {

    data class RowUiModel(
        val id: Long?,
        val timeText: String,
        val title: String,
        val detail: String?,
    )

    data class UiState(
        val rows: List<RowUiModel> = emptyList(),
        val isLoading: Boolean = true,
        val rangeText: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val labelCache = mutableMapOf<String, String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    init {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val startOfTodayMillis = startOfTodayMillis(
                nowMillis = timeSource.nowMillis(),
                zoneId = zone,
            )

            timelineRepository
                .observeEventsBetween(startOfTodayMillis, Long.MAX_VALUE)
                .collectLatest { events ->
                    val sorted = events.sortedBy { it.timestampMillis }
                    val rows = buildRows(sorted)

                    _uiState.value = UiState(
                        rows = rows,
                        isLoading = false,
                        rangeText = "${dateFormat.format(Date(startOfTodayMillis))} のイベント",
                    )
                }
        }
    }

    private suspend fun buildRows(events: List<TimelineEvent>): List<RowUiModel> {
        if (events.isEmpty()) return emptyList()
        return events.map { ev ->
            val (title, detail) = describeEvent(ev)
            RowUiModel(
                id = ev.id,
                timeText = timeFormat.format(Date(ev.timestampMillis)),
                title = title,
                detail = detail,
            )
        }
    }

    private suspend fun describeEvent(event: TimelineEvent): Pair<String, String?> {
        return when (event) {
            is ServiceLifecycleEvent -> {
                "サービス" to when (event.state) {
                    com.example.refocus.core.model.ServiceState.Started -> "起動"
                    com.example.refocus.core.model.ServiceState.Stopped -> "停止"
                }
            }

            is ServiceConfigEvent -> {
                val kind = when (event.config) {
                    com.example.refocus.core.model.ServiceConfigKind.OverlayEnabled -> "オーバーレイ"
                    com.example.refocus.core.model.ServiceConfigKind.AutoStartOnBoot -> "端末起動時自動開始"
                }
                val state = when (event.state) {
                    com.example.refocus.core.model.ServiceConfigState.Enabled -> "有効"
                    com.example.refocus.core.model.ServiceConfigState.Disabled -> "無効"
                }
                "サービス設定" to listOfNotNull(
                    "$kind = $state",
                    event.meta?.takeIf { it.isNotBlank() },
                ).joinToString("，").ifBlank { null }
            }

            is PermissionEvent -> {
                val perm = when (event.permission) {
                    com.example.refocus.core.model.PermissionKind.UsageStats -> "利用状況アクセス"
                    com.example.refocus.core.model.PermissionKind.Overlay -> "オーバーレイ"
                }
                val state = when (event.state) {
                    com.example.refocus.core.model.PermissionState.Granted -> "許可"
                    com.example.refocus.core.model.PermissionState.Revoked -> "未許可"
                }
                "権限" to "$perm = $state"
            }

            is ScreenEvent -> {
                "画面" to when (event.state) {
                    com.example.refocus.core.model.ScreenState.On -> "ON"
                    com.example.refocus.core.model.ScreenState.Off -> "OFF"
                }
            }

            is ForegroundAppEvent -> {
                val pkg = event.packageName
                if (pkg.isNullOrBlank()) {
                    "前面アプリ" to "ホーム等"
                } else {
                    val label = resolveLabel(pkg)
                    "前面アプリ" to "$label（$pkg）"
                }
            }

            is TargetAppsChangedEvent -> {
                "対象アプリ変更" to "${event.targetPackages.size} 件"
            }

            is SuggestionShownEvent -> {
                val label = resolveLabel(event.packageName)
                "提案表示" to "$label（${event.packageName}），id=${event.suggestionId}"
            }

            is SuggestionDecisionEvent -> {
                val label = resolveLabel(event.packageName)
                "提案操作" to "$label（${event.packageName}），id=${event.suggestionId}，${event.decision}"
            }

            is SettingsChangedEvent -> {
                "設定変更" to listOfNotNull(
                    event.key,
                    event.newValueDescription?.takeIf { it.isNotBlank() }?.let { "$it" },
                ).joinToString("，").ifBlank { null }
            }

            else -> {
                RefocusLog.w("TimelineHistoryViewModel") { "Unknown timeline event type: ${event::class.simpleName}" }
                "イベント" to event::class.simpleName
            }
        }
    }

    private suspend fun resolveLabel(packageName: String): String {
        labelCache[packageName]?.let { return it }

        val label = runCatching {
            appCatalogRepository.getLastKnownLabel(packageName)
                ?: appCatalogRepository.getFirstTargetedLabel(packageName)
                ?: appLabelProvider.labelOf(packageName)
        }.getOrElse { e ->
            RefocusLog.w("TimelineHistoryViewModel", e) { "Failed to resolve app label: $packageName" }
            packageName
        }

        labelCache[packageName] = label
        return label
    }

    private fun startOfTodayMillis(
        nowMillis: Long,
        zoneId: ZoneId,
    ): Long {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return nowDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
