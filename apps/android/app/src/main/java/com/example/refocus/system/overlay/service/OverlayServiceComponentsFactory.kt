package com.example.refocus.system.overlay.service

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.monitor.port.ForegroundAppObserver
import com.example.refocus.domain.overlay.model.OverlayRuntimeState
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.domain.overlay.orchestration.ForegroundTrackingOrchestrator
import com.example.refocus.domain.overlay.orchestration.OverlaySessionLifecycle
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker
import com.example.refocus.domain.overlay.orchestration.OverlaySettingsObserver
import com.example.refocus.domain.overlay.orchestration.SessionBootstrapper
import com.example.refocus.domain.overlay.orchestration.SuggestionOrchestrator
import com.example.refocus.domain.overlay.policy.OverlayTimerDisplayCalculator
import com.example.refocus.domain.overlay.runtime.OverlayCoordinator
import com.example.refocus.domain.overlay.runtime.OverlayEventDispatcher
import com.example.refocus.domain.overlay.runtime.OverlaySideEffectHandler
import com.example.refocus.domain.overlay.usecase.DailyUsageUseCase
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.domain.timeline.TimelineProjectionService
import com.example.refocus.system.notification.OverlayServiceNotificationController
import com.example.refocus.system.overlay.MiniGameOverlayController
import com.example.refocus.system.overlay.SuggestionOverlayController
import com.example.refocus.system.overlay.TimerOverlayController
import com.example.refocus.system.overlay.WindowOverlayUiController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * [com.example.refocus.system.overlay.OverlayService] が必要とする overlay 関連コンポーネントを生成する Factory．
 *
 * Service の責務（ライフサイクル管理）から，生成処理（依存配線）を切り離すことで，
 * [com.example.refocus.system.overlay.OverlayService] 本体を読みやすく保つ．
 */
internal class OverlayServiceComponentsFactory {
    companion object {
        // 「停止猶予を含む論理セッション」を復元するためにどれだけ遡るか（必要なら後で調整）
        private const val BOOTSTRAP_LOOKBACK_HOURS = 48L
    }

    fun create(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        scope: CoroutineScope,
        timeSource: TimeSource,
        overlayHealthStore: OverlayHealthStore,
        targetsRepository: TargetsRepository,
        settingsRepository: SettingsRepository,
        settingsCommand: SettingsCommand,
        suggestionsRepository: SuggestionsRepository,
        foregroundAppObserver: ForegroundAppObserver,
        suggestionEngine: SuggestionEngine,
        suggestionSelector: SuggestionSelector,
        eventRecorder: EventRecorder,
        timelineRepository: TimelineRepository,
    ): OverlayServiceComponents {
        val timerOverlayController =
            TimerOverlayController(
                context = context,
                lifecycleOwner = lifecycleOwner,
                timeSource = timeSource,
                scope = scope,
            )

        val suggestionOverlayController =
            SuggestionOverlayController(
                context = context,
                lifecycleOwner = lifecycleOwner,
            )

        val miniGameOverlayController =
            MiniGameOverlayController(
                context = context,
                lifecycleOwner = lifecycleOwner,
            )

        val overlayUiController =
            WindowOverlayUiController(
                scope = scope,
                timerOverlayController = timerOverlayController,
                suggestionOverlayController = suggestionOverlayController,
                miniGameOverlayController = miniGameOverlayController,
            )

        // ===== domain runtime wiring =====
        val runtimeState = MutableStateFlow(OverlayRuntimeState())

        val sessionTracker = OverlaySessionTracker(timeSource)

        val timelineProjectionService = TimelineProjectionService(timelineRepository)

        val sessionBootstrapper =
            SessionBootstrapper(
                timeSource = timeSource,
                timelineProjectionService = timelineProjectionService,
                lookbackHours = BOOTSTRAP_LOOKBACK_HOURS,
            )

        val dailyUsageUseCase =
            DailyUsageUseCase(
                scope = scope,
                timeSource = timeSource,
                timelineProjectionService = timelineProjectionService,
                lookbackHours = BOOTSTRAP_LOOKBACK_HOURS,
            )

        val timerDisplayCalculator =
            OverlayTimerDisplayCalculator(
                timeSource = timeSource,
                sessionTracker = sessionTracker,
                dailyUsageUseCase = dailyUsageUseCase,
                customizeProvider = { runtimeState.value.customize },
                lastTargetPackagesProvider = { runtimeState.value.lastTargetPackages },
            )

        val suggestionOrchestrator =
            SuggestionOrchestrator(
                scope = scope,
                timeSource = timeSource,
                sessionElapsedProvider = { pkg, nowElapsed ->
                    sessionTracker.computeElapsedFor(pkg, nowElapsed)
                },
                onUiPause = { pkg, nowElapsed ->
                    sessionTracker.onUiPause(pkg, nowElapsed)
                },
                onUiResume = { pkg, nowElapsed ->
                    sessionTracker.onUiResume(pkg, nowElapsed)
                },
                suggestionEngine = suggestionEngine,
                suggestionSelector = suggestionSelector,
                suggestionsRepository = suggestionsRepository,
                uiController = overlayUiController,
                eventRecorder = eventRecorder,
                overlayPackageProvider = { runtimeState.value.overlayPackage },
                customizeProvider = { runtimeState.value.customize },
            )

        val sessionLifecycle =
            OverlaySessionLifecycle(
                scope = scope,
                timeSource = timeSource,
                runtimeState = runtimeState,
                settingsCommand = settingsCommand,
                uiController = overlayUiController,
                sessionBootstrapper = sessionBootstrapper,
                dailyUsageUseCase = dailyUsageUseCase,
                sessionTracker = sessionTracker,
                timerDisplayCalculator = timerDisplayCalculator,
                suggestionOrchestrator = suggestionOrchestrator,
            )

        val customizeFlow =
            settingsRepository
                .observeOverlaySettings()
                .shareIn(
                    scope = scope,
                    started = SharingStarted.Eagerly,
                    replay = 1,
                )

        val sideEffectHandler =
            OverlaySideEffectHandler(
                scope = scope,
                runtimeState = runtimeState,
                uiController = overlayUiController,
                suggestionOrchestrator = suggestionOrchestrator,
                sessionLifecycle = sessionLifecycle,
                sessionTracker = sessionTracker,
            )

        val eventDispatcher =
            OverlayEventDispatcher(
                runtimeState = runtimeState,
                onStateChanged = sideEffectHandler::handle,
            )

        val settingsObserver =
            OverlaySettingsObserver(
                scope = scope,
                timeSource = timeSource,
                customizeFlow = customizeFlow,
                runtimeState = runtimeState,
                dailyUsageUseCase = dailyUsageUseCase,
                uiController = overlayUiController,
                sessionBootstrapper = sessionBootstrapper,
                sessionTracker = sessionTracker,
                suggestionOrchestrator = suggestionOrchestrator,
                dispatchEvent = eventDispatcher::dispatch,
            )

        val foregroundTrackingOrchestrator =
            ForegroundTrackingOrchestrator(
                scope = scope,
                timeSource = timeSource,
                overlayHealthStore = overlayHealthStore,
                targetsRepository = targetsRepository,
                foregroundAppObserver = foregroundAppObserver,
                runtimeState = runtimeState,
                sessionTracker = sessionTracker,
                dailyUsageUseCase = dailyUsageUseCase,
                suggestionOrchestrator = suggestionOrchestrator,
                uiController = overlayUiController,
                eventRecorder = eventRecorder,
                dispatchEvent = eventDispatcher::dispatch,
            )

        val overlayCoordinator =
            OverlayCoordinator(
                scope = scope,
                timeSource = timeSource,
                overlayHealthStore = overlayHealthStore,
                uiController = overlayUiController,
                runtimeState = runtimeState,
                sessionTracker = sessionTracker,
                timerDisplayCalculator = timerDisplayCalculator,
                suggestionOrchestrator = suggestionOrchestrator,
                sessionLifecycle = sessionLifecycle,
                settingsObserver = settingsObserver,
                foregroundTrackingOrchestrator = foregroundTrackingOrchestrator,
                eventDispatcher = eventDispatcher,
            )

        val notificationController = OverlayServiceNotificationController(context)

        return OverlayServiceComponents(
            timerOverlayController = timerOverlayController,
            suggestionOverlayController = suggestionOverlayController,
            miniGameOverlayController = miniGameOverlayController,
            overlayUiController = overlayUiController,
            overlayCoordinator = overlayCoordinator,
            notificationController = notificationController,
        )
    }
}

internal data class OverlayServiceComponents(
    val timerOverlayController: TimerOverlayController,
    val suggestionOverlayController: SuggestionOverlayController,
    val miniGameOverlayController: MiniGameOverlayController,
    val overlayUiController: WindowOverlayUiController,
    val overlayCoordinator: OverlayCoordinator,
    val notificationController: OverlayServiceNotificationController,
)
