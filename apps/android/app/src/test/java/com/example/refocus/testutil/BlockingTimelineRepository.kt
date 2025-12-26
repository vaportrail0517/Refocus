package com.example.refocus.testutil

import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.domain.repository.TimelineRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * テスト用の「ブロッキング可能な」TimelineRepository．
 *
 * refresh ジョブが IO スレッド側で getEvents を待つ状況を作り，
 * refresh 実行中の onTick（ランタイム加算）との競合を再現するために使う．
 */
class BlockingTimelineRepository(
    initialEvents: List<TimelineEvent> = emptyList(),
    val gate: CompletableDeferred<Unit> = CompletableDeferred<Unit>(),
) : TimelineRepository {

    private val events = initialEvents.toMutableList().apply { sortBy { it.timestampMillis } }

    override suspend fun append(event: TimelineEvent): Long {
        events += event
        events.sortBy { it.timestampMillis }
        return events.size.toLong()
    }

    override suspend fun getEvents(startMillis: Long, endMillis: Long): List<TimelineEvent> {
        gate.await()
        return events
            .filter { it.timestampMillis >= startMillis && it.timestampMillis <= endMillis }
            .sortedBy { it.timestampMillis }
    }

    override suspend fun getEventsForDate(date: LocalDate, zoneId: ZoneId): List<TimelineEvent> {
        gate.await()
        // 日付でフィルタする必要があるテストでは，getEvents を使う．
        // ここは「refresh がブロックされる」状況を作るための最小実装に留める．
        return events.sortedBy { it.timestampMillis }
    }

    override fun observeEventsBetween(startMillis: Long, endMillis: Long): Flow<List<TimelineEvent>> {
        val snapshot = events
            .filter { it.timestampMillis >= startMillis && it.timestampMillis <= endMillis }
            .sortedBy { it.timestampMillis }
        return flowOf(snapshot)
    }

    override suspend fun getSeedEventsBefore(beforeMillis: Long): List<TimelineEvent> {
        // seed は即時で返し，メインのイベント取得だけをブロックする
        return events
            .filter { it.timestampMillis < beforeMillis }
            .sortedBy { it.timestampMillis }
    }

    @Deprecated("全件購読は性能劣化の原因になるため，observeEventsBetween を使う")
    override fun observeEvents(): Flow<List<TimelineEvent>> = flowOf(events.sortedBy { it.timestampMillis })
}
