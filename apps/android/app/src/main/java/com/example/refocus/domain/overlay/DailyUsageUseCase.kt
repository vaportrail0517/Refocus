package com.example.refocus.domain.overlay

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.domain.session.SessionDurationCalculator
import com.example.refocus.domain.timeline.SessionProjector
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「今日の累計使用時間」を計算し，UI（オーバーレイ／通知）へ提供するためのユースケース．
 *
 * - DB 参照を毎フレーム叩かないように，1 秒程度の粒度でスナップショットをキャッシュする
 * - 停止猶予時間や対象アプリ集合の変更に追従するため，TimelineEvent から毎回再構成する
 */
class DailyUsageUseCase(
    private val scope: CoroutineScope,
    private val timeSource: com.example.refocus.core.util.TimeSource,
    private val timelineRepository: TimelineRepository,
    private val lookbackHours: Long,
) {

    companion object {
        private const val TAG = "DailyUsageUseCase"
        private const val CACHE_TTL_MILLIS: Long = 1_000L
    }

    private data class DailyUsageSnapshot(
        val computedAtMillis: Long,
        val dayStartMillis: Long,
        val perPackageMillis: Map<String, Long>,
        val allTargetsMillis: Long,
    )

    @Volatile
    private var snapshot: DailyUsageSnapshot? = null

    @Volatile
    private var refreshJob: Job? = null

    fun invalidate() {
        snapshot = null
    }

    fun getTodayThisTargetMillis(packageName: String): Long {
        return snapshot?.perPackageMillis?.get(packageName) ?: 0L
    }

    fun getTodayAllTargetsMillis(): Long {
        return snapshot?.allTargetsMillis ?: 0L
    }

    /**
     * 同期的に値が欲しい箇所（通知更新など）用。
     * 必要ならバックグラウンドで更新を要求するだけで，ここでは suspend しない。
     */
    fun requestRefreshIfNeeded(
        customize: Customize,
        targetPackages: Set<String>,
    ) {
        if (customize.timerTimeMode == TimerTimeMode.SessionElapsed) return

        val nowMillis = timeSource.nowMillis()
        if (isSnapshotFresh(nowMillis)) return

        val job = refreshJob
        if (job != null && job.isActive) return

        refreshJob = scope.launch {
            try {
                refreshIfNeeded(
                    customize = customize,
                    targetPackages = targetPackages,
                    nowMillis = nowMillis,
                )
            } catch (e: Exception) {
                RefocusLog.w(TAG, e) { "refreshIfNeeded failed" }
            } finally {
                refreshJob = null
            }
        }
    }

    suspend fun refreshIfNeeded(
        customize: Customize,
        targetPackages: Set<String>,
        nowMillis: Long,
    ) {
        if (customize.timerTimeMode == TimerTimeMode.SessionElapsed) return

        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val startOfDayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

        if (isSnapshotFresh(nowMillis) && snapshot?.dayStartMillis == startOfDayMillis) return

        if (targetPackages.isEmpty()) {
            snapshot = DailyUsageSnapshot(
                computedAtMillis = nowMillis,
                dayStartMillis = startOfDayMillis,
                perPackageMillis = emptyMap(),
                allTargetsMillis = 0L,
            )
            return
        }

        // 日付またぎや猶予時間を考慮して，少し前からイベントを取ってくる
        val lookbackStartMillis = startOfDayMillis - lookbackHours * 60L * 60L * 1_000L

        val events = withContext(Dispatchers.IO) {
            timelineRepository.getEvents(lookbackStartMillis, nowMillis)
        }

        val sessions = SessionProjector.projectSessions(
            events = events,
            targetPackages = targetPackages,
            stopGracePeriodMillis = customize.gracePeriodMillis,
            nowMillis = nowMillis,
        )

        val perPkg = mutableMapOf<String, Long>()
        var all = 0L

        for (s in sessions) {
            val pkg = s.session.packageName
            val durationToday = computeTodayDurationMillis(
                sessionEvents = s.events,
                nowMillis = nowMillis,
                startOfDayMillis = startOfDayMillis,
            )
            if (durationToday <= 0L) continue
            perPkg[pkg] = (perPkg[pkg] ?: 0L) + durationToday
            all += durationToday
        }

        snapshot = DailyUsageSnapshot(
            computedAtMillis = nowMillis,
            dayStartMillis = startOfDayMillis,
            perPackageMillis = perPkg.toMap(),
            allTargetsMillis = all,
        )
    }

    private fun isSnapshotFresh(nowMillis: Long): Boolean {
        val existing = snapshot ?: return false
        return (nowMillis - existing.computedAtMillis) < CACHE_TTL_MILLIS
    }

    private fun computeTodayDurationMillis(
        sessionEvents: List<com.example.refocus.core.model.SessionEvent>,
        nowMillis: Long,
        startOfDayMillis: Long,
    ): Long {
        val segments = SessionDurationCalculator.buildActiveSegments(
            events = sessionEvents,
            nowMillis = nowMillis,
        )

        var sum = 0L
        for (seg in segments) {
            val start = maxOf(seg.startMillis, startOfDayMillis)
            val end = seg.endMillis
            if (end > start) sum += (end - start)
        }
        return sum.coerceAtLeast(0L)
    }
}
