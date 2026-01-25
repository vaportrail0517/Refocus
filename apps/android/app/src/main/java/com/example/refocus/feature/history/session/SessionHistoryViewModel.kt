package com.example.refocus.feature.history.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionStats
import com.example.refocus.core.model.SessionStatus
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.core.util.TimeSource
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.domain.appinfo.port.AppLabelProvider
import com.example.refocus.domain.monitor.port.ForegroundAppObserver
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.stats.SessionStatsCalculator
import com.example.refocus.domain.timeline.TimelineInterpretationConfig
import com.example.refocus.domain.timeline.TimelineProjector
import com.example.refocus.domain.timeline.TimelineWindowEventsLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SessionHistoryViewModel
    @Inject
    constructor(
        private val timelineRepository: TimelineRepository,
        private val settingsRepository: SettingsRepository,
        private val targetsRepository: TargetsRepository,
        private val foregroundAppObserver: ForegroundAppObserver,
        private val timeSource: TimeSource,
        private val appLabelProvider: AppLabelProvider,
    ) : ViewModel() {
        private val historyLookbackMillis: Long = TimeUnit.DAYS.toMillis(30)

        data class PauseResumeUiModel(
            val pausedAtText: String,
            val resumedAtText: String?,
        )

        data class SessionUiModel(
            val id: Long,
            val appName: String,
            val packageName: String,
            val startedText: String,
            val endedText: String,
            val durationText: String,
            val status: SessionStatus,
            val pauseResumeEvents: List<PauseResumeUiModel>,
        )

        data class UiState(
            val sessions: List<SessionUiModel> = emptyList(),
            val isLoading: Boolean = true,
        )

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

        private val windowLoader = TimelineWindowEventsLoader(timelineRepository)

        init {
            viewModelScope.launch {
                val nowMillis = timeSource.nowMillis()
                val windowStart = (nowMillis - historyLookbackMillis).coerceAtLeast(0L)

                // foreground の Flow に初期値 null を付与
                val foregroundFlow =
                    foregroundAppObserver
                        .foregroundSampleFlow(pollingIntervalMs = 1_000L)
                        .map { it.packageName }
                        .onStart { emit(null) }

                combine(
                    windowLoader.observeWithSeed(windowStartMillis = windowStart, windowEndMillis = Long.MAX_VALUE),
                    settingsRepository.observeOverlaySettings(),
                    targetsRepository.observeTargets(),
                    foregroundFlow,
                ) { events, settings, targets, foregroundPackage ->
                    CombinedInput(
                        events = events,
                        customize = settings,
                        targets = targets,
                        foregroundPackage = foregroundPackage,
                    )
                }.collectLatest { input ->
                    buildUiState(
                        events = input.events,
                        customize = input.customize,
                        targets = input.targets,
                        foregroundPackage = input.foregroundPackage,
                        windowStartMillis = windowStart,
                    )
                }
            }
        }

        private data class CombinedInput(
            val events: List<TimelineEvent>,
            val customize: Customize,
            val targets: Set<String>,
            val foregroundPackage: String?,
        )

        private fun buildUiState(
            events: List<TimelineEvent>,
            customize: Customize,
            targets: Set<String>,
            foregroundPackage: String?,
            windowStartMillis: Long,
        ) {
            if (events.isEmpty()) {
                _uiState.value =
                    UiState(
                        sessions = emptyList(),
                        isLoading = false,
                    )
                return
            }

            val nowMillis = timeSource.nowMillis()

            val eventsForProjection =
                ensureTargetAppsSeedIfMissing(
                    events = events,
                    fallbackTargets = targets,
                    windowStartMillis = windowStartMillis,
                )

            // TimelineEvent からセッションとイベント列を再構成（共通プロジェクタに一本化）
            val projection =
                TimelineProjector.project(
                    events = eventsForProjection,
                    config = TimelineInterpretationConfig(stopGracePeriodMillis = customize.gracePeriodMillis),
                    nowMillis = nowMillis,
                    zoneId = ZoneId.systemDefault(),
                )

            val sessions = projection.sessions
            val eventsMap: Map<Long, List<SessionEvent>> = projection.eventsBySessionId

            if (sessions.isEmpty()) {
                _uiState.value =
                    UiState(
                        sessions = emptyList(),
                        isLoading = false,
                    )
                return
            }

            // domain の集計ロジックで SessionStats を作る
            val statsList: List<SessionStats> =
                SessionStatsCalculator.buildSessionStats(
                    sessions = sessions,
                    eventsMap = eventsMap,
                    foregroundPackage = foregroundPackage,
                    nowMillis = nowMillis,
                )

            // それを UI 用に整形
            val uiModels =
                statsList.map { stats ->
                    val durationText = formatDurationMilliSeconds(stats.durationMillis)

                    val pauseUiList =
                        stats.pauseResumeEvents.map { pauseStats ->
                            PauseResumeUiModel(
                                pausedAtText = formatDateTime(pauseStats.pausedAtMillis),
                                resumedAtText =
                                    pauseStats.resumedAtMillis?.let { resumed ->
                                        formatDateTime(resumed)
                                    },
                            )
                        }
                    SessionUiModel(
                        id = stats.id,
                        appName = resolveAppName(stats.packageName),
                        packageName = stats.packageName,
                        startedText = formatDateTime(stats.startedAtMillis),
                        endedText = stats.endedAtMillis?.let { formatDateTime(it) } ?: "未終了",
                        durationText = durationText,
                        status = stats.status,
                        pauseResumeEvents = pauseUiList,
                    )
                }

            _uiState.value =
                UiState(
                    sessions = uiModels,
                    isLoading = false,
                )
        }

        private fun ensureTargetAppsSeedIfMissing(
            events: List<TimelineEvent>,
            fallbackTargets: Set<String>,
            windowStartMillis: Long,
        ): List<TimelineEvent> {
            if (fallbackTargets.isEmpty()) return events
            if (events.any { it is TargetAppsChangedEvent }) return events

            val baseTs =
                if (events.isNotEmpty()) {
                    minOf(windowStartMillis, events.minOf { it.timestampMillis })
                } else {
                    windowStartMillis
                }.coerceAtLeast(0L)

            RefocusLog.w(TAG) {
                "TargetAppsChangedEvent が見つからないため，現在の対象アプリ（${fallbackTargets.size}件）を seed として補完します（ts=$baseTs）"
            }

            val seed = TargetAppsChangedEvent(timestampMillis = baseTs, targetPackages = fallbackTargets)
            return (events + seed).sortedBy { it.timestampMillis }
        }

        private companion object {
            const val TAG = "SessionHistoryViewModel"
        }


        private fun resolveAppName(packageName: String): String = appLabelProvider.labelOf(packageName)

        private fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))
    }
