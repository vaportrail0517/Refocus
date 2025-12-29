package com.example.refocus.domain.overlay.orchestration

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.engine.OverlayEvent
import com.example.refocus.domain.overlay.engine.OverlayState
import com.example.refocus.domain.overlay.model.OverlayRuntimeState
import com.example.refocus.domain.overlay.port.OverlayUiPort
import com.example.refocus.domain.overlay.usecase.DailyUsageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Overlay の設定購読を OverlayCoordinator から分離し，責務を明確化するためのクラス．
 *
 * - customize のスナップショット更新
 * - タイムラインへの SettingsChanged イベント記録
 * - UI への applySettings
 * - 停止猶予時間（grace）の変更時に，現在の論理セッションを再解釈して追従
 */
internal class OverlaySettingsObserver(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val customizeFlow: SharedFlow<Customize>,
    private val runtimeState: MutableStateFlow<OverlayRuntimeState>,
    private val dailyUsageUseCase: DailyUsageUseCase,
    private val uiController: OverlayUiPort,
    private val sessionBootstrapper: SessionBootstrapper,
    private val sessionTracker: OverlaySessionTracker,
    private val suggestionOrchestrator: SuggestionOrchestrator,
    private val dispatchEvent: (OverlayEvent) -> Unit,
) {
    companion object {
        private const val TAG = "OverlaySettingsObserver"
    }

    @Volatile
    private var job: Job? = null

    fun start(): Job {
        if (job?.isActive == true) return job!!

        job =
            scope.launch {
                try {
                    customizeFlow.collect { settings ->
                        val oldSettings = runtimeState.value.customize

                        // 先に customize スナップショットを更新（Provider などが最新設定を読めるようにする）
                        runtimeState.update { it.copy(customize = settings) }

                        // タイマー表示モードが変わった場合は，日次集計キャッシュを無効化する
                        if (settings.timerTimeMode != oldSettings.timerTimeMode) {
                            dailyUsageUseCase.invalidate()
                        }

                        // 設定変更イベントとしてタイムラインに記録
                        dispatchEvent(OverlayEvent.SettingsChanged(settings))

                        // UI 側の見た目反映
                        uiController.applySettings(settings)

                        // 停止猶予時間が変わったかどうかを判定
                        val graceChanged = settings.gracePeriodMillis != oldSettings.gracePeriodMillis
                        if (graceChanged) {
                            RefocusLog.d(TAG) {
                                "gracePeriodMillis changed: ${oldSettings.gracePeriodMillis} -> ${settings.gracePeriodMillis}"
                            }

                            // 停止猶予時間が変わると，日次累計の再投影結果も変わり得るためキャッシュを無効化する
                            dailyUsageUseCase.invalidate()

                            // 「今表示中のターゲット」について，変更後の停止猶予で論理セッションを再解釈して追従する
                            val pkg = runtimeState.value.overlayPackage
                            val stateSnapshot = runtimeState.value
                            if (pkg != null &&
                                stateSnapshot.overlayState is OverlayState.Tracking &&
                                stateSnapshot.isScreenOn
                            ) {
                                val nowMillis = timeSource.nowMillis()

                                // 変更前 tracker が残っていても投影したいので force=true で復元
                                val bootstrap =
                                    sessionBootstrapper.computeBootstrapFromTimeline(
                                        packageName = pkg,
                                        customize = settings,
                                        nowMillis = nowMillis,
                                        force = true,
                                        sessionTracker = sessionTracker,
                                    )

                                // tracker を入れ替える（timer は provider 経由なので，ここで再注入すれば表示は追従する）
                                sessionTracker.clear()
                                suggestionOrchestrator.resetGate()

                                val initialElapsed = bootstrap?.initialElapsedMillis ?: 0L
                                sessionTracker.onEnterTargetApp(
                                    packageName = pkg,
                                    gracePeriodMillis = settings.gracePeriodMillis,
                                    initialElapsedIfNew = initialElapsed,
                                )
                                suggestionOrchestrator.restoreGateIfOngoing(bootstrap)
                            } else {
                                // 表示中でなければ単純にクリアだけ
                                sessionTracker.clear()
                                suggestionOrchestrator.resetGate()
                            }
                        }
                    }
                } catch (e: Exception) {
                    RefocusLog.e(TAG, e) { "observeOverlaySettings failed" }
                }
            }

        return job!!
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
