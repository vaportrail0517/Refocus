package com.example.refocus.domain.timeline

import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.domain.repository.TimelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Repository から「必要最小限の seed + ウィンドウイベント」を取得してマージするユーティリティ。
 *
 * - SessionProjector は TargetAppsChangedEvent や 直前の ForegroundAppEvent を必要とするため，
 *   観測ウィンドウの直前状態を seed として補う。
 * - seed を無制限に伸ばすと想定外の I/O が起きうるため，必要に応じて lookback をかける。
 */
class TimelineWindowEventsLoader(
    private val timelineRepository: TimelineRepository,
) {
    suspend fun loadWithSeed(
        windowStartMillis: Long,
        windowEndMillis: Long,
        seedLookbackMillis: Long? = null,
    ): List<TimelineEvent> {
        val seed = timelineRepository.getSeedEventsBefore(windowStartMillis)
        val window = timelineRepository.getEvents(windowStartMillis, windowEndMillis)

        val filteredSeed =
            if (seedLookbackMillis != null) {
                val minSeedMillis = (windowStartMillis - seedLookbackMillis).coerceAtLeast(0L)
                seed.filter { it.timestampMillis >= minSeedMillis }
            } else {
                seed
            }

        return (filteredSeed + window)
            .sortedBy { it.timestampMillis }
    }

    fun observeWithSeed(
        windowStartMillis: Long,
        windowEndMillis: Long,
        seedLookbackMillis: Long? = null,
    ): Flow<List<TimelineEvent>> =
        timelineRepository
            .observeEventsBetween(windowStartMillis, windowEndMillis)
            .mapLatest { windowEvents ->
                val seed = timelineRepository.getSeedEventsBefore(windowStartMillis)
                val filteredSeed =
                    if (seedLookbackMillis != null) {
                        val minSeedMillis = (windowStartMillis - seedLookbackMillis).coerceAtLeast(0L)
                        seed.filter { it.timestampMillis >= minSeedMillis }
                    } else {
                        seed
                    }
                (filteredSeed + windowEvents)
                    .sortedBy { it.timestampMillis }
            }
}
