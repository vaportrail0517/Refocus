package com.example.refocus.testutil

import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.domain.repository.TimelineRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * テスト用の「キャンセルされてもブロックし続ける」TimelineRepository．
 *
 * DailyUsageUseCase.invalidate() で refreshJob がキャンセルされても，
 * getEvents が NonCancellable で待ち続けることで，
 * 「古いジョブの finally が遅れて走る」状況を確実に作る．
 *
 * さらに 2 回目の getEvents は gate 解放後もしばらく遅延させ，
 * 1 回目の finally が 2 回目実行中に走る時間窓を作る．
 */
class NonCancellableBlockingTimelineRepository(
    initialEvents: List<TimelineEvent> = emptyList(),
    val gate: CompletableDeferred<Unit> = CompletableDeferred<Unit>(),
    private val secondCallExtraDelayMillis: Long = 350L,
) : TimelineRepository {
    private val events = initialEvents.toMutableList().apply { sortBy { it.timestampMillis } }

    /** getEvents が呼ばれた回数（このクラスでは getEvents のみをカウントする） */
    val getEventsCallCount: AtomicInteger = AtomicInteger(0)

    override suspend fun append(event: TimelineEvent): Long {
        events += event
        events.sortBy { it.timestampMillis }
        return events.size.toLong()
    }

    override suspend fun getEvents(
        startMillis: Long,
        endMillis: Long,
    ): List<TimelineEvent> {
        val callIndex = getEventsCallCount.incrementAndGet()

        // キャンセルされても gate.await が解除されるまで進まない状態を作る
        withContext(NonCancellable) {
            gate.await()
            // 2 回目だけ追加で遅延させて，1 回目の finally が 2 回目実行中に走る時間窓を作る
            if (callIndex == 2) {
                delay(secondCallExtraDelayMillis)
            }
        }

        return events
            .filter { it.timestampMillis >= startMillis && it.timestampMillis <= endMillis }
            .sortedBy { it.timestampMillis }
    }

    override suspend fun getEventsForDate(
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TimelineEvent> {
        // このテスト用Repoでは日付検索の厳密な実装は不要なので単純実装に留める
        return events.sortedBy { it.timestampMillis }
    }

    override fun observeEventsBetween(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TimelineEvent>> {
        val snapshot =
            events
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
