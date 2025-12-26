package com.example.refocus.testutil

import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.domain.repository.TimelineRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 単体テスト用の in-memory TimelineRepository．
 * - append されたイベントは timestampMillis 昇順で保持する
 * - observe 系は必要最小限（固定値の Flow）として実装する
 */
class FakeTimelineRepository(
    initialEvents: List<TimelineEvent> = emptyList(),
) : TimelineRepository {

    private val events = initialEvents.toMutableList()

    override suspend fun append(event: TimelineEvent): Long {
        events += event
        events.sortBy { it.timestampMillis }
        return events.size.toLong()
    }

    override suspend fun getEvents(startMillis: Long, endMillis: Long): List<TimelineEvent> {
        return events
            .filter { it.timestampMillis >= startMillis && it.timestampMillis <= endMillis }
            .sortedBy { it.timestampMillis }
    }

    override suspend fun getEventsForDate(date: LocalDate, zoneId: ZoneId): List<TimelineEvent> {
        // この Fake は日付検索を使うテストを想定していないので，単純実装に留める
        return events.sortedBy { it.timestampMillis }
    }

    override fun observeEventsBetween(startMillis: Long, endMillis: Long): Flow<List<TimelineEvent>> {
        val snapshot = events
            .filter { it.timestampMillis >= startMillis && it.timestampMillis <= endMillis }
            .sortedBy { it.timestampMillis }
        return flowOf(snapshot)
    }

    override suspend fun getSeedEventsBefore(beforeMillis: Long): List<TimelineEvent> {
        return events
            .filter { it.timestampMillis < beforeMillis }
            .sortedBy { it.timestampMillis }
    }

    @Deprecated("全件購読は性能劣化の原因になるため，observeEventsBetween を使う")
    override fun observeEvents(): Flow<List<TimelineEvent>> = flowOf(events.sortedBy { it.timestampMillis })
}
