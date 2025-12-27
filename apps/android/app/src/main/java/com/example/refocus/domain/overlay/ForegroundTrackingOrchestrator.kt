package com.example.refocus.domain.overlay

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.gateway.ForegroundAppObserver
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 前面アプリ監視のオーケストレーション。
 *
 * - Targets と Foreground を combine して Enter/Leave の OverlayEvent を発火
 * - ForegroundAppEvent の記録
 * - 日次集計（今日の累計）スナップショットの更新
 * - 提案表示のトリガ
 */
class ForegroundTrackingOrchestrator(
    private val scope: CoroutineScope,
    private val timeSource: com.example.refocus.core.util.TimeSource,
    private val targetsRepository: TargetsRepository,
    private val foregroundAppObserver: ForegroundAppObserver,
    private val runtimeState: MutableStateFlow<OverlayRuntimeState>,
    private val sessionTracker: OverlaySessionTracker,
    private val dailyUsageUseCase: DailyUsageUseCase,
    private val suggestionOrchestrator: SuggestionOrchestrator,
    private val uiController: OverlayUiGateway,
    private val eventRecorder: EventRecorder,
    private val dispatchEvent: (OverlayEvent) -> Unit,
) {
    companion object {
        private const val TAG = "ForegroundTracking"
    }


    private var job: Job? = null

    private fun tickerFlow(periodMs: Long): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(periodMs)
        }
    }.onStart { emit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(
        screenOnFlow: StateFlow<Boolean>,
    ) {
        if (job?.isActive == true) return
        job = scope.launch {
            val targetsFlow = targetsRepository.observeTargets()

            // pollingIntervalMillis は runtimeState.customize から取得して単一の真実に寄せる
            val pollingIntervalFlow = runtimeState
                .map { it.customize.pollingIntervalMillis }
                .distinctUntilChanged()

            val foregroundSampleFlow = pollingIntervalFlow
                .flatMapLatest { interval ->
                    foregroundAppObserver.foregroundSampleFlow(
                        pollingIntervalMs = interval,
                    )
                }

            // Customize も combine に含めて，ループ内で runtimeState.value.customize を参照しない
            val customizeSnapshotFlow = runtimeState
                .map { it.customize }
                .distinctUntilChanged()

            var lastForegroundRaw: String? = null
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
                        // 別 launch に逃がすと順序が崩れ得るため直列にする
                        withContext(Dispatchers.IO) {
                            eventRecorder.onForegroundAppChanged(foregroundRaw)
                        }
                    } catch (e: Exception) {
                        RefocusLog.e(TAG, e) { "Failed to record foreground app change: $foregroundRaw" }
                    }
                }

                try {
                    val nowMillis = timeSource.nowMillis()
                    val nowElapsed = timeSource.elapsedRealtime()

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
                            RefocusLog.d(TAG) { "Foreground reconfirmed for $foregroundRaw -> reset stable timer only" }
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
                                )
                            )
                        }

                        // 対象 → 非対象
                        prevIsTarget && !nowIsTarget -> {
                            dispatchEvent(
                                OverlayEvent.LeaveTargetApp(
                                    packageName = previous!!,
                                    nowMillis = nowMillis,
                                    nowElapsedRealtime = nowElapsed,
                                )
                            )
                        }

                        // 対象A → 対象B
                        prevIsTarget && previous != foregroundPackage -> {
                            dispatchEvent(
                                OverlayEvent.LeaveTargetApp(
                                    packageName = previous!!,
                                    nowMillis = nowMillis,
                                    nowElapsedRealtime = nowElapsed,
                                )
                            )
                            dispatchEvent(
                                OverlayEvent.EnterTargetApp(
                                    packageName = foregroundPackage!!,
                                    nowMillis = nowMillis,
                                    nowElapsedRealtime = nowElapsed,
                                )
                            )
                        }

                        else -> Unit
                    }

                    // Suggestion は Tracking 中だけ評価
                    val stateSnapshot = runtimeState.value.overlayState
                    if (stateSnapshot is OverlayState.Tracking && foregroundPackage != null) {
                        val elapsed = sessionTracker.computeElapsedFor(foregroundPackage, nowElapsed)
                            ?: return@collect
                        val sinceFg = sessionTracker.sinceForegroundMillis(foregroundPackage, nowElapsed)
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
                    withContext(Dispatchers.Main) {
                        uiController.hideTimer()
                        uiController.hideSuggestion()
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
    }

    private data class OverlayLoopInput(
        val targets: Set<String>,
        val sample: ForegroundAppObserver.ForegroundSample,
        val isScreenOn: Boolean,
        val customize: com.example.refocus.core.model.Customize,
    )


    fun stop() {
        job?.cancel()
        job = null
    }

}
