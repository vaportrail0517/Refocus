package com.example.refocus.domain.timeline

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
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
    private companion object {
        private const val TAG = "TimelineWindowEventsLoader"
    }

    private fun isLookbackExemptSeed(event: TimelineEvent): Boolean = event is TargetAppsChangedEvent || event is ForegroundAppEvent

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

                // 重要: TargetAppsChangedEvent / ForegroundAppEvent は
                // ウィンドウ内の ForegroundAppEvent を正しく解釈するために必要な seed であり，
                // lookback で落とすとセッションが 0 件になるなどの破綻が起き得る．
                val exempted =
                    seed.filter { e ->
                        e.timestampMillis < minSeedMillis && isLookbackExemptSeed(e)
                    }
                val filtered =
                    seed.filter { e ->
                        e.timestampMillis >= minSeedMillis || isLookbackExemptSeed(e)
                    }

                if (exempted.isNotEmpty()) {
                    val exemptedKinds = exempted.joinToString { it.javaClass.simpleName }
                    RefocusLog.d(TAG) {
                        "seedLookback kept exempt seed(s): " +
                            "lookbackMs=$seedLookbackMillis " +
                            "windowStart=$windowStartMillis " +
                            "minSeed=$minSeedMillis " +
                            "exempted=${exempted.size} " +
                            "kinds=[$exemptedKinds]"
                    }
                }

                if (filtered.size != seed.size) {
                    val dropped =
                        seed.filter { e ->
                            e.timestampMillis < minSeedMillis && !isLookbackExemptSeed(e)
                        }
                    val droppedTargetApps = dropped.any { it is TargetAppsChangedEvent }
                    val droppedForeground = dropped.any { it is ForegroundAppEvent }
                    val oldestDroppedMillis = dropped.minOfOrNull { it.timestampMillis }
                    val newestDroppedMillis = dropped.maxOfOrNull { it.timestampMillis }

                    val msg =
                        "seedLookback filtered seed: " +
                            "lookbackMs=$seedLookbackMillis " +
                            "windowStart=$windowStartMillis " +
                            "minSeed=$minSeedMillis " +
                            "seed=${seed.size} " +
                            "kept=${filtered.size} " +
                            "dropped=${dropped.size} " +
                            "droppedTargetApps=$droppedTargetApps " +
                            "droppedForeground=$droppedForeground " +
                            "droppedRange=[${oldestDroppedMillis ?: "-"}, ${newestDroppedMillis ?: "-"}]"

                    if (droppedTargetApps) {
                        // TargetAppsChangedEvent が落ちると，投影で「対象集合が空」になり得るため，特に目立つようにする．
                        RefocusLog.wRateLimited(
                            TAG,
                            key = "seedLookback_dropped_targetApps",
                            intervalMs = 10 * 60_000L,
                        ) { msg }
                    } else {
                        RefocusLog.d(TAG) { msg }
                    }
                }

                filtered
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

                        val exempted =
                            seed.filter { e ->
                                e.timestampMillis < minSeedMillis && isLookbackExemptSeed(e)
                            }
                        val filtered =
                            seed.filter { e ->
                                e.timestampMillis >= minSeedMillis || isLookbackExemptSeed(e)
                            }

                        if (exempted.isNotEmpty()) {
                            val exemptedKinds = exempted.joinToString { it.javaClass.simpleName }
                            RefocusLog.d(TAG) {
                                "seedLookback kept exempt seed(s) (observe): " +
                                    "lookbackMs=$seedLookbackMillis " +
                                    "windowStart=$windowStartMillis " +
                                    "minSeed=$minSeedMillis " +
                                    "exempted=${exempted.size} " +
                                    "kinds=[$exemptedKinds]"
                            }
                        }

                        if (filtered.size != seed.size) {
                            val dropped =
                                seed.filter { e ->
                                    e.timestampMillis < minSeedMillis && !isLookbackExemptSeed(e)
                                }
                            val droppedTargetApps = dropped.any { it is TargetAppsChangedEvent }
                            val droppedForeground = dropped.any { it is ForegroundAppEvent }
                            val oldestDroppedMillis = dropped.minOfOrNull { it.timestampMillis }
                            val newestDroppedMillis = dropped.maxOfOrNull { it.timestampMillis }

                            val msg =
                                "seedLookback filtered seed (observe): " +
                                    "lookbackMs=$seedLookbackMillis " +
                                    "windowStart=$windowStartMillis " +
                                    "minSeed=$minSeedMillis " +
                                    "seed=${seed.size} " +
                                    "kept=${filtered.size} " +
                                    "dropped=${dropped.size} " +
                                    "droppedTargetApps=$droppedTargetApps " +
                                    "droppedForeground=$droppedForeground " +
                                    "droppedRange=[${oldestDroppedMillis ?: "-"}, ${newestDroppedMillis ?: "-"}]"

                            if (droppedTargetApps) {
                                RefocusLog.wRateLimited(
                                    TAG,
                                    key = "seedLookback_dropped_targetApps_observe",
                                    intervalMs = 10 * 60_000L,
                                ) { msg }
                            } else {
                                RefocusLog.d(TAG) { msg }
                            }
                        }

                        filtered
                    } else {
                        seed
                    }
                (filteredSeed + windowEvents)
                    .sortedBy { it.timestampMillis }
            }
}
