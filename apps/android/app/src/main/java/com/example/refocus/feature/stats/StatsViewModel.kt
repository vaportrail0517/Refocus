package com.example.refocus.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.stats.Stats
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

/** 将来の Last7Days / Last30Days などを見据えた期間種別 */
enum class StatsRange {
    Today,
    // 今後: Last7Days, Last30Days などを追加
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val stats: Stats,
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
        observeTodayStats()
    }

    private fun observeTodayStats() {
        viewModelScope.launch {
            stats.observeTodayStats()
                .collectLatest { dailyStats ->
                    val nowMillis = timeSource.nowMillis()
                    val today = Instant.ofEpochMilli(nowMillis)
                        .atZone(zoneId)
                        .toLocalDate()

                    val targetDate = dailyStats?.date ?: today
                    val dateLabel = if (targetDate == today) {
                        "今日"
                    } else {
                        // ここは将来、ローカライズした日付フォーマットに差し替えても良い
                        targetDate.toString()
                    }

                    _uiState.value = UiState(
                        isLoading = false,
                        statsRange = StatsRange.Today,
                        selectedDate = targetDate,
                        dateLabel = dateLabel,
                        todayStats = dailyStats,
                    )
                }
        }
    }
}
