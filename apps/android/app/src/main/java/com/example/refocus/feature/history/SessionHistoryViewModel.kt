package com.example.refocus.feature.history

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionStats
import com.example.refocus.core.model.SessionStatus
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.core.util.TimeSource
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.domain.stats.SessionStatsCalculator
import com.example.refocus.domain.timeline.SessionProjector
import com.example.refocus.system.monitor.ForegroundAppMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SessionHistoryViewModel @Inject constructor(
    application: Application,
    private val timelineRepository: TimelineRepository,
    private val settingsRepository: SettingsRepository,
    private val targetsRepository: TargetsRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val timeSource: TimeSource,
) : AndroidViewModel(application) {

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

    private val appContext = application
    private val packageManager: PackageManager = application.packageManager
    private val appNameCache = mutableMapOf<String, String>()

    private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            // foreground の Flow に初期値 null を付与
            val foregroundFlow = foregroundAppMonitor
                .foregroundAppFlow(pollingIntervalMs = 1_000L)
                .onStart { emit(null) }

            combine(
                timelineRepository.observeEvents(),
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
    ) {
        if (events.isEmpty()) {
            _uiState.value = UiState(
                sessions = emptyList(),
                isLoading = false,
            )
            return
        }

        val nowMillis = timeSource.nowMillis()

        // TimelineEvent からセッションを再構成
        val sessionsWithEvents = SessionProjector.projectSessions(
            events = events,
            targetPackages = targets,
            stopGracePeriodMillis = customize.gracePeriodMillis,
            nowMillis = nowMillis,
        )

        if (sessionsWithEvents.isEmpty()) {
            _uiState.value = UiState(
                sessions = emptyList(),
                isLoading = false,
            )
            return
        }

        val sessions = sessionsWithEvents.map { it.session }
        val eventsMap: Map<Long, List<SessionEvent>> =
            sessionsWithEvents.mapNotNull { swe ->
                val id = swe.session.id ?: return@mapNotNull null
                id to swe.events
            }.toMap()

        // domain の集計ロジックで SessionStats を作る
        val statsList: List<SessionStats> = SessionStatsCalculator.buildSessionStats(
            sessions = sessions,
            eventsMap = eventsMap,
            foregroundPackage = foregroundPackage,
            nowMillis = nowMillis,
        )

        // それを UI 用に整形
        val uiModels = statsList.map { stats ->
            val durationText = formatDurationMilliSeconds(stats.durationMillis)

            val pauseUiList = stats.pauseResumeEvents.map { pauseStats ->
                PauseResumeUiModel(
                    pausedAtText = formatDateTime(pauseStats.pausedAtMillis),
                    resumedAtText = pauseStats.resumedAtMillis?.let { resumed ->
                        formatDateTime(resumed)
                    }
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

        _uiState.value = UiState(
            sessions = uiModels,
            isLoading = false,
        )
    }

    private fun resolveAppName(packageName: String): String {
        appNameCache[packageName]?.let { return it }
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            appNameCache[packageName] = label
            label
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun formatDateTime(millis: Long): String {
        return dateTimeFormat.format(Date(millis))
    }
}
