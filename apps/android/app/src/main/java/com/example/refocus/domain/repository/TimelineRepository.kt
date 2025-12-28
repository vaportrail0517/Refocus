package com.example.refocus.domain.repository

import com.example.refocus.core.model.TimelineEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * タイムラインイベントの永続化と購読の抽象。
 */
interface TimelineRepository {
    suspend fun append(event: TimelineEvent): Long

    suspend fun getEvents(
        startMillis: Long,
        endMillis: Long,
    ): List<TimelineEvent>

    suspend fun getEventsForDate(
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TimelineEvent>

    /**
     * 指定区間のイベントを Flow で購読する。
     *
     * 大量データでの UI 劣化を避けるため，「全件購読」を基本禁止にし，
     * 画面ごとに必要な期間だけ購読する。
     */
    fun observeEventsBetween(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TimelineEvent>>

    /**
     * ウィンドウ購読の起点より前の状態復元に使う「種イベント」を返す。
     */
    suspend fun getSeedEventsBefore(beforeMillis: Long): List<TimelineEvent>

    /**
     * 互換用（既存コードが残っている間だけ）。
     * 新規コードでは observeEventsBetween を使う。
     */
    @Deprecated("全件購読は性能劣化の原因になるため，observeEventsBetween を使う")
    fun observeEvents(): Flow<List<TimelineEvent>>
}
