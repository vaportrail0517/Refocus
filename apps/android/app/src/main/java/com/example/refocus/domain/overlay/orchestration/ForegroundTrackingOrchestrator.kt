package com.example.refocus.domain.overlay.orchestration

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.monitor.port.ForegroundAppObserver
import com.example.refocus.domain.overlay.engine.OverlayEvent
import com.example.refocus.domain.overlay.engine.OverlayState
import com.example.refocus.domain.overlay.model.OverlayRuntimeState
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.domain.overlay.port.OverlayUiPort
import com.example.refocus.domain.overlay.usecase.DailyUsageUseCase
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 前面アプリ監視のオーケストレーション。
 *
 * - Targets と Foreground を combine して Enter/Leave の OverlayEvent を発火
 * - ForegroundAppEvent の記録
 * - 日次集計（今日の累計）スナップショットの更新
 * - 提案表示のトリガ
 *
 * 堅牢性のために，
 * - start/stop を状態遷移（StateFlow）として扱い，二重起動を防ぐ
 * - 監視ループが例外で落ちた場合は，自動的にリトライして復帰する
 */
class ForegroundTrackingOrchestrator(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val overlayHealthStore: OverlayHealthStore,
    private val targetsRepository: TargetsRepository,
    private val foregroundAppObserver: ForegroundAppObserver,
    private val runtimeState: MutableStateFlow<OverlayRuntimeState>,
    private val sessionTracker: OverlaySessionTracker,
    private val dailyUsageUseCase: DailyUsageUseCase,
    private val suggestionOrchestrator: SuggestionOrchestrator,
    private val uiController: OverlayUiPort,
    private val eventRecorder: EventRecorder,
    private val dispatchEvent: (OverlayEvent) -> Unit,
) {
    companion object {
        private const val TAG = "ForegroundTracking"

        private const val INITIAL_RETRY_BACKOFF_MS = 500L
        private const val MAX_RETRY_BACKOFF_MS = 10_000L

        private const val MONITOR_HEALTH_WRITE_INTERVAL_MS: Long = 10_000L
        private const val ERROR_SUMMARY_MAX = 160

        // 監視開始直後は，「すでに前面にいるアプリ」の直前イベントを拾い直すために
        // UsageEvents を少し長めに巻き戻す．
        // 例: 対象アプリを開いたまま overlay を ON にしたケースで無反応になりにくくする．
        private const val MONITOR_STARTUP_LOOKBACK_MS: Long = 120_000L
    }

    private sealed interface DesiredState {
        data object Stopped : DesiredState

        data class Running(
            val screenOnFlow: StateFlow<Boolean>,
        ) : DesiredState
    }

    private val desiredState = MutableStateFlow<DesiredState>(DesiredState.Stopped)

    @Volatile
    private var controlJob: Job? = null

    private val sideEffects =
        ForegroundTrackingSideEffects(
            scope = scope,
            eventRecorder = eventRecorder,
            overlayHealthStore = overlayHealthStore,
        )

    /**
     * 起動・停止の世代番号。
     * collectLatest のキャンセルが遅延した場合でも，古い世代の処理をフェイルセーフに無効化する。
     */
    @Volatile
    private var activeGeneration: Long = 0L

    private fun tickerFlow(periodMs: Long): Flow<Unit> =
        flow {
            while (currentCoroutineContext().isActive) {
                emit(Unit)
                delay(periodMs)
            }
        }.onStart { emit(Unit) }

    /**
     * 監視開始要求。
     *
     * - 二重起動はしない
     * - 途中で監視ループが落ちた場合は自動復帰する
     */
    fun start(screenOnFlow: StateFlow<Boolean>) {
        desiredState.value = DesiredState.Running(screenOnFlow = screenOnFlow)
        ensureControlLoop()
    }

    /**
     * 監視停止要求。
     *
     * scope 自体がキャンセルされる場合（Service 終了など）は，自然に停止する。
     */
    fun stop() {
        desiredState.value = DesiredState.Stopped
        ensureControlLoop()
    }

    @Synchronized
    private fun ensureControlLoop() {
        if (controlJob?.isActive == true) return

        controlJob =
            scope.launch {
                desiredState.collectLatest { state ->
                    // 状態が変わるたびに世代を更新し，古いループを無効化する
                    val generation = activeGeneration + 1
                    activeGeneration = generation

                    when (state) {
                        DesiredState.Stopped -> {
                            RefocusLog.d(TAG) { "foreground monitoring stopped (gen=$generation)" }
                            // collectLatest により，直前の監視ループは自動的にキャンセルされる
                            cleanupAfterStop()
                        }

                        is DesiredState.Running -> {
                            RefocusLog.d(TAG) { "foreground monitoring requested (gen=$generation)" }
                            superviseMonitoring(
                                screenOnFlow = state.screenOnFlow,
                                generation = generation,
                            )
                        }
                    }
                }
            }
    }

    private suspend fun cleanupAfterStop() {
        // UI とランタイム状態をフェイルセーフに片付ける
        try {
            withContext(Dispatchers.Main) {
                uiController.hideTimer()
                uiController.hideSuggestion()
                uiController.hideMiniGame()
            }
        } catch (e: Exception) {
            RefocusLog.w(TAG, e) { "Failed to hide overlay UI on stop" }
        }

        runtimeState.update {
            it.copy(
                overlayPackage = null,
                trackingPackage = null,
                timerVisible = false,
            )
        }

        suggestionOrchestrator.clearOverlayState()
        suggestionOrchestrator.resetGate()
    }

    private suspend fun recordMonitorRestart(summary: String?) {
        try {
            overlayHealthStore.update { current ->
                current.copy(
                    monitorRestartCount = current.monitorRestartCount + 1,
                    lastErrorSummary = summary,
                )
            }
        } catch (e: Exception) {
            RefocusLog.w(TAG, e) { "Failed to update monitor restart health" }
        }
    }

    private fun summarizeError(e: Throwable): String {
        val name = e::class.java.simpleName
        val msg = e.message
        val raw = if (msg.isNullOrBlank()) name else "$name: $msg"
        return if (raw.length <= ERROR_SUMMARY_MAX) raw else raw.take(ERROR_SUMMARY_MAX)
    }

    private suspend fun superviseMonitoring(
        screenOnFlow: StateFlow<Boolean>,
        generation: Long,
    ) {
        var backoffMs = INITIAL_RETRY_BACKOFF_MS

        while (currentCoroutineContext().isActive && activeGeneration == generation) {
            try {
                runMonitoringOnce(
                    screenOnFlow = screenOnFlow,
                    generation = generation,
                )

                // 監視ループが自然終了するのは基本的に想定外なので，ログを残してリトライする
                RefocusLog.w(TAG) { "foreground monitoring completed unexpectedly (gen=$generation). restarting." }
                recordMonitorRestart("monitor_completed")
            } catch (e: CancellationException) {
                // collectLatest による停止，Service 破棄など
                throw e
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "foreground monitoring crashed (gen=$generation). restarting." }
                recordMonitorRestart("monitor_crash: ${summarizeError(e)}")
            }

            // リトライを少し待ってから再起動（急速なクラッシュループを避ける）
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_RETRY_BACKOFF_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runMonitoringOnce(
        screenOnFlow: StateFlow<Boolean>,
        generation: Long,
    ) {
        // ここで例外を投げると supervise が拾って再起動する。
        // ただし，1 回の tick 内で起きた一時的な例外は，collect 内で握って監視継続する。
        val targetsFlow = targetsRepository.observeTargets()

        // pollingIntervalMillis は runtimeState.customize から取得して単一の真実に寄せる
        val pollingIntervalFlow =
            runtimeState
                .map { it.customize.pollingIntervalMillis }
                .distinctUntilChanged()

        val baseForegroundSampleFlow =
            pollingIntervalFlow
                .flatMapLatest { interval ->
                    foregroundAppObserver.foregroundSampleFlow(
                        pollingIntervalMs = interval,
                        initialLookbackMs = MONITOR_STARTUP_LOOKBACK_MS,
                    )
                }

        // 画面 OFF 中は UsageEvents のポーリングを止める（無駄な負荷を減らし，監視が殺されにくくする）．
        // combine が動き続けるために，OFF へ遷移したタイミングでダミーサンプルを 1 回だけ流す．
        val foregroundSampleFlow =
            screenOnFlow
                // StateFlow は元々 distinctUntilChanged 相当の挙動なので，ここでの distinctUntilChanged は不要（かつ deprecated）．
                .flatMapLatest { isScreenOn ->
                    if (isScreenOn) {
                        baseForegroundSampleFlow
                    } else {
                        flowOf(
                            ForegroundAppObserver.ForegroundSample(
                                packageName = null,
                                generation = 0L,
                            ),
                        )
                    }
                }
        // Customize も combine に含めて，ループ内で runtimeState.value.customize を参照しない
        val customizeSnapshotFlow =
            runtimeState
                .map { it.customize }
                .distinctUntilChanged()

        var lastForegroundRaw: String? = null
        var lastMonitorHealthWriteElapsed: Long = 0L
        var lastSample: ForegroundAppObserver.ForegroundSample? = null
        var lastScreenOn: Boolean? = null

        val tickFlow = tickerFlow(periodMs = 1_000L)

        combine(
            targetsFlow,
            foregroundSampleFlow,
            screenOnFlow,
            tickFlow,
            customizeSnapshotFlow,
        ) { targets, sample, isScreenOn, _, customize ->
            OverlayLoopInput(
                targets = targets,
                sample = sample,
                isScreenOn = isScreenOn,
                customize = customize,
            )
        }.collect { input ->
            // 念のため：世代が変わっていたら（停止・再起動要求）処理しない
            if (activeGeneration != generation) return@collect

            val targets = input.targets
            val sample = input.sample
            val isScreenOn = input.isScreenOn
            val customize = input.customize

            val foregroundRaw = sample.packageName
            val foregroundPackage = if (isScreenOn) foregroundRaw else null

            val shouldLog = foregroundRaw != lastForegroundRaw || isScreenOn != lastScreenOn
            if (shouldLog) {
                RefocusLog.d(TAG) {
                    "foreground: raw=$foregroundRaw, gen=${sample.generation}, screenOn=$isScreenOn, effective=$foregroundPackage, targets=$targets"
                }
                lastScreenOn = isScreenOn
            }

            if (foregroundRaw != lastForegroundRaw) {
                lastForegroundRaw = foregroundRaw
                try {
                    // hot loop を IO でブロックしないため，副作用は専用キューへ逃がす
                    sideEffects.recordForegroundChange(foregroundRaw)
                } catch (e: Exception) {
                    RefocusLog.e(
                        TAG,
                        e,
                    ) { "Failed to enqueue foreground app change: $foregroundRaw" }
                }
            }

            try {
                val nowMillis = timeSource.nowMillis()
                val nowElapsed = timeSource.elapsedRealtime()

                // 監視ループが生きていることを外部から判定できるように，定期的に liveness を記録する（過剰な DataStore 書き込みは避ける）
                if (nowElapsed - lastMonitorHealthWriteElapsed >= MONITOR_HEALTH_WRITE_INTERVAL_MS) {
                    lastMonitorHealthWriteElapsed = nowElapsed
                    try {
                        sideEffects.updateMonitorLiveness(nowElapsed)
                    } catch (e: Exception) {
                        RefocusLog.w(TAG, e) { "Failed to enqueue monitor liveness update" }
                    }
                }

                val prevTargets = runtimeState.value.lastTargetPackages
                if (prevTargets != targets) {
                    runtimeState.update { it.copy(lastTargetPackages = targets) }
                }

                // 日次表示モードの場合は，
                // - スナップショット更新（低頻度）
                // - ランタイム加算（高頻度）
                // の二段構えで追従し，毎秒 DB を叩かないようにする．
                dailyUsageUseCase.onTick(
                    customize = customize,
                    targetPackages = targets,
                    activePackageName = foregroundPackage,
                    nowMillis = nowMillis,
                )

                // 「同一パッケージだが前面復帰した」を検知して，前面安定だけリセット
                val prevSample = lastSample
                lastSample = sample
                val reconfirmed =
                    isScreenOn &&
                        foregroundRaw != null &&
                        prevSample?.packageName == foregroundRaw &&
                        prevSample.generation != sample.generation
                if (reconfirmed) {
                    val stateSnapshot = runtimeState.value.overlayState
                    if (stateSnapshot is OverlayState.Tracking &&
                        stateSnapshot.packageName == foregroundRaw &&
                        runtimeState.value.overlayPackage == foregroundRaw
                    ) {
                        sessionTracker.onForegroundReconfirmed(
                            packageName = foregroundRaw,
                            nowElapsedRealtime = nowElapsed,
                        )
                        RefocusLog.d(
                            TAG,
                        ) { "Foreground reconfirmed for $foregroundRaw -> reset stable timer only" }
                    }
                }

                val previous = runtimeState.value.currentForegroundPackage
                val prevIsTarget = previous != null && previous in targets
                val nowIsTarget = foregroundPackage != null && foregroundPackage in targets

                if (previous != foregroundPackage) {
                    runtimeState.update { it.copy(currentForegroundPackage = foregroundPackage) }
                }

                when {
                    // 非対象 → 対象
                    !prevIsTarget && nowIsTarget -> {
                        dispatchEvent(
                            OverlayEvent.EnterTargetApp(
                                packageName = foregroundPackage!!,
                                nowMillis = nowMillis,
                                nowElapsedRealtime = nowElapsed,
                            ),
                        )
                    }

                    // 対象 → 非対象
                    prevIsTarget && !nowIsTarget -> {
                        dispatchEvent(
                            OverlayEvent.LeaveTargetApp(
                                packageName = previous!!,
                                nowMillis = nowMillis,
                                nowElapsedRealtime = nowElapsed,
                            ),
                        )
                    }

                    // 対象A → 対象B
                    prevIsTarget && previous != foregroundPackage -> {
                        dispatchEvent(
                            OverlayEvent.LeaveTargetApp(
                                packageName = previous!!,
                                nowMillis = nowMillis,
                                nowElapsedRealtime = nowElapsed,
                            ),
                        )
                        dispatchEvent(
                            OverlayEvent.EnterTargetApp(
                                packageName = foregroundPackage!!,
                                nowMillis = nowMillis,
                                nowElapsedRealtime = nowElapsed,
                            ),
                        )
                    }

                    else -> Unit
                }

                // Suggestion は Tracking 中かつタイマー表示中だけ評価
                val stateSnapshot = runtimeState.value.overlayState
                val isTimerVisible = runtimeState.value.timerVisible
                if (stateSnapshot is OverlayState.Tracking && foregroundPackage != null && isTimerVisible) {
                    val elapsed =
                        sessionTracker.computeElapsedFor(foregroundPackage, nowElapsed)
                            ?: return@collect
                    val sinceFg =
                        sessionTracker.sinceForegroundMillis(foregroundPackage, nowElapsed)
                            ?: return@collect
                    suggestionOrchestrator.maybeShowSuggestionIfNeeded(
                        packageName = foregroundPackage,
                        nowMillis = nowMillis,
                        elapsedMillis = elapsed,
                        sinceForegroundMillis = sinceFg,
                    )
                }
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Error in foreground monitoring loop" }
                try {
                    sideEffects.recordMonitorError("monitor_loop_error: ${summarizeError(e)}")
                } catch (ignored: Exception) {
                    // ignore
                }
                withContext(Dispatchers.Main) {
                    uiController.hideTimer()
                    uiController.hideSuggestion()
                    uiController.hideMiniGame()
                }
                runtimeState.update {
                    it.copy(
                        overlayPackage = null,
                        trackingPackage = null,
                        timerVisible = false,
                    )
                }
                suggestionOrchestrator.clearOverlayState()
                suggestionOrchestrator.resetGate()
            }
        }
    }

    /**
     * 監視ホットループから IO を隔離するための副作用キュー．
     *
     * - 前面アプリ変更の記録（DB/IO）など，遅延しやすい処理を IO スレッドで直列実行する
     * - タイムアウトでハングを避け，キューの詰まりによる連鎖停止を防ぐ
     */
    private class ForegroundTrackingSideEffects(
        scope: CoroutineScope,
        private val eventRecorder: EventRecorder,
        private val overlayHealthStore: OverlayHealthStore,
    ) {
        companion object {
            private const val TAG = "ForegroundSideEffects"
            private const val RECORD_TIMEOUT_MS: Long = 1_500L
        }

        private sealed interface Command {
            data class RecordForegroundChange(
                val packageName: String?,
            ) : Command

            data class UpdateMonitorLiveness(
                val nowElapsedRealtimeMillis: Long,
            ) : Command

            data class RecordMonitorError(
                val summary: String,
            ) : Command
        }

        private val channel = Channel<Command>(capacity = Channel.BUFFERED)

        init {
            scope.launch(Dispatchers.IO) {
                for (cmd in channel) {
                    try {
                        when (cmd) {
                            is Command.RecordForegroundChange -> {
                                withTimeout(RECORD_TIMEOUT_MS) {
                                    eventRecorder.onForegroundAppChanged(cmd.packageName)
                                }
                            }

                            is Command.UpdateMonitorLiveness -> {
                                overlayHealthStore.update { current ->
                                    current.copy(
                                        lastForegroundSampleElapsedRealtimeMillis = cmd.nowElapsedRealtimeMillis,
                                    )
                                }
                            }

                            is Command.RecordMonitorError -> {
                                overlayHealthStore.update { current ->
                                    current.copy(
                                        lastErrorSummary = cmd.summary,
                                    )
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        RefocusLog.w(TAG, e) { "side effect timed out: $cmd" }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        RefocusLog.w(TAG, e) { "side effect failed: $cmd" }
                    }
                }
            }
        }

        suspend fun recordForegroundChange(packageName: String?) {
            // Channel.BUFFERED を使うことで通常は即 enqueue できる．
            // もしバックプレッシャがかかるほど詰まった場合は，ここで suspend して順序を守る．
            channel.send(Command.RecordForegroundChange(packageName))
        }

        suspend fun updateMonitorLiveness(nowElapsedRealtimeMillis: Long) {
            channel.send(Command.UpdateMonitorLiveness(nowElapsedRealtimeMillis))
        }

        suspend fun recordMonitorError(summary: String) {
            channel.send(Command.RecordMonitorError(summary))
        }
    }

    private data class OverlayLoopInput(
        val targets: Set<String>,
        val sample: ForegroundAppObserver.ForegroundSample,
        val isScreenOn: Boolean,
        val customize: Customize,
    )
}
