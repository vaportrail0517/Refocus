package com.example.refocus.system.overlay.service

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.formatDurationForTimerBubble
import com.example.refocus.core.util.ResilientCoroutines
import com.example.refocus.domain.overlay.model.OverlayPresentationState
import com.example.refocus.domain.overlay.runtime.OverlayCoordinator
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.notification.OverlayNotificationUiState
import com.example.refocus.system.notification.OverlayServiceNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class OverlayServiceNotificationDriver(
    private val scope: CoroutineScope,
    private val overlayCoordinator: OverlayCoordinator,
    private val appLabelResolver: AppLabelResolver,
    private val notificationController: OverlayServiceNotificationController,
    private val notificationId: Int,
) {
    companion object {
        private const val TAG = "OverlayServiceNotificationDriver"
    }

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job =
            ResilientCoroutines.launchResilient(
                scope = scope,
                tag = TAG,
            ) {
                overlayCoordinator.presentationStateFlow.collect { state ->
                    publishNotification(state)
                }
            }

        publishNotification(overlayCoordinator.currentPresentationState())
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun publishNotification(presentation: OverlayPresentationState) {
        val pkg = presentation.trackingPackage
        val state =
            if (pkg == null) {
                OverlayNotificationUiState(
                    isTracking = false,
                    trackingAppLabel = null,
                    elapsedLabel = null,
                    isTimerVisible = false,
                    touchMode = presentation.touchMode,
                )
            } else {
                val label = appLabelResolver.labelOf(pkg) ?: pkg
                val elapsedMillis = presentation.timerDisplayMillis ?: 0L
                OverlayNotificationUiState(
                    isTracking = true,
                    trackingAppLabel = label,
                    elapsedLabel = formatDurationForTimerBubble(elapsedMillis),
                    isTimerVisible = presentation.isTimerVisible,
                    touchMode = presentation.touchMode,
                )
            }

        try {
            notificationController.notify(notificationId, state)
        } catch (e: SecurityException) {
            // Android 13+ で通知権限が拒否されている場合など
            RefocusLog.w("OverlayService", e) { "Notification update blocked" }
        } catch (e: Exception) {
            RefocusLog.e("OverlayService", e) { "Failed to update notification" }
        }
    }
}
