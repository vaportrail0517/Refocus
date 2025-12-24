package com.example.refocus.domain.overlay

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.domain.session.SessionDurationCalculator
import com.example.refocus.domain.timeline.SessionProjector

/**
 * TimelineEvent の列から，「いまの設定（停止猶予時間など）で解釈した論理セッション」を復元するユースケース。
 *
 * OverlayService の再起動や，停止猶予時間の変更直後に
 * 「ランタイムの OverlaySessionTracker を一貫した値へ追従」させるために使う。
 */
class SessionBootstrapper(
    private val timeSource: com.example.refocus.core.util.TimeSource,
    private val timelineRepository: TimelineRepository,
    private val lookbackHours: Long,
) {

    /**
     * @param force
     *   true の場合は，すでに sessionTracker に状態があっても Timeline から再計算する。
     */
    suspend fun computeBootstrapFromTimeline(
        packageName: String,
        customize: Customize,
        nowMillis: Long,
        force: Boolean,
        sessionTracker: OverlaySessionTracker,
    ): SessionBootstrapFromTimeline? {
        if (!force) {
            // ランタイムの tracker がすでにこの package を知っているなら，再注入しない。
            val nowElapsed = timeSource.elapsedRealtime()
            val already = sessionTracker.computeElapsedFor(packageName, nowElapsed)
            if (already != null) return null
        }

        val startMillis = (nowMillis - lookbackHours * 60L * 60L * 1000L)
            .coerceAtLeast(0L)

        val events = timelineRepository.getEvents(
            startMillis = startMillis,
            endMillis = nowMillis,
        )
        if (events.isEmpty()) return null

        val sessionsWithEvents = SessionProjector.projectSessions(
            events = events,
            targetPackages = setOf(packageName),
            stopGracePeriodMillis = customize.gracePeriodMillis,
            nowMillis = nowMillis,
        )

        val last = sessionsWithEvents.lastOrNull() ?: return null

        // End が入っている = 論理セッションは閉じている
        val ended = last.events.any { it.type == SessionEventType.End }
        if (ended) {
            return SessionBootstrapFromTimeline(
                initialElapsedMillis = 0L,
                isOngoingSession = false,
                gate = SessionSuggestionGate(),
            )
        }

        val duration = SessionDurationCalculator.calculateDurationMillis(
            events = last.events,
            nowMillis = nowMillis,
        ).coerceAtLeast(0L)

        val disabledForSession = last.events.any { it.type == SessionEventType.SuggestionDisabledForSession }

        val lastDecisionAt = last.events
            .filter {
                it.type == SessionEventType.SuggestionSnoozed ||
                    it.type == SessionEventType.SuggestionDismissed
            }
            .maxOfOrNull { it.timestampMillis }

        val lastDecisionElapsed = lastDecisionAt?.let { at ->
            val truncated = last.events.filter { it.timestampMillis <= at }
            SessionDurationCalculator.calculateDurationMillis(
                events = truncated,
                nowMillis = at,
            ).coerceAtLeast(0L)
        }

        return SessionBootstrapFromTimeline(
            initialElapsedMillis = duration,
            isOngoingSession = true,
            gate = SessionSuggestionGate(
                disabledForThisSession = disabledForSession,
                lastDecisionElapsedMillis = lastDecisionElapsed,
            ),
        )
    }
}
