package com.example.refocus.feature.history

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.system.monitor.ForegroundAppMonitor
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
import java.util.concurrent.TimeUnit

class SessionHistoryViewModel(
    application: Application,
    private val sessionRepository: SessionRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor,
) : AndroidViewModel(application) {

    enum class SessionStatus {
        RUNNING,
        GRACE,
        FINISHED
    }

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
        foregroundPackage: String?
    ) {
        if (sessions.isEmpty()) {
            _uiState.value = UiState(
                sessions = emptyList(),
                isLoading = false
            )
            return
        }
        val nowMillis = System.currentTimeMillis()
        val uiModels = sessions
            .sortedByDescending { session ->
                val events = eventsMap[session.id] ?: emptyList()
                events.maxOfOrNull { it.timestampMillis } ?: 0L
            }
            .mapNotNull { session ->
                val id = session.id ?: return@mapNotNull null
                val events = eventsMap[id] ?: emptyList()
                if (events.isEmpty()) return@mapNotNull null
                val startedAt = events.firstOrNull {
                    it.type == SessionEventType.Start
                }?.timestampMillis ?: events.first().timestampMillis
                val endedAt = events.lastOrNull {
                    it.type == SessionEventType.End
                }?.timestampMillis
                val status = when {
                    endedAt != null -> SessionStatus.FINISHED
                    session.packageName == foregroundPackage -> SessionStatus.RUNNING
                    else -> SessionStatus.GRACE
                }
                val durationMillis =
                    calculateDurationFromEvents(events, nowMillis)
                val durationText = when (status) {
                    SessionStatus.GRACE -> "" // GRACE 中は空でもよい（好みで変えてOK）
                    else -> formatDuration(durationMillis)
                }
                val pauseUiList = buildPauseResumeUiModels(events)
                SessionUiModel(
                    id = id,
                    appName = resolveAppName(session.packageName),
                    packageName = session.packageName,
                    startedText = formatDateTime(startedAt),
                    endedText = endedAt?.let { formatDateTime(it) } ?: "未終了",
                    durationText = durationText,
                    status = status,
                    pauseResumeEvents = pauseUiList
                )
            }
        _uiState.value = UiState(
            sessions = uiModels,
            isLoading = false
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

    /**
     * Start / Pause / Resume / End のイベント列から、
     * 「実際にアプリを使っていた時間」の合計を求める。
     */
    private fun calculateDurationFromEvents(
        events: List<SessionEvent>,
        nowMillis: Long
    ): Long {
        if (events.isEmpty()) return 0L
        val sorted = events.sortedBy { it.timestampMillis }

        var lastStart: Long? = null
        var totalActive = 0L

        for (e in sorted) {
            when (e.type) {
                SessionEventType.Start -> {
                    lastStart = e.timestampMillis
                }
                SessionEventType.Pause -> {
                    if (lastStart != null) {
                        totalActive += (e.timestampMillis - lastStart!!).coerceAtLeast(0L)
                        lastStart = null
                    }
                }
                SessionEventType.Resume -> {
                    if (lastStart == null) {
                        lastStart = e.timestampMillis
                    }
                }
                SessionEventType.End -> {
                    if (lastStart != null) {
                        totalActive += (e.timestampMillis - lastStart!!).coerceAtLeast(0L)
                        lastStart = null
                    }
                }
            }
        }

        // まだ Start されたまま（End が来ていない）場合は now まで加算
        if (lastStart != null) {
            totalActive += (nowMillis - lastStart!!).coerceAtLeast(0L)
        }

        return totalActive.coerceAtLeast(0L)
    }

    /**
     * イベント列から Pause/Resume のペアを UI 用に組み立てる。
     * - Pause → Resume の順に現れたもののみペアにする
     * - Resume が無い Pause は「未再開」として resumedAtText = null で残す
     */
    private fun buildPauseResumeUiModels(
        events: List<SessionEvent>
    ): List<PauseResumeUiModel> {
        if (events.isEmpty()) return emptyList()
        val sorted = events.sortedBy { it.timestampMillis }

        val result = mutableListOf<PauseResumeUiModel>()
        var currentPause: Long? = null

        for (e in sorted) {
            when (e.type) {
                SessionEventType.Pause -> {
                    // すでに Pause 中なら上書き（異常系は雑に潰す）
                    currentPause = e.timestampMillis
                }
                SessionEventType.Resume -> {
                    if (currentPause != null) {
                        result.add(
                            PauseResumeUiModel(
                                pausedAtText = formatDateTime(currentPause!!),
                                resumedAtText = formatDateTime(e.timestampMillis)
                            )
                        )
                        currentPause = null
                    }
                }
                else -> {
                    // Start / End はここでは何もしない
                }
            }
        }

        // Pause されたまま終わっている場合も、未再開として 1 行出しておく
        if (currentPause != null) {
            result.add(
                PauseResumeUiModel(
                    pausedAtText = formatDateTime(currentPause!!),
                    resumedAtText = null
                )
            )
        }

        return result
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return if (hours > 0) {
            String.format("%d時間%02d分%02d秒", hours, remMinutes, seconds)
        } else {
            String.format("%d分%02d秒", minutes, seconds)
        }
    }
}
