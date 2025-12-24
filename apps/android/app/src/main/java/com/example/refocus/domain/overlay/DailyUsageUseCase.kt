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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「今日の累計使用時間」を計算し，UI（オーバーレイ／通知）へ提供するためのユースケース．
 *
 * 設計の狙い
 * - DB 参照＋セッション投影（重い処理）は「スナップショット」として一定周期でキャッシュする
 * - 表示上の 1 秒更新は「ランタイム加算」（直近の前面継続分のみ）で賄い，毎秒 DB を叩かない
 *
 * 注意
 * - ランタイム加算は UI を滑らかにするためのベストエフォートであり，
 *   正確な履歴・統計はあくまでタイムラインイベントからの再投影結果を正とする．
 */
class DailyUsageUseCase(
    private val scope: CoroutineScope,
    private val timeSource: com.example.refocus.core.util.TimeSource,
    private val timelineRepository: TimelineRepository,
    private val lookbackHours: Long,
) {

    companion object {
        private const val TAG = "DailyUsageUseCase"

        /**
         * スナップショット（DB 参照＋投影）の最短更新間隔．
         *
         * ここを短くしすぎると DB と CPU が燃えるため，UI の 1 秒更新はランタイム加算で吸収する．
         */
        private const val SNAPSHOT_TTL_MILLIS: Long = 30_000L

        /**
         * 何らかの理由（プロセス停止，スケジューラ遅延など）で tick 間隔が大きく空いた場合に，
         * ランタイム加算が過大計上しないように上限を設ける．
         */
        private const val MAX_RUNTIME_STEP_MILLIS: Long = 2_000L
    }

    private data class DailyUsageSnapshot(
        val computedAtMillis: Long,
        val dayStartMillis: Long,
        val gracePeriodMillis: Long,
        val targetPackages: Set<String>,
        val perPackageMillis: Map<String, Long>,
        val allTargetsMillis: Long,
    )

    private data class RuntimeDeltaState(
        val lastTickMillis: Long?,
        val activePackageName: String?,
        val perPackageMillis: Map<String, Long>,
        val allTargetsMillis: Long,
    ) {
        companion object {
            fun empty(): RuntimeDeltaState = RuntimeDeltaState(
                lastTickMillis = null,
                activePackageName = null,
                perPackageMillis = emptyMap(),
                allTargetsMillis = 0L,
            )
        }
    }

    private val lock = Any()

    @Volatile
    private var snapshot: DailyUsageSnapshot? = null

    @Volatile
    private var refreshJob: Job? = null

    // スナップショット以降の「ランタイム加算分」
    private var runtimeDelta: RuntimeDeltaState = RuntimeDeltaState.empty()

    fun invalidate() {
        synchronized(lock) {
            snapshot = null
            runtimeDelta = RuntimeDeltaState.empty()
        }
    }

    /**
     * 1 秒 tick などから呼び出して，ランタイム加算を更新する．
     *
     * - activePackageName: 画面ON時の前面パッケージ（対象外や画面OFFなら null）
     */
    fun onTick(
        customize: Customize,
        targetPackages: Set<String>,
        activePackageName: String?,
        nowMillis: Long = timeSource.nowMillis(),
    ) {
        if (customize.timerTimeMode == TimerTimeMode.SessionElapsed) {
            // 日次モードを使っていないなら加算も更新も不要だが，
            // 復帰時に過大 dt を作らないため lastTick だけ進める．
            synchronized(lock) {
                runtimeDelta = runtimeDelta.copy(
                    lastTickMillis = nowMillis,
                    activePackageName = null,
                )
            }
            return
        }

        // 日付またぎを検知したら即リセット（跨ぎ dt の混入を防ぐ）
        val dayStartMillis = computeStartOfDayMillis(nowMillis)
        val needsDayReset = snapshot?.dayStartMillis?.let { it != dayStartMillis } ?: false
        if (needsDayReset) {
            invalidate()
        }

        // スナップショットが必要ならバックグラウンドで更新
        requestRefreshIfNeeded(
            customize = customize,
            targetPackages = targetPackages,
            nowMillis = nowMillis,
        )

        // 直近 1 秒の分だけを加算
        accumulateRuntimeDelta(
            targetPackages = targetPackages,
            newActivePackageName = activePackageName,
            nowMillis = nowMillis,
        )
    }

    fun getTodayThisTargetMillis(packageName: String): Long {
        synchronized(lock) {
            val base = snapshot?.perPackageMillis?.get(packageName) ?: 0L
            val delta = runtimeDelta.perPackageMillis[packageName] ?: 0L
            return base + delta
        }
    }

    fun getTodayAllTargetsMillis(): Long {
        synchronized(lock) {
            val base = snapshot?.allTargetsMillis ?: 0L
            val delta = runtimeDelta.allTargetsMillis
            return base + delta
        }
    }

    /**
     * 同期的に値が欲しい箇所（通知更新など）用。
     * 必要ならバックグラウンドで更新を要求するだけで，ここでは suspend しない。
     */
    fun requestRefreshIfNeeded(
        customize: Customize,
        targetPackages: Set<String>,
        nowMillis: Long = timeSource.nowMillis(),
    ) {
        if (customize.timerTimeMode == TimerTimeMode.SessionElapsed) return

        if (!needsSnapshotRefresh(customize, targetPackages, nowMillis)) return

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

    /**
     * スナップショット（DB 参照＋投影）を必要なときだけ更新する．
     *
     * 呼び出し側は高頻度で呼んでもよいが，内部で TTL とキーで抑制すること．
     */
    suspend fun refreshIfNeeded(
        customize: Customize,
        targetPackages: Set<String>,
        nowMillis: Long,
    ) {
        if (customize.timerTimeMode == TimerTimeMode.SessionElapsed) return
        if (!needsSnapshotRefresh(customize, targetPackages, nowMillis)) return

        val startOfDayMillis = computeStartOfDayMillis(nowMillis)

        if (targetPackages.isEmpty()) {
            synchronized(lock) {
                snapshot = DailyUsageSnapshot(
                    computedAtMillis = nowMillis,
                    dayStartMillis = startOfDayMillis,
                    gracePeriodMillis = customize.gracePeriodMillis,
                    targetPackages = emptySet(),
                    perPackageMillis = emptyMap(),
                    allTargetsMillis = 0L,
                )
                // 以降はスナップショット起点で加算したいので，ランタイム加算をリセットする
                runtimeDelta = runtimeDelta.copy(
                    lastTickMillis = nowMillis,
                    perPackageMillis = emptyMap(),
                    allTargetsMillis = 0L,
                )
            }
            return
        }

        // startOfDay 以前の状態復元に必要な「種イベント」＋ 今日のイベントだけを読む．
        // 大量データの全再投影を避けるため，seed は必要最小限にする．
        val (seed, todayEvents) = withContext(Dispatchers.IO) {
            val seedEvents = timelineRepository.getSeedEventsBefore(startOfDayMillis)
            val events = timelineRepository.getEvents(startOfDayMillis, nowMillis)
            seedEvents to events
        }

        // 異常に古い seed を避ける（保険）
        val maxLookbackMillis = lookbackHours * 60L * 60L * 1_000L
        val minSeedMillis = startOfDayMillis - maxLookbackMillis
        val filteredSeed = seed.filter { it.timestampMillis >= minSeedMillis }

        val events = (filteredSeed + todayEvents)
            .sortedBy { it.timestampMillis }

        val (perPkg, all) = withContext(Dispatchers.Default) {
            val sessions = SessionProjector.projectSessions(
                events = events,
                targetPackages = targetPackages,
                stopGracePeriodMillis = customize.gracePeriodMillis,
                nowMillis = nowMillis,
            )

            val per = mutableMapOf<String, Long>()
            var total = 0L

            for (s in sessions) {
                val pkg = s.session.packageName
                val durationToday = computeTodayDurationMillis(
                    sessionEvents = s.events,
                    nowMillis = nowMillis,
                    startOfDayMillis = startOfDayMillis,
                )
                if (durationToday <= 0L) continue
                per[pkg] = (per[pkg] ?: 0L) + durationToday
                total += durationToday
            }

            per.toMap() to total
        }

        val activePkg = synchronized(lock) { runtimeDelta.activePackageName }
        synchronized(lock) {
            snapshot = DailyUsageSnapshot(
                computedAtMillis = nowMillis,
                dayStartMillis = startOfDayMillis,
                gracePeriodMillis = customize.gracePeriodMillis,
                targetPackages = targetPackages.toSet(),
                perPackageMillis = perPkg,
                allTargetsMillis = all,
            )
            // スナップショットの computedAt を起点にランタイム加算を再開する
            runtimeDelta = RuntimeDeltaState(
                lastTickMillis = nowMillis,
                activePackageName = activePkg,
                perPackageMillis = emptyMap(),
                allTargetsMillis = 0L,
            )
        }
    }

    private fun needsSnapshotRefresh(
        customize: Customize,
        targetPackages: Set<String>,
        nowMillis: Long,
    ): Boolean {
        val dayStartMillis = computeStartOfDayMillis(nowMillis)
        val existing = snapshot ?: return true

        if (existing.dayStartMillis != dayStartMillis) return true
        if (existing.gracePeriodMillis != customize.gracePeriodMillis) return true
        if (existing.targetPackages != targetPackages) return true

        // TTL
        return (nowMillis - existing.computedAtMillis) >= SNAPSHOT_TTL_MILLIS
    }

    private fun accumulateRuntimeDelta(
        targetPackages: Set<String>,
        newActivePackageName: String?,
        nowMillis: Long,
    ) {
        synchronized(lock) {
            val prevTick = runtimeDelta.lastTickMillis
            val dtRaw = if (prevTick == null) 0L else (nowMillis - prevTick).coerceAtLeast(0L)
            val dt = dtRaw.coerceAtMost(MAX_RUNTIME_STEP_MILLIS)

            val prevActive = runtimeDelta.activePackageName
            if (dt > 0L && prevActive != null && prevActive in targetPackages) {
                val updated = runtimeDelta.perPackageMillis.toMutableMap()
                updated[prevActive] = (updated[prevActive] ?: 0L) + dt
                runtimeDelta = runtimeDelta.copy(
                    lastTickMillis = nowMillis,
                    activePackageName = newActivePackageName,
                    perPackageMillis = updated.toMap(),
                    allTargetsMillis = runtimeDelta.allTargetsMillis + dt,
                )
            } else {
                runtimeDelta = runtimeDelta.copy(
                    lastTickMillis = nowMillis,
                    activePackageName = newActivePackageName,
                )
            }
        }
    }

    private fun computeStartOfDayMillis(nowMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return today.atStartOfDay(zone).toInstant().toEpochMilli()
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
