package com.example.refocus.feature.history

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionPauseResume
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.feature.monitor.ForegroundAppMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class SessionHistoryViewModel(
    application: Application,
    private val sessionRepository: SessionRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor
) : AndroidViewModel(application) {

    enum class SessionStatus {
        RUNNING,
        GRACE,
        FINISHED
    }
    data class PauseResumeUiModel(
        val pausedAtText: String,
        val resumedAtText: String?, // null の場合は「未再開」などで表示
    )
    data class SessionUiModel(
        val id: Long?,
        val appLabel: String,
        val packageName: String,
        val startedAtText: String,
        val endedAtText: String,
        val durationText: String,
        val status: SessionStatus,
        val pauses: List<PauseResumeUiModel>,
    )

    private val pm: PackageManager = application.packageManager
    private val _sessions = MutableStateFlow<List<SessionUiModel>>(emptyList())
    val sessions: StateFlow<List<SessionUiModel>> = _sessions.asStateFlow()

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            combine(
                sessionRepository.observeAllSessions(),                          // DB上の全セッション
                foregroundAppMonitor.foregroundAppFlow(pollingIntervalMs = 500L) // 現在の前面アプリ
                    .catch { emit(null) }                                        // 取得失敗時は null
                    .onStart { emit(null) }                                     // 初期値
            ) { sessions, foregroundPackage ->
                sessions to foregroundPackage
            }.collect { (sessions, foregroundPackage) ->
                val pauseMap = mutableMapOf<Long, List<SessionPauseResume>>()
                for (session in sessions) {
                    val id = session.id ?: continue
                    val events = sessionRepository.getPauseResumeEvents(id)
                    pauseMap[id] = events
                }

                _sessions.value = sessions.map { session ->
                    val events = session.id?.let { pauseMap[it] } ?: emptyList()
                    session.toUiModel(
                        foregroundPackage = foregroundPackage,
                        pauses = events
                    )
                }
            }
        }
    }

    private fun Session.toUiModel(
        foregroundPackage: String?,
        pauses: List<SessionPauseResume>,
    ): SessionUiModel {
        val label = resolveAppLabel(packageName)
        val startedText = formatDateTime(startedAtMillis)
        val endedText = endedAtMillis?.let { formatDateTime(it) } ?: "未終了"
        val status = when {
            endedAtMillis != null -> SessionStatus.FINISHED
            packageName == foregroundPackage -> SessionStatus.RUNNING
            else -> SessionStatus.GRACE
        }
        val durationText = when (status) {
            SessionStatus.GRACE -> ""                          // 何も表示しない
            else -> formatDuration(this)              // RUNNING / FINISHED は表示
        }
        val pauseUiList = pauses.map { ev ->
            PauseResumeUiModel(
                pausedAtText = formatDateTime(ev.pausedAtMillis),
                resumedAtText = ev.resumedAtMillis?.let { formatDateTime(it) }
            )
        }
        return SessionUiModel(
            id = id,
            appLabel = label,
            packageName = packageName,
            startedAtText = startedText,
            endedAtText = endedText,
            durationText = durationText,
            status = status,
            pauses = pauseUiList,
        )
    }


    private fun resolveAppLabel(packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            // 取得できない場合はパッケージ名をそのまま表示
            packageName
        }
    }

    private fun formatDateTime(millis: Long): String {
        val df = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        return df.format(Date(millis))
    }

//    private fun formatTime(millis: Long): String {
//        val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
//        return df.format(Date(millis))
//    }

    private fun formatDuration(
        session: Session
    ): String {
        // ★ まず、durationMillis を優先して使う
        val baseDuration = session.durationMillis
            ?: run {
                // 古いデータなど duration が未設定の場合のフォールバック
                val end = session.endedAtMillis ?: System.currentTimeMillis()
                (end - session.startedAtMillis).coerceAtLeast(0L)
            }
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(baseDuration)
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
