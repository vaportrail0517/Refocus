package com.example.refocus.domain.overlay.orchestration

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.util.MILLIS_PER_HOUR
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.model.SessionBootstrapFromTimeline
import com.example.refocus.domain.overlay.model.SessionSuggestionGate
import com.example.refocus.domain.session.SessionDurationCalculator
import com.example.refocus.domain.timeline.TimelineInterpretationConfig
import com.example.refocus.domain.timeline.TimelineProjectionService
import java.time.ZoneId

/**
 * TimelineEvent の列から，「いまの設定（停止猶予時間など）で解釈した論理セッション」を復元するユースケース。
 *
 * OverlayService の再起動や，停止猶予時間の変更直後に
 * 「ランタイムの OverlaySessionTracker を一貫した値へ追従」させるために使う。
 */
class SessionBootstrapper(
    private val timeSource: TimeSource,
    private val timelineProjectionService: TimelineProjectionService,
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

        val startMillis =
            (nowMillis - lookbackHours * MILLIS_PER_HOUR)
                .coerceAtLeast(0L)

        // startMillis より前の TargetAppsChanged などを seed として補い，
        // ウィンドウ内の ForegroundAppEvent を「当時の対象集合」で解釈できるようにする。
        // 重要: seed の TargetAppsChangedEvent を lookback で落とすと，セッションが一切組み立たず
        // bootstrap が失敗し得る（端末再起動・サービス再起動でタイマーが 0 に戻る原因）．
        // seed は種類ごとに最大 1 件程度なので，ここでは lookback をかけない．
        val events =
            timelineProjectionService.loadWithSeed(
                windowStartMillis = startMillis,
                windowEndMillis = nowMillis,
            )
        if (events.isEmpty()) return null

        val projection =
            timelineProjectionService.project(
                events = events,
                config = TimelineInterpretationConfig(stopGracePeriodMillis = customize.gracePeriodMillis),
                nowMillis = nowMillis,
                zoneId = ZoneId.systemDefault(),
            )

        val last =
            projection.sessionsWithEvents
                .filter { it.session.packageName == packageName }
                .lastOrNull() ?: return null

        // End が入っている = 論理セッションは閉じている
        val ended = last.events.any { it.type == SessionEventType.End }
        if (ended) {
            return SessionBootstrapFromTimeline(
                initialElapsedMillis = 0L,
                isOngoingSession = false,
                gate = SessionSuggestionGate(),
            )
        }

        val duration =
            SessionDurationCalculator
                .calculateDurationMillis(
                    events = last.events,
                    nowMillis = nowMillis,
                ).coerceAtLeast(0L)

        val lastDecisionAt =
            last.events
                .filter {
                    it.type == SessionEventType.SuggestionSnoozed ||
                        it.type == SessionEventType.SuggestionDismissed ||
                        it.type == SessionEventType.SuggestionOpened ||
                        it.type == SessionEventType.SuggestionDisabledForSession
                }.maxOfOrNull { it.timestampMillis }

        val lastDecisionElapsed =
            lastDecisionAt?.let { at ->
                val truncated = last.events.filter { it.timestampMillis <= at }
                SessionDurationCalculator
                    .calculateDurationMillis(
                        events = truncated,
                        nowMillis = at,
                    ).coerceAtLeast(0L)
            }

        return SessionBootstrapFromTimeline(
            initialElapsedMillis = duration,
            isOngoingSession = true,
            gate =
                SessionSuggestionGate(
                    lastDecisionElapsedMillis = lastDecisionElapsed,
                ),
        )
    }
}
