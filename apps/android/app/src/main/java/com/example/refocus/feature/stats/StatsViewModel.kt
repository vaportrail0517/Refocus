package com.example.refocus.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SessionsWithEvents
import com.example.refocus.domain.session.SessionPartGenerator
import com.example.refocus.domain.stats.DailyStatsCalculator
import com.example.refocus.domain.stats.SessionStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 将来の拡張を見据えて、期間種別を enum で定義しておく */
enum class StatsRange {
    Today,
    // 今後追加: Last7Days, Last30Days など
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val statsRange: StatsRange = StatsRange.Today,
        val selectedDate: LocalDate = LocalDate.now(),
        val dateLabel: String = "今日",
        val todayStats: DailyStats? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val zoneId: ZoneId = ZoneId.systemDefault()

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            sessionRepository.observeAllSessionsWithEvents()
                .collectLatest { sessionsWithEvents ->
                    buildTodayStats(sessionsWithEvents)
                }
        }
    }

    private fun buildTodayStats(
        sessionsWithEvents: SessionsWithEvents,
    ) {
        val nowMillis = timeSource.nowMillis()
        val today = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()

        val sessions = sessionsWithEvents.sessions
        val eventsBySessionId: Map<Long, List<SessionEvent>> =
            sessionsWithEvents.eventsBySessionId

        if (sessions.isEmpty()) {
            _uiState.value = UiState(
                isLoading = false,
                statsRange = StatsRange.Today,
                selectedDate = today,
                dateLabel = "今日",
                todayStats = DailyStats(
                    date = today,
                    sessionCount = 0,
                    averageSessionDurationMillis = 0L,
                    longestSessionDurationMillis = 0L,
                    totalUsageMillis = 0L,
                    appUsageStats = emptyList(),
                    timeBuckets = emptyList(),
                    suggestionStats = null,
                ),
            )
            return
        }

        // 1. セッション単位の統計 (開始/終了/長さなど)
        val sessionStats = SessionStatsCalculator.buildSessionStats(
            sessions = sessions,
            eventsMap = eventsBySessionId,
            foregroundPackage = null,       // 統計画面では RUNNING/GRACE の区別は不要
            nowMillis = nowMillis,
        )

        // 2. 日付跨ぎを考慮した SessionPart を生成
        val sessionParts = SessionPartGenerator.generateParts(
            sessions = sessions,
            eventsBySessionId = eventsBySessionId,
            nowMillis = nowMillis,
            zoneId = zoneId,
        )

        // 3. 1 日分の DailyStats を計算
        val dailyStats = DailyStatsCalculator.calculateDailyStats(
            sessions = sessions,
            sessionStats = sessionStats,
            sessionParts = sessionParts,
            eventsBySessionId = eventsBySessionId,
            targetDate = today,
            zoneId = zoneId,
        )

        _uiState.value = UiState(
            isLoading = false,
            statsRange = StatsRange.Today,
            selectedDate = today,
            dateLabel = "今日",
            todayStats = dailyStats,
        )
    }
}
