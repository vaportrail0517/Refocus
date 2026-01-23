package com.example.refocus.domain.overlay.usecase

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.timeline.TimelineInterpretationConfig
import com.example.refocus.domain.timeline.TimelineProjectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CancellationException

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
    private val timeSource: TimeSource,
    private val timelineProjectionService: TimelineProjectionService,
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
            fun empty(): RuntimeDeltaState =
                RuntimeDeltaState(
                    lastTickMillis = null,
                    activePackageName = null,
                    perPackageMillis = emptyMap(),
                    allTargetsMillis = 0L,
                )
        }
    }

    private data class RefreshBaseline(
        val generation: Long,
        val runtimeDeltaAtStart: RuntimeDeltaState,
    )

    private val lock = Any()

    @Volatile
    private var snapshot: DailyUsageSnapshot? = null

    @Volatile
    private var refreshJob: Job? = null

    // invalidate と refresh の競合を安全に捌くための世代カウンタ
    @Volatile
    private var generation: Long = 0L

    // スナップショット以降の「ランタイム加算分」
    private var runtimeDelta: RuntimeDeltaState = RuntimeDeltaState.empty()

    fun invalidate() {
        val jobToCancel: Job?
        synchronized(lock) {
            generation += 1L
            snapshot = null
            runtimeDelta = RuntimeDeltaState.empty()
            jobToCancel = refreshJob
            refreshJob = null
        }
        jobToCancel?.cancel()
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
        // 日付またぎを検知したら即リセット（跨ぎ dt の混入を防ぐ）
        // - snapshot がまだ無い（refresh が未完了）状況でも，lastTick の日付で検知できるようにする
        val dayStartMillisNow = computeStartOfDayMillis(nowMillis)
        val needsDayReset =
            synchronized(lock) {
                val snapshotMismatch =
                    snapshot?.dayStartMillis?.let { it != dayStartMillisNow } ?: false
                val prevTick = runtimeDelta.lastTickMillis
                val runtimeMismatch =
                    prevTick?.let { computeStartOfDayMillis(it) != dayStartMillisNow } ?: false
                snapshotMismatch || runtimeMismatch
            }
        if (needsDayReset) {
            invalidate()
        }

        if (customize.timerTimeMode == TimerTimeMode.SessionElapsed) {
            // 日次モードを使っていないなら加算も更新も不要だが，
            // 復帰時に過大 dt を作らないため lastTick だけ進める．
            synchronized(lock) {
                runtimeDelta =
                    runtimeDelta.copy(
                        lastTickMillis = nowMillis,
                        activePackageName = null,
                    )
            }
            return
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
     * 同期的に値が欲しい箇所（通知更新など）用．
     * 必要ならバックグラウンドで更新を要求するだけで，ここでは suspend しない．
     */
    fun requestRefreshIfNeeded(
        customize: Customize,
        targetPackages: Set<String>,
        nowMillis: Long = timeSource.nowMillis(),
    ) {
        if (customize.timerTimeMode == TimerTimeMode.SessionElapsed) return
        val refreshReason = snapshotRefreshReasonOrNull(customize, targetPackages, nowMillis) ?: return

        RefocusLog.d(TAG) {
            val msg =
                "requestRefreshIfNeeded: " +
                    "reason=$refreshReason " +
                    "mode=${customize.timerTimeMode} " +
                    "now=$nowMillis " +
                    "targetCount=${targetPackages.size}"
            msg
        }

        // requestRefreshIfNeeded は複数箇所（overlay tick / 設定変更など）から並行に呼ばれ得る．
        // - refreshJob のチェックと代入は lock で原子的に行い，多重起動を防ぐ
        // - finally で refreshJob を無条件に null にすると，新しいジョブを古いジョブが消してしまう可能性がある
        //   （invalidate→再起動，などのタイミングで起こり得る）ため，自己同一性を見てクリアする

        val jobCandidate =
            scope.launch(start = CoroutineStart.LAZY) {
                val self = coroutineContext.job
                try {
                    refreshIfNeeded(
                        customize = customize,
                        targetPackages = targetPackages,
                        nowMillis = nowMillis,
                    )
                } catch (e: Exception) {
                    // invalidate() などで発生するキャンセルは正常系なので，ログを出さずに伝播させる
                    if (e is CancellationException) throw e
                    RefocusLog.w(TAG, e) { "refreshIfNeeded failed" }
                } finally {
                    synchronized(lock) {
                        if (refreshJob === self) {
                            refreshJob = null
                        }
                    }
                }
            }

        val shouldStart: Boolean =
            synchronized(lock) {
                val existing = refreshJob
                if (existing != null && existing.isActive) {
                    false
                } else {
                    refreshJob = jobCandidate
                    true
                }
            }

        if (!shouldStart) {
            jobCandidate.cancel()
            return
        }

        jobCandidate.start()
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
        val refreshReason = snapshotRefreshReasonOrNull(customize, targetPackages, nowMillis) ?: return

        val baseline: RefreshBaseline =
            synchronized(lock) {
                RefreshBaseline(
                    generation = generation,
                    runtimeDeltaAtStart = runtimeDelta,
                )
            }

        val startOfDayMillis = computeStartOfDayMillis(nowMillis)

        RefocusLog.d(TAG) {
            val msg =
                "refreshIfNeeded: start " +
                    "reason=$refreshReason " +
                    "dayStart=$startOfDayMillis " +
                    "now=$nowMillis " +
                    "grace=${customize.gracePeriodMillis} " +
                    "targetCount=${targetPackages.size} " +
                    "lookbackHours=$lookbackHours"
            msg
        }

        val computedSnapshot: DailyUsageSnapshot =
            if (targetPackages.isEmpty()) {
                DailyUsageSnapshot(
                    computedAtMillis = nowMillis,
                    dayStartMillis = startOfDayMillis,
                    gracePeriodMillis = customize.gracePeriodMillis,
                    targetPackages = emptySet(),
                    perPackageMillis = emptyMap(),
                    allTargetsMillis = 0L,
                )
            } else {
                // startOfDay 以前の状態復元に必要な seed + 今日のイベントだけを読み，
                // 同じ投影ロジック（TimelineProjector）で「今日の累計」を再構成する．
                // 重要: seed に含まれる TargetAppsChangedEvent は，48h 等の lookback で落とすと
                // 「対象集合が空」と解釈され，日次累計が 0 に倒れ得る．
                // seed は種類ごとに最大 1 件程度なので，ここでは lookback をかけない．
                val events =
                    withContext(Dispatchers.IO) {
                        timelineProjectionService.loadWithSeed(
                            windowStartMillis = startOfDayMillis,
                            windowEndMillis = nowMillis,
                        )
                    }

                RefocusLog.d(TAG) {
                    val seedCount = events.count { it.timestampMillis < startOfDayMillis }
                    val windowCount = events.size - seedCount
                    val hasTargetSeed =
                        events.any { e ->
                            e is TargetAppsChangedEvent &&
                                e.timestampMillis < startOfDayMillis
                        }
                    val msg =
                        "refreshIfNeeded: loaded events " +
                            "total=${events.size} " +
                            "seed=$seedCount " +
                            "window=$windowCount " +
                            "hasTargetAppsSeed=$hasTargetSeed " +
                            "kinds=${summarizeEventKinds(events)}"
                    msg
                }

                val zone = ZoneId.systemDefault()
                val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

                val (perPkg, all) =
                    withContext(Dispatchers.Default) {
                        val projection =
                            timelineProjectionService.project(
                                events = events,
                                config =
                                    TimelineInterpretationConfig(
                                        stopGracePeriodMillis = customize.gracePeriodMillis,
                                    ),
                                nowMillis = nowMillis,
                                zoneId = zone,
                            )

                        val partsToday = projection.sessionParts.filter { it.date == today }
                        val per =
                            partsToday
                                .groupBy { it.packageName }
                                .mapValues { (_, parts) -> parts.sumOf { it.durationMillis }.coerceAtLeast(0L) }

                        val total = partsToday.sumOf { it.durationMillis }.coerceAtLeast(0L)

                        per.toMap() to total
                    }

                RefocusLog.d(TAG) {
                    val msg =
                        "refreshIfNeeded: projected " +
                            "perPkg=${perPkg.size} " +
                            "allTargetsMillis=$all " +
                            "todayPartsTotal=${perPkg.values.sum()}"
                    msg
                }

                DailyUsageSnapshot(
                    computedAtMillis = nowMillis,
                    dayStartMillis = startOfDayMillis,
                    gracePeriodMillis = customize.gracePeriodMillis,
                    targetPackages = targetPackages.toSet(),
                    perPackageMillis = perPkg,
                    allTargetsMillis = all,
                )
            }

        synchronized(lock) {
            // invalidate / 日付またぎなどで世代が進んでいたら，古い refresh 結果は捨てる
            if (baseline.generation != generation) return

            val currentRuntime = runtimeDelta

            // refresh 実行中に積まれた「後続のランタイム加算分」だけを残す
            // （refresh 対象期間＝nowMillis までの分は snapshot 側に含まれる）
            val deltaPerPkg =
                subtractNonNegative(
                    current = currentRuntime.perPackageMillis,
                    baseline = baseline.runtimeDeltaAtStart.perPackageMillis,
                )
            val deltaAll =
                (currentRuntime.allTargetsMillis - baseline.runtimeDeltaAtStart.allTargetsMillis)
                    .coerceAtLeast(0L)

            snapshot = computedSnapshot
            runtimeDelta =
                currentRuntime.copy(
                    perPackageMillis = deltaPerPkg,
                    allTargetsMillis = deltaAll,
                )

            RefocusLog.d(TAG) {
                val msg =
                    "refreshIfNeeded: commit " +
                        "snapshotAll=${computedSnapshot.allTargetsMillis} " +
                        "snapshotPerPkg=${computedSnapshot.perPackageMillis.size} " +
                        "keptDeltaAll=$deltaAll " +
                        "keptDeltaPerPkg=${deltaPerPkg.size} " +
                        "generation=$generation"
                msg
            }
        }
    }

    private fun needsSnapshotRefresh(
        customize: Customize,
        targetPackages: Set<String>,
        nowMillis: Long,
    ): Boolean = snapshotRefreshReasonOrNull(customize, targetPackages, nowMillis) != null

    private fun snapshotRefreshReasonOrNull(
        customize: Customize,
        targetPackages: Set<String>,
        nowMillis: Long,
    ): String? {
        val dayStartMillis = computeStartOfDayMillis(nowMillis)
        val existing = snapshot ?: return "missing_snapshot"

        if (existing.dayStartMillis != dayStartMillis) {
            return "day_changed(existing=${existing.dayStartMillis}, now=$dayStartMillis)"
        }
        if (existing.gracePeriodMillis != customize.gracePeriodMillis) {
            return "grace_changed(existing=${existing.gracePeriodMillis}, now=${customize.gracePeriodMillis})"
        }
        if (existing.targetPackages != targetPackages) {
            return "targets_changed(existingCount=${existing.targetPackages.size}, nowCount=${targetPackages.size})"
        }

        // TTL
        val age = (nowMillis - existing.computedAtMillis)
        return if (age >= SNAPSHOT_TTL_MILLIS) {
            "ttl_expired(age=${age}ms, ttl=${SNAPSHOT_TTL_MILLIS}ms)"
        } else {
            null
        }
    }

    private fun summarizeEventKinds(events: List<com.example.refocus.core.model.TimelineEvent>): String {
        if (events.isEmpty()) return "{}"
        var service = 0
        var screen = 0
        var fg = 0
        var targets = 0
        var other = 0
        for (e in events) {
            when (e) {
                is ServiceLifecycleEvent -> service += 1
                is ScreenEvent -> screen += 1
                is ForegroundAppEvent -> fg += 1
                is TargetAppsChangedEvent -> targets += 1
                else -> other += 1
            }
        }
        return "{service=$service, screen=$screen, foreground=$fg, targetsChanged=$targets, other=$other}"
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
                runtimeDelta =
                    runtimeDelta.copy(
                        lastTickMillis = nowMillis,
                        activePackageName = newActivePackageName,
                        perPackageMillis = updated.toMap(),
                        allTargetsMillis = runtimeDelta.allTargetsMillis + dt,
                    )
            } else {
                runtimeDelta =
                    runtimeDelta.copy(
                        lastTickMillis = nowMillis,
                        activePackageName = newActivePackageName,
                    )
            }
        }
    }

    private fun subtractNonNegative(
        current: Map<String, Long>,
        baseline: Map<String, Long>,
    ): Map<String, Long> {
        if (current.isEmpty()) return emptyMap()
        if (baseline.isEmpty()) return current

        val out = mutableMapOf<String, Long>()
        for ((k, v) in current) {
            val b = baseline[k] ?: 0L
            val diff = (v - b).coerceAtLeast(0L)
            if (diff > 0L) out[k] = diff
        }
        return out.toMap()
    }

    private fun computeStartOfDayMillis(nowMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return today.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
