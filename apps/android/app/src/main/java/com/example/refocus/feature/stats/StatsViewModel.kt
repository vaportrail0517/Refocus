package com.example.refocus.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.stats.Stats
import com.example.refocus.system.appinfo.AppLabelResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val appLabelResolver: AppLabelResolver,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val statsRange: StatsRange = StatsRange.Today,
        val selectedDate: LocalDate = LocalDate.now(),
        val dateLabel: String = "今日",
        val todayStats: DailyStats? = null,
        val appLabelByPackage: Map<String, String> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val zoneId: ZoneId = ZoneId.systemDefault()

    init {
        observeTodayStats()
    }

    private fun observeTodayStats() {
        viewModelScope.launch {
            stats.observeTodayStats().collectLatest { dailyStats ->
                val nowMillis = timeSource.nowMillis()
                val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
                val targetDate = dailyStats?.date ?: today
                val dateLabel = if (targetDate == today) "今日" else targetDate.toString()

                // 追加：統計に出てくる package を必要分だけ解決（キャッシュ維持）
                val packages =
                    dailyStats?.appUsageStats?.map { it.packageName }.orEmpty().distinct()
                val prevMap = _uiState.value.appLabelByPackage

                val newMap = withContext(Dispatchers.Default) {
                    buildMap {
                        putAll(prevMap)
                        for (pkg in packages) {
                            if (!containsKey(pkg)) {
                                put(pkg, appLabelResolver.labelOf(pkg))
                            }
                        }
                    }
                }

                _uiState.value = UiState(
                    isLoading = false,
                    statsRange = StatsRange.Today,
                    selectedDate = targetDate,
                    dateLabel = dateLabel,
                    todayStats = dailyStats,
                    appLabelByPackage = newMap,
                )
            }
        }
    }
}
