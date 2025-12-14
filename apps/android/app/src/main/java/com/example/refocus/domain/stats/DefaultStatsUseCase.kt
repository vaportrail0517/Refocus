package com.example.refocus.domain.stats

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.SessionStats
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.domain.session.SessionPartGenerator
import com.example.refocus.domain.timeline.MonitoringPeriod
import com.example.refocus.domain.timeline.SessionProjector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
     * 今日 1 日分の統計を監視する Flow。
     *
     * タイムラインイベント / 設定 / 対象アプリが更新される度に再計算される。
     */
    fun observeTodayStats(): Flow<DailyStats?> =
        combine(
            timelineRepository.observeEvents(),
            settingsRepository.observeOverlaySettings(),
            targetsRepository.observeTargets(),
        ) { events, settings, targets ->
            val nowMillis = timeSource.nowMillis()
            val today = Instant.ofEpochMilli(nowMillis)
                .atZone(zoneId)
                .toLocalDate()

            buildDailyStatsForDate(
                date = today,
                events = events,
                customize = settings,
                targets = targets,
                nowMillis = nowMillis,
            )
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
        val targets = targetsRepository.observeTargets().first()

        // その日より前に確定した状態（サービス稼働・権限・直前foreground等）も必要なので
        // 0..endOfDay まで取って「その日の解釈」を構築する
        val events = timelineRepository.getEvents(0L, endOfDayExclusive)
        if (events.isEmpty()) return null

        return buildDailyStatsForDate(
            date = date,
            events = events,
            customize = settings,
            targets = targets,
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
        targets: Set<String>,
        nowMillis: Long,
    ): DailyStats? {
        if (events.isEmpty()) return null

        // 1) セッションを再構成（SessionProjector の現状シグネチャに合わせる）
        val sessionsWithEvents = SessionProjector.projectSessions(
            events = events,
            targetPackages = targets,
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
            buildMonitoringPeriodsForDate(
                date = date,
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

    /**
     * 1 日分のタイムラインイベントから MonitoringPeriod の一覧を再構成する。
     *
     * 条件:
     * - OverlayService が稼働している
     * - 画面が ON
     * - UsageStats / Overlay の 2 権限が GRANTED
     */
    private fun buildMonitoringPeriodsForDate(
        date: LocalDate,
        events: List<TimelineEvent>,
        nowMillis: Long,
    ): List<MonitoringPeriod> {
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDayExclusive = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        var serviceRunning = false
        var screenOn = false
        val permissionStates = mutableMapOf<PermissionKind, PermissionState>()

        fun isMonitoring(): Boolean {
            val required = listOf(PermissionKind.UsageStats, PermissionKind.Overlay)
            return serviceRunning &&
                    screenOn &&
                    required.all { permissionStates[it] == PermissionState.Granted }
        }

        val periods = mutableListOf<MonitoringPeriod>()
        fun applyStateChange(event: TimelineEvent) {
            when (event) {
                is ServiceLifecycleEvent -> {
                    serviceRunning = (event.state == ServiceState.Started)
                }

                is ScreenEvent -> {
                    screenOn = (event.state == ScreenState.On)
                }

                is PermissionEvent -> {
                    permissionStates[event.permission] = event.state
                }

                else -> Unit
            }
        }
        // 1) 日付開始より前のイベントで「初期状態」を復元する
        //    （サービスが前日から動き続けている/画面ONのまま/権限Granted済み、など）
        val sorted = events.sortedBy { it.timestampMillis }
        for (event in sorted) {
            if (event.timestampMillis >= startOfDay) break
            applyStateChange(event)
        }
        // 2) 日付開始時点ですでに監視可能なら startOfDay から開始
        var monitoring = isMonitoring()
        var currentStart: Long? = if (monitoring) startOfDay else null
        // 3) 当日イベントを流して ON/OFF を切り替える
        for (event in sorted) {
            if (event.timestampMillis < startOfDay) continue
            if (event.timestampMillis >= endOfDayExclusive) break
            val wasMonitoring = monitoring
            applyStateChange(event)
            val newMonitoring = isMonitoring()
            // OFF → ON
            if (!wasMonitoring && newMonitoring) {
                currentStart = maxOf(event.timestampMillis, startOfDay)
            }
            // ON → OFF
            else if (wasMonitoring && !newMonitoring) {
                val start = currentStart ?: startOfDay
                val end = event.timestampMillis
                if (end > start) {
                    periods += MonitoringPeriod(startMillis = start, endMillis = end)
                }
                currentStart = null
            }
            monitoring = newMonitoring
        }
        // 4) 当日終端（または now）まで監視が続く場合は閉じる
        if (monitoring) {
            val start = currentStart ?: startOfDay
            val end = minOf(nowMillis, endOfDayExclusive)
            if (end > start) {
                periods += MonitoringPeriod(startMillis = start, endMillis = end)
            }
        }

        return periods
    }
}
