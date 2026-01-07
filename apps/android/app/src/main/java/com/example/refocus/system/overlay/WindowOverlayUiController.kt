package com.example.refocus.system.overlay

import com.example.refocus.core.model.Customize
import com.example.refocus.domain.overlay.port.MiniGameOverlayUiModel
import com.example.refocus.domain.overlay.port.OverlayUiPort
import com.example.refocus.domain.overlay.port.SuggestionOverlayUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * domain/overlay.OverlayUiPort の実装。
 * WindowManager × Compose な UI を実際に操作する。
 */
class WindowOverlayUiController(
    private val scope: CoroutineScope,
    private val timerOverlayController: TimerOverlayController,
    private val suggestionOverlayController: SuggestionOverlayController,
    private val miniGameOverlayController: MiniGameOverlayController,
) : OverlayUiPort {
    override fun applySettings(customize: Customize) {
        scope.launch(Dispatchers.Main) {
            timerOverlayController.overlayCustomize = customize
        }
    }

    override fun showTimer(
        token: String?,
        displayMillisProvider: (Long) -> Long,
        visualMillisProvider: (Long) -> Long,
        onPositionChanged: (x: Int, y: Int) -> Unit,
    ) {
        scope.launch(Dispatchers.Main) {
            timerOverlayController.showTimer(
                token = token,
                displayMillisProvider = displayMillisProvider,
                visualMillisProvider = visualMillisProvider,
                onPositionChanged = onPositionChanged,
            )
        }
    }

    override fun hideTimer(token: String?) {
        scope.launch(Dispatchers.Main) {
            timerOverlayController.hideTimer(token = token)
        }
    }

    override suspend fun showSuggestion(model: SuggestionOverlayUiModel): Boolean =
        withContext(Dispatchers.Main) {
            suggestionOverlayController.showSuggestionOverlay(
                title = model.title,
                mode = model.mode,
                autoDismissMillis = model.autoDismissMillis,
                interactionLockoutMillis = model.interactionLockoutMillis,
                onSnoozeLater = { model.onSnoozeLater() },
                onDisableThisSession = { model.onDisableThisSession() },
                onDismissOnly = { model.onDismissOnly() },
            )
        }

    override fun hideSuggestion() {
        scope.launch(Dispatchers.Main) {
            suggestionOverlayController.hideSuggestionOverlay()
        }
    }

    override suspend fun showMiniGame(model: MiniGameOverlayUiModel): Boolean =
        withContext(Dispatchers.Main) {
            miniGameOverlayController.showMiniGameOverlay(model)
        }

    override fun hideMiniGame() {
        scope.launch(Dispatchers.Main) {
            miniGameOverlayController.hideMiniGameOverlay()
        }
    }
}
