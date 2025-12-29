package com.example.refocus.domain.timeline

import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.domain.repository.TimelineRepository
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「ウィンドウイベント + seed」取得と TimelineProjector 投影を，ユースケース間で共有するためのサービス．
 *
 * 目的
 * - DailyUsage / Stats / Overlay bootstrap などが，seed 取得やマージのルールを独自に持たないようにする
 * - 将来のマルチモジュール化で，タイムライン投影パイプラインの入口を 1 箇所に固定する
 *
 * 注意
 * - ここは「どの範囲を読むか（windowStart / lookback）」自体の方針までは決めない
 *   （用途により異なるため）．ただし seed の読み方と投影の入口はこのクラスに集約する．
 */
@Singleton
class TimelineProjectionService
    @Inject
    constructor(
        private val timelineRepository: TimelineRepository,
    ) {
        private val windowLoader = TimelineWindowEventsLoader(timelineRepository)

        suspend fun loadWithSeed(
            windowStartMillis: Long,
            windowEndMillis: Long,
            seedLookbackMillis: Long? = null,
        ): List<TimelineEvent> =
            windowLoader.loadWithSeed(
                windowStartMillis = windowStartMillis,
                windowEndMillis = windowEndMillis,
                seedLookbackMillis = seedLookbackMillis,
            )

        fun observeWithSeed(
            windowStartMillis: Long,
            windowEndMillis: Long,
            seedLookbackMillis: Long? = null,
        ): Flow<List<TimelineEvent>> =
            windowLoader.observeWithSeed(
                windowStartMillis = windowStartMillis,
                windowEndMillis = windowEndMillis,
                seedLookbackMillis = seedLookbackMillis,
            )

        fun project(
            events: List<TimelineEvent>,
            config: TimelineInterpretationConfig,
            nowMillis: Long,
            zoneId: ZoneId,
        ): TimelineProjection =
            TimelineProjector.project(
                events = events,
                config = config,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )
    }
