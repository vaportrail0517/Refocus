package com.example.refocus.system.overlay.service

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.monitor.port.ForegroundAppObserver
import com.example.refocus.domain.overlay.runtime.OverlayCoordinator
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.system.notification.OverlayServiceNotificationController
import com.example.refocus.system.overlay.SuggestionOverlayController
import com.example.refocus.system.overlay.TimerOverlayController
import com.example.refocus.system.overlay.WindowOverlayUiController
import kotlinx.coroutines.CoroutineScope

/**
 * [com.example.refocus.system.overlay.OverlayService] が必要とする overlay 関連コンポーネントを生成する Factory．
 *
 * Service の責務（ライフサイクル管理）から，生成処理（依存配線）を切り離すことで，
 * [com.example.refocus.system.overlay.OverlayService] 本体を読みやすく保つ．
 */
internal class OverlayServiceComponentsFactory {
    fun create(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        scope: CoroutineScope,
        timeSource: TimeSource,
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

        val overlayUiController =
            WindowOverlayUiController(
                scope = scope,
                timerOverlayController = timerOverlayController,
                suggestionOverlayController = suggestionOverlayController,
            )

        val overlayCoordinator =
            OverlayCoordinator(
                scope = scope,
                timeSource = timeSource,
                targetsRepository = targetsRepository,
                settingsRepository = settingsRepository,
                settingsCommand = settingsCommand,
                suggestionsRepository = suggestionsRepository,
                foregroundAppObserver = foregroundAppObserver,
                suggestionEngine = suggestionEngine,
                suggestionSelector = suggestionSelector,
                uiController = overlayUiController,
                eventRecorder = eventRecorder,
                timelineRepository = timelineRepository,
            )

        val notificationController = OverlayServiceNotificationController(context)

        return OverlayServiceComponents(
            timerOverlayController = timerOverlayController,
            suggestionOverlayController = suggestionOverlayController,
            overlayUiController = overlayUiController,
            overlayCoordinator = overlayCoordinator,
            notificationController = notificationController,
        )
    }
}

internal data class OverlayServiceComponents(
    val timerOverlayController: TimerOverlayController,
    val suggestionOverlayController: SuggestionOverlayController,
    val overlayUiController: WindowOverlayUiController,
    val overlayCoordinator: OverlayCoordinator,
    val notificationController: OverlayServiceNotificationController,
)
