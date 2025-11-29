package com.example.refocus.system.overlay.window

import com.example.refocus.core.model.Settings
import com.example.refocus.domain.overlay.OverlayUiController
import com.example.refocus.domain.overlay.SuggestionOverlayUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * domain/overlay.OverlayUiController の実装。
 * WindowManager × Compose な UI を実際に操作する。
 */
class WindowOverlayUiController(
    private val scope: CoroutineScope,
    private val timerOverlayController: TimerOverlayController,
    private val suggestionOverlayController: SuggestionOverlayController,
) : OverlayUiController {

    override fun applySettings(settings: Settings) {
        scope.launch(Dispatchers.Main) {
            timerOverlayController.overlaySettings = settings
        }
    }

    override fun showTimer(
        elapsedMillisProvider: (Long) -> Long,
        onPositionChanged: (x: Int, y: Int) -> Unit,
    ) {
        scope.launch(Dispatchers.Main) {
            timerOverlayController.showTimer(
                elapsedMillisProvider = elapsedMillisProvider,
                onPositionChanged = onPositionChanged,
            )
        }
    }

    override fun hideTimer() {
        scope.launch(Dispatchers.Main) {
            timerOverlayController.hideTimer()
        }
    }

    override fun showSuggestion(model: SuggestionOverlayUiModel) {
        scope.launch(Dispatchers.Main) {
            suggestionOverlayController.showSuggestionOverlay(
                title = model.title,
                mode = model.mode,
                autoDismissMillis = model.autoDismissMillis,
                interactionLockoutMillis = model.interactionLockoutMillis,
                onSnoozeLater = {
                    model.onSnoozeLater()
                },
                onDisableThisSession = {
                    model.onDisableThisSession()
                },
                onDismissOnly = {
                    model.onDismissOnly()
                },
            )
        }
    }

    override fun hideSuggestion() {
        scope.launch(Dispatchers.Main) {
            suggestionOverlayController.hideSuggestionOverlay()
        }
    }
}
