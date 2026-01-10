package com.example.refocus.system.overlay.service

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.ResilientCoroutines
import com.example.refocus.core.util.formatDurationForTimerBubble
import com.example.refocus.domain.overlay.model.OverlayPresentationState
import com.example.refocus.domain.overlay.runtime.OverlayCoordinator
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.notification.OverlayNotificationUiState
import com.example.refocus.system.notification.OverlayServiceNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample

internal class OverlayServiceNotificationDriver(
    private val scope: CoroutineScope,
    private val overlayCoordinator: OverlayCoordinator,
    private val appLabelResolver: AppLabelResolver,
    private val notificationController: OverlayServiceNotificationController,
    private val notificationId: Int,
) {
    companion object {
        private const val TAG = "OverlayServiceNotificationDriver"

        // timerDisplayMillis は毎秒更新され得るため，通知更新をそのまま追従させると負荷が高い端末がある．
        // 秒単位での正確さはオーバーレイに任せ，通知はサンプル更新に落とす．
        private const val NOTIFICATION_TIME_SAMPLE_MS: Long = 5_000L
    }

    private data class NotificationStableKey(
        val trackingPackage: String?,
        val isTimerVisible: Boolean,
        val touchMode: Any,
        val timerTimeMode: Any,
    )

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job =
            ResilientCoroutines.launchResilient(
                scope = scope,
                tag = TAG,
            ) {
                val immediateFlow =
                    overlayCoordinator.presentationStateFlow
                        .distinctUntilChangedBy { state ->
                            NotificationStableKey(
                                trackingPackage = state.trackingPackage,
                                isTimerVisible = state.isTimerVisible,
                                touchMode = state.touchMode,
                                timerTimeMode = state.timerTimeMode,
                            )
                        }

                val sampledFlow =
                    overlayCoordinator.presentationStateFlow
                        .sample(NOTIFICATION_TIME_SAMPLE_MS)

                merge(immediateFlow, sampledFlow)
                    .distinctUntilChanged()
                    .collect { state ->
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
