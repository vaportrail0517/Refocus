package com.example.refocus.system.overlay.service

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.ResilientCoroutines
import com.example.refocus.core.util.formatDurationForNotificationMinutes
import com.example.refocus.domain.overlay.model.OverlayPresentationState
import com.example.refocus.domain.overlay.runtime.OverlayCoordinator
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.notification.OverlayNotificationUiState
import com.example.refocus.system.notification.OverlayServiceNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChangedBy

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

    private data class NotificationStableKey(
        val trackingPackage: String?,
        val isTimerVisible: Boolean,
        val touchMode: Any,
        val timerTimeMode: Any,
        val elapsedMinuteBucket: Long,
    )

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job =
            ResilientCoroutines.launchResilient(
                scope = scope,
                tag = TAG,
            ) {
                // 通知は分単位での表示にするため，経過時間の更新は「分が変わった」タイミングだけで十分．
                // これにより毎秒の通知更新を避け，端末負荷を大きく下げる．
                overlayCoordinator.presentationStateFlow
                    .distinctUntilChangedBy { state ->
                        NotificationStableKey(
                            trackingPackage = state.trackingPackage,
                            isTimerVisible = state.isTimerVisible,
                            touchMode = state.touchMode,
                            timerTimeMode = state.timerTimeMode,
                            elapsedMinuteBucket = (state.timerDisplayMillis ?: 0L) / 60_000L,
                        )
                    }.collect { state ->
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
                    elapsedLabel = formatDurationForNotificationMinutes(elapsedMillis),
                    elapsedMillis = elapsedMillis,
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
