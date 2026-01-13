package com.example.refocus.system.overlay.service

import android.content.Intent
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.domain.overlay.runtime.OverlayCoordinator
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.settings.SettingsCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class OverlayServiceIntentHandler(
    private val scope: CoroutineScope,
    private val overlayCoordinator: OverlayCoordinator,
    private val settingsRepository: SettingsRepository,
    private val settingsCommand: SettingsCommand,
    private val actionStop: String,
    private val actionToggleTimerVisibility: String,
    private val actionToggleTouchMode: String,
    private val actionSelfHeal: String,
    private val onStopRequested: () -> Unit,
) {
    fun handle(intent: Intent?): Boolean {
        when (intent?.action) {
            actionStop -> {
                onStopRequested()
                return true
            }

            actionToggleTimerVisibility -> {
                overlayCoordinator.toggleTimerVisibilityForCurrentSession()
                return true
            }

            actionSelfHeal -> {
                overlayCoordinator.requestForegroundTrackingRestart(reason = "intent_self_heal")
                return true
            }

            actionToggleTouchMode -> {
                scope.launch {
                    try {
                        val current = settingsRepository.observeOverlaySettings().first()
                        val newTouchMode =
                            if (current.touchMode == TimerTouchMode.Drag) {
                                TimerTouchMode.PassThrough
                            } else {
                                TimerTouchMode.Drag
                            }
                        settingsCommand.setTouchMode(
                            mode = newTouchMode,
                            source = "service_notification",
                            reason = "toggle_touch_mode",
                            markPresetCustom = false,
                        )
                    } catch (e: Exception) {
                        RefocusLog.e("OverlayService", e) { "Failed to toggle touch mode" }
                    }
                }
                return true
            }
        }
        return false
    }
}
