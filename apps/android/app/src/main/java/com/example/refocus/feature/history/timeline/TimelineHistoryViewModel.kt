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
import com.example.refocus.domain.appinfo.port.AppLabelProvider
import com.example.refocus.domain.repository.AppCatalogRepository
import com.example.refocus.domain.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class TimelineCategory(
    val label: String,
) {
    Foreground("前面アプリ"),
    Screen("画面"),
    Permission("権限"),
    Service("サービス"),
    ServiceConfig("サービス設定"),
    TargetApps("対象アプリ"),
    Suggestion("提案"),
    Settings("設定"),
    Other("その他"),
}

@HiltViewModel
class TimelineHistoryViewModel
    @Inject
    constructor(
        private val timelineRepository: TimelineRepository,
        private val appCatalogRepository: AppCatalogRepository,
        private val appLabelProvider: AppLabelProvider,
        private val timeSource: TimeSource,
    ) : ViewModel() {
        data class CategoryUiModel(
            val category: TimelineCategory,
            val label: String,
            val selected: Boolean,
        )

        data class RowUiModel(
            val id: Long?,
            val hour: Int,
            val timeText: String,
            val title: String,
            val detail: String?,
            val category: TimelineCategory,
        )

        data class UiState(
            val isLoading: Boolean = true,
            val selectedDate: LocalDate = LocalDate.ofEpochDay(0),
            val selectedDateText: String = "",
            val selectedDateUtcMillis: Long = 0L,
            val canGoNext: Boolean = false,
            val isAllCategoriesSelected: Boolean = true,
            val categories: List<CategoryUiModel> = emptyList(),
            val rows: List<RowUiModel> = emptyList(),
            val rangeText: String = "",
            val countText: String = "",
        )

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        private val zoneId = ZoneId.systemDefault()

        private val selectedDate =
            MutableStateFlow(
                Instant.ofEpochMilli(timeSource.nowMillis()).atZone(zoneId).toLocalDate(),
            )

        private val selectedCategories =
            MutableStateFlow(
                TimelineCategory.entries.toSet(),
            )

        private val labelCache = mutableMapOf<String, String>()

        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val dateFormatter =
            DateTimeFormatter
                .ofPattern("yyyy/MM/dd（EEE）")
                .withLocale(Locale.JAPAN)

        init {
            val eventsForSelectedDate =
                selectedDate.flatMapLatest { date ->
                    val (startMillis, endMillis) = toLocalDayRangeMillis(date, zoneId)
                    timelineRepository.observeEventsBetween(startMillis, endMillis)
                }

            viewModelScope.launch {
                combine(
                    eventsForSelectedDate,
                    selectedDate,
                    selectedCategories,
                ) { events, date, categories ->
                    Triple(events, date, categories)
                }.mapLatest { (events, date, categories) ->
                    val effectiveCategories = categories.ifEmpty { TimelineCategory.entries.toSet() }
                    val filtered = events.filter { categoryOf(it) in effectiveCategories }
                    val rows = buildRows(filtered)

                    val isAllSelected = effectiveCategories.size == TimelineCategory.entries.size
                    val categoryUiModels =
                        TimelineCategory.entries.map { cat ->
                            CategoryUiModel(
                                category = cat,
                                label = cat.label,
                                selected = cat in effectiveCategories,
                            )
                        }

                    val today = Instant.ofEpochMilli(timeSource.nowMillis()).atZone(zoneId).toLocalDate()
                    val canGoNext = date.isBefore(today)

                    UiState(
                        isLoading = false,
                        selectedDate = date,
                        selectedDateText = date.format(dateFormatter),
                        selectedDateUtcMillis = toUtcDateMillis(date),
                        canGoNext = canGoNext,
                        isAllCategoriesSelected = isAllSelected,
                        categories = categoryUiModels,
                        rows = rows,
                        rangeText = "${date.format(dateFormatter)} のイベント",
                        countText = "${rows.size} 件",
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            }
        }

        fun onPreviousDay() {
            onSelectDate(selectedDate.value.minusDays(1))
        }

        fun onNextDay() {
            val today = Instant.ofEpochMilli(timeSource.nowMillis()).atZone(zoneId).toLocalDate()
            val next = selectedDate.value.plusDays(1)
            if (!next.isAfter(today)) {
                onSelectDate(next)
            }
        }

        fun onSelectDate(date: LocalDate) {
            val today = Instant.ofEpochMilli(timeSource.nowMillis()).atZone(zoneId).toLocalDate()
            selectedDate.value = if (date.isAfter(today)) today else date
            // 日付が変わるので，ラベルキャッシュは維持しつつ，表示行の生成負荷を下げるためにここでは何もしない
        }

        fun onSelectAllCategories() {
            selectedCategories.value = TimelineCategory.entries.toSet()
        }

        fun onToggleCategory(category: TimelineCategory) {
            val current = selectedCategories.value
            val next =
                current.toMutableSet().apply {
                    if (contains(category)) remove(category) else add(category)
                }

            // 0 件選択は使い勝手が悪いので「すべて」に戻す
            selectedCategories.value = if (next.isEmpty()) TimelineCategory.entries.toSet() else next
        }

        private suspend fun buildRows(events: List<TimelineEvent>): List<RowUiModel> {
            if (events.isEmpty()) return emptyList()
            return events
                .sortedBy { it.timestampMillis }
                .map { ev ->
                    val category = categoryOf(ev)
                    val (title, detail) = describeEvent(ev)
                    val time = timeFormat.format(Date(ev.timestampMillis))
                    val hour = runCatching { time.substring(0, 2).toInt() }.getOrDefault(0)

                    RowUiModel(
                        id = ev.id,
                        hour = hour,
                        timeText = time,
                        title = title,
                        detail = detail,
                        category = category,
                    )
                }
        }

        private fun categoryOf(event: TimelineEvent): TimelineCategory =
            when (event) {
                is ForegroundAppEvent -> TimelineCategory.Foreground
                is ScreenEvent -> TimelineCategory.Screen
                is PermissionEvent -> TimelineCategory.Permission
                is ServiceLifecycleEvent -> TimelineCategory.Service
                is ServiceConfigEvent -> TimelineCategory.ServiceConfig
                is TargetAppsChangedEvent -> TimelineCategory.TargetApps
                is SuggestionShownEvent, is SuggestionDecisionEvent -> TimelineCategory.Suggestion
                is SettingsChangedEvent -> TimelineCategory.Settings
                else -> TimelineCategory.Other
            }

        private suspend fun describeEvent(event: TimelineEvent): Pair<String, String?> =
            when (event) {
                is ServiceLifecycleEvent -> {
                    "サービス" to
                        when (event.state) {
                            com.example.refocus.core.model.ServiceState.Started -> "起動"
                            com.example.refocus.core.model.ServiceState.Stopped -> "停止"
                        }
                }

                is ServiceConfigEvent -> {
                    val kind =
                        when (event.config) {
                            com.example.refocus.core.model.ServiceConfigKind.OverlayEnabled -> "オーバーレイ"
                            com.example.refocus.core.model.ServiceConfigKind.AutoStartOnBoot -> "端末起動時自動開始"
                        }
                    val state =
                        when (event.state) {
                            com.example.refocus.core.model.ServiceConfigState.Enabled -> "有効"
                            com.example.refocus.core.model.ServiceConfigState.Disabled -> "無効"
                        }
                    "サービス設定" to
                        listOfNotNull(
                            "$kind = $state",
                            event.meta?.takeIf { it.isNotBlank() },
                        ).joinToString("，").ifBlank { null }
                }

                is PermissionEvent -> {
                    val perm =
                        when (event.permission) {
                            com.example.refocus.core.model.PermissionKind.UsageStats -> "利用状況アクセス"
                            com.example.refocus.core.model.PermissionKind.Overlay -> "オーバーレイ"
                            com.example.refocus.core.model.PermissionKind.Notification -> "通知"
                        }
                    val state =
                        when (event.state) {
                            com.example.refocus.core.model.PermissionState.Granted -> "許可"
                            com.example.refocus.core.model.PermissionState.Revoked -> "未許可"
                        }
                    "権限" to "$perm = $state"
                }

                is ScreenEvent -> {
                    "画面" to
                        when (event.state) {
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
                    "設定変更" to
                        listOfNotNull(
                            event.key,
                            event.newValueDescription?.takeIf { it.isNotBlank() },
                        ).joinToString("，").ifBlank { null }
                }

                else -> {
                    RefocusLog.w(
                        "TimelineHistoryViewModel",
                    ) { "Unknown timeline event type: ${event::class.simpleName}" }
                    "イベント" to event::class.simpleName
                }
            }

        private suspend fun resolveLabel(packageName: String): String {
            labelCache[packageName]?.let { return it }

            val label =
                runCatching {
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

        private fun toLocalDayRangeMillis(
            date: LocalDate,
            zoneId: ZoneId,
        ): Pair<Long, Long> {
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end =
                date
                    .plusDays(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            return start to end
        }

        private fun toUtcDateMillis(date: LocalDate): Long =
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
