package com.example.refocus.domain.stats

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.SessionStats
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.session.SessionPartGenerator
import com.example.refocus.domain.timeline.MonitoringPeriod
import com.example.refocus.domain.timeline.MonitoringStateProjector
import com.example.refocus.domain.timeline.SessionProjector
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultStatsUseCase @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val settingsRepository: SettingsRepository,
    private val targetsRepository: TargetsRepository,
    private val timeSource: TimeSource,
) {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    /**
     * 日次統計のために「日付境界を跨ぐセッション」や直前状態を拾うための巻き戻し幅。
     *
     * 大きすぎると集計コストが上がり，小さすぎると 0:00 直前開始のセッションが欠ける。
     * 現状は安全側に倒して 36 時間とする。
     */
    private val statsLookbackMillis: Long = 36L * 60L * 60L * 1_000L

    /**
     * 日付変化を検知するための低頻度 ticker。
     *
     * UI 表示の「今日」が日付跨ぎで壊れないための保険であり，1 秒更新は意図していない。
     */
    private fun dayFlow(tickMillis: Long = 60_000L): Flow<LocalDate> =
        flow {
            while (currentCoroutineContext().isActive) {
                emit(timeSource.nowMillis())
                delay(tickMillis)
            }
        }
            .onStart { emit(timeSource.nowMillis()) }
            .map { millis ->
                Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
            }
            .distinctUntilChanged()

    /**
     * 今日 1 日分の統計を監視する Flow。
     *
     * タイムラインイベント / 設定 / 対象アプリが更新される度に再計算される。
     */
    fun observeTodayStats(): Flow<DailyStats?> =
        combine(
            settingsRepository.observeOverlaySettings(),
            targetsRepository.observeTargets(),
            dayFlow(),
        ) { settings, targets, today ->
            Triple(settings, targets, today)
        }.flatMapLatest { (settings, targets, today) ->
            val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDayExclusive = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val windowStart = (startOfDay - statsLookbackMillis).coerceAtLeast(0L)

            timelineRepository.observeEventsBetween(windowStart, endOfDayExclusive)
                .mapLatest { windowEvents ->
                    val seed = timelineRepository.getSeedEventsBefore(windowStart)
                    val merged = mergeSeedAndWindow(seed, windowEvents)

                    val nowMillis = timeSource.nowMillis()
                    val nowMillisForDay = minOf(nowMillis, endOfDayExclusive)

                    buildDailyStatsForDate(
                        date = today,
                        events = merged,
                        customize = settings,
                        // セッション投影は TargetAppsChangedEvent から「当時の対象集合」を復元するため，
            // 現在の targets をフィルタとして渡さない。
                        nowMillis = nowMillisForDay,
                    )
                }
        }

    /**
     * 任意の日付の統計を 1 回だけ計算する。
     *
     * 履歴画面などで「指定日を開いたときに計算」する用途を想定。
     */
    suspend fun calculateDailyStatsForDate(date: LocalDate): DailyStats? {
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDayExclusive = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        // 「その日の終わり」か「今時点」のうち早い方を上限として扱う
        // endOfDayExclusive は「翌日の 0:00（排他的）」として扱う
        val nowMillisForDay = minOf(timeSource.nowMillis(), endOfDayExclusive)

        val settings = settingsRepository.observeOverlaySettings().first()

        // その日より前に確定した状態（サービス稼働・権限・直前foreground等）を拾うため，
        // 「日付境界を跨ぐ可能性がある範囲」だけ取得し，直前の状態イベントを seed として補う。
        val windowStart = (startOfDay - statsLookbackMillis).coerceAtLeast(0L)
        val seed = timelineRepository.getSeedEventsBefore(windowStart)
        val windowEvents = timelineRepository.getEvents(windowStart, endOfDayExclusive)
        val events = mergeSeedAndWindow(seed, windowEvents)
        if (events.isEmpty()) return null

        return buildDailyStatsForDate(
            date = date,
            events = events,
            customize = settings,
            // セッション投影は TargetAppsChangedEvent から「当時の対象集合」を復元するため，
            // 現在の targets をフィルタとして渡さない。
            nowMillis = nowMillisForDay,
        )
    }

    /**
     * 共通の「日次統計計算」の本体。
     */
    private fun buildDailyStatsForDate(
        date: LocalDate,
        events: List<TimelineEvent>,
        customize: Customize,
        nowMillis: Long,
    ): DailyStats? {
        if (events.isEmpty()) return null

        // 1) セッションを再構成（SessionProjector の現状シグネチャに合わせる）
        val sessionsWithEvents = SessionProjector.projectSessions(
            events = events,
            stopGracePeriodMillis = customize.gracePeriodMillis,
            nowMillis = nowMillis,
        )

        val sessions: List<Session> = sessionsWithEvents.map { it.session }
        val eventsBySessionId: Map<Long, List<SessionEvent>> =
            sessionsWithEvents.mapNotNull { swe ->
                val id = swe.session.id ?: return@mapNotNull null
                id to swe.events
            }.toMap()

        // 2) SessionStats を生成（SessionStatsCalculator のシグネチャに合わせる）
        // foregroundPackage は日次集計では不要なので null にしている
        val sessionStats: List<SessionStats> =
            SessionStatsCalculator.buildSessionStats(
                sessions = sessions,
                eventsMap = eventsBySessionId,
                foregroundPackage = null,
                nowMillis = nowMillis,
            )

        // 3) SessionPart を生成（日付境界の分割は SessionPartGenerator に一本化）
        val sessionParts: List<SessionPart> =
            SessionPartGenerator.generateParts(
                sessions = sessions,
                eventsBySessionId = eventsBySessionId,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )

        // 4) 監視期間を日ベースで再構成
        val monitoringPeriods: List<MonitoringPeriod> =
            MonitoringStateProjector.buildMonitoringPeriodsForDate(
                date = date,
                zoneId = zoneId,
                events = events,
                nowMillis = nowMillis,
            )

        // 対象アプリ利用も監視もまったく無い日は null を返す
        if (sessionParts.isEmpty() && monitoringPeriods.isEmpty()) {
            return null
        }

        // 5) DailyStats を計算（DailyStatsCalculator のシグネチャに完全一致させる）
        return DailyStatsCalculator.calculateDailyStats(
            sessions = sessions,
            sessionStats = sessionStats,
            sessionParts = sessionParts,
            eventsBySessionId = eventsBySessionId,
            monitoringPeriods = monitoringPeriods,
            targetDate = date,
            zoneId = zoneId,
            nowMillis = nowMillis,
        )
    }

    private fun mergeSeedAndWindow(
        seedEvents: List<TimelineEvent>,
        windowEvents: List<TimelineEvent>,
    ): List<TimelineEvent> {
        if (seedEvents.isEmpty()) return windowEvents
        if (windowEvents.isEmpty()) return seedEvents
        // seed は windowStart より前のみのため，単純連結で良い。
        // 念のため時系列に整列する。
        return (seedEvents + windowEvents).sortedBy { it.timestampMillis }
    }
}
