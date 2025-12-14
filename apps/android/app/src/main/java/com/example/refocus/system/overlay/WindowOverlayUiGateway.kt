package com.example.refocus.system.overlay

import com.example.refocus.core.model.Customize
import com.example.refocus.domain.overlay.OverlayUiGateway
import com.example.refocus.domain.overlay.SuggestionOverlayUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * domain/overlay.OverlayUiGateway の実装。
 * WindowManager × Compose な UI を実際に操作する。
 */
class WindowOverlayUiGateway(
    private val scope: CoroutineScope,
    private val timerOverlayController: TimerOverlayController,
    private val suggestionOverlayController: SuggestionOverlayController,
) : OverlayUiGateway {

    override fun applySettings(customize: Customize) {
        scope.launch(Dispatchers.Main) {
            timerOverlayController.overlayCustomize = customize
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

    override suspend fun showSuggestion(model: SuggestionOverlayUiModel): Boolean {
        return withContext(Dispatchers.Main) {
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
