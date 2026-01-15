package com.example.refocus.system.overlay

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.domain.overlay.port.MiniGameOverlayUiModel
import com.example.refocus.domain.overlay.port.OverlayUiPort
import com.example.refocus.domain.overlay.port.SuggestionOverlayUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * domain/overlay.OverlayUiPort の実装．
 * WindowManager × Compose な UI を実際に操作する．
 */
class WindowOverlayUiController(
    private val scope: CoroutineScope,
    private val timerOverlayController: TimerOverlayController,
    private val suggestionOverlayController: SuggestionOverlayController,
    private val miniGameOverlayController: MiniGameOverlayController,
) : OverlayUiPort {
    companion object {
        private const val TAG = "WindowOverlayUi"
    }

    override fun applySettings(customize: Customize) {
        scope.launch(Dispatchers.Main) {
            try {
                timerOverlayController.overlayCustomize = customize
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "applySettings failed" }
            }
        }
    }

    override fun showTimer(
        token: String?,
        displayMillisProvider: (Long) -> Long,
        visualMillisProvider: (Long) -> Long,
        effectMillisProvider: (Long) -> Long,
        onPositionChanged: (x: Int, y: Int) -> Unit,
    ) {
        scope.launch(Dispatchers.Main) {
            try {
                timerOverlayController.showTimer(
                    token = token,
                    displayMillisProvider = displayMillisProvider,
                    visualMillisProvider = visualMillisProvider,
                    effectMillisProvider = effectMillisProvider,
                    onPositionChanged = onPositionChanged,
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "showTimer failed" }
            }
        }
    }

    override fun hideTimer(token: String?) {
        scope.launch(Dispatchers.Main) {
            try {
                timerOverlayController.hideTimer(token = token)
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "hideTimer failed" }
            }
        }
    }

    override suspend fun showSuggestion(model: SuggestionOverlayUiModel): Boolean =
        withContext(Dispatchers.Main) {
            try {
                suggestionOverlayController.showSuggestionOverlay(
                    title = model.title,
                    targetPackageName = model.targetPackageName,
                    mode = model.mode,
                    autoDismissMillis = model.autoDismissMillis,
                    interactionLockoutMillis = model.interactionLockoutMillis,
                    onSnoozeLater = { model.onSnoozeLater() },
                    onCloseTargetApp = { model.onCloseTargetApp() },
                    onDismissOnly = { model.onDismissOnly() },
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "showSuggestion failed" }
                false
            }
        }

    override fun hideSuggestion() {
        scope.launch(Dispatchers.Main) {
            try {
                suggestionOverlayController.hideSuggestionOverlay()
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "hideSuggestion failed" }
            }
        }
    }

    override suspend fun showMiniGame(
        model: MiniGameOverlayUiModel,
        token: Long?,
    ): Boolean =
        withContext(Dispatchers.Main) {
            try {
                miniGameOverlayController.showMiniGameOverlay(model = model, token = token)
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "showMiniGame failed" }
                false
            }
        }

    override fun hideMiniGame(token: Long?) {
        scope.launch(Dispatchers.Main) {
            try {
                miniGameOverlayController.hideMiniGameOverlay(token = token)
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "hideMiniGame failed" }
            }
        }
    }
}
