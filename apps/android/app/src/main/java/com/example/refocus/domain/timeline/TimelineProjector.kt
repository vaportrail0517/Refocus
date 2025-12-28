package com.example.refocus.domain.timeline

import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.domain.session.SessionPartGenerator
import java.time.LocalDate
import java.time.ZoneId

/**
 * TimelineEvent → (Session / SessionEvent / SessionPart / MonitoringPeriod) を投影する単一の入口。
 *
 * ここは pure Kotlin として扱えるように，Repository / Android API への依存は持たない。
 *
 * 目的
 * - セッション投影ロジックをオーバーレイ／統計／履歴が共有し，
 *   それぞれが独自解釈を持たないようにする。
 */
object TimelineProjector {
    /**
     * TimelineEvent 列から，セッションと日付境界分割（SessionPart）までを一括で構築する。
     */
    fun project(
        events: List<TimelineEvent>,
        config: TimelineInterpretationConfig,
        nowMillis: Long,
        zoneId: ZoneId,
    ): TimelineProjection {
        val sessionsWithEvents =
            SessionProjector.projectSessions(
                events = events,
                stopGracePeriodMillis = config.stopGracePeriodMillis,
                nowMillis = nowMillis,
            )

        val sessions: List<Session> = sessionsWithEvents.map { it.session }
        val eventsBySessionId: Map<Long, List<SessionEvent>> =
            sessionsWithEvents
                .mapNotNull { swe ->
                    val id = swe.session.id ?: return@mapNotNull null
                    id to swe.events
                }.toMap()

        val sessionParts: List<SessionPart> =
            SessionPartGenerator.generateParts(
                sessions = sessions,
                eventsBySessionId = eventsBySessionId,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )

        return TimelineProjection(
            sessionsWithEvents = sessionsWithEvents,
            sessions = sessions,
            eventsBySessionId = eventsBySessionId,
            sessionParts = sessionParts,
        )
    }

    /**
     * 指定日付の「監視できていた時間帯（= monitoringActive）」を再構成する。
     */
    fun buildMonitoringPeriodsForDate(
        date: LocalDate,
        zoneId: ZoneId,
        events: List<TimelineEvent>,
        nowMillis: Long,
    ): List<MonitoringPeriod> =
        MonitoringStateProjector.buildMonitoringPeriodsForDate(
            date = date,
            zoneId = zoneId,
            events = events,
            nowMillis = nowMillis,
        )
}
