package com.example.refocus.feature.history

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionStatus
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.domain.stats.SessionStatsCalculator
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
    private val sessionRepository: SessionRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor
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
                .onStart {
                    emit(null)
                }
            combine(
                sessionRepository.observeAllSessionsWithEvents(),
                foregroundFlow
            ) { sessionsWithEvents, foregroundPackage: String? ->
                Triple(
                    sessionsWithEvents.sessions,
                    sessionsWithEvents.eventsBySessionId,
                    foregroundPackage
                )
            }.collectLatest { (sessions, eventsMap, foregroundPackage) ->
                buildUiState(
                    sessions = sessions,
                    eventsMap = eventsMap,
                    foregroundPackage = foregroundPackage
                )
            }
        }
    }

    private fun buildUiState(
        sessions: List<Session>,
        eventsMap: Map<Long, List<SessionEvent>>,
        foregroundPackage: String?,
    ) {
        if (sessions.isEmpty()) {
            _uiState.value = UiState(
                sessions = emptyList(),
                isLoading = false,
            )
            return
        }
        val nowMillis = System.currentTimeMillis()
        // まず domain の集計ロジックで SessionStats を作る
        val statsList = SessionStatsCalculator.buildSessionStats(
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
