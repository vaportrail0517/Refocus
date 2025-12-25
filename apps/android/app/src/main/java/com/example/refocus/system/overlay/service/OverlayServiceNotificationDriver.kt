package com.example.refocus.system.overlay.service

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.util.formatDurationForTimerBubble
import com.example.refocus.domain.overlay.OverlayCoordinator
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.notification.OverlayNotificationUiState
import com.example.refocus.system.notification.OverlayServiceNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class OverlayServiceNotificationDriver(
    private val scope: CoroutineScope,
    private val overlayCoordinator: OverlayCoordinator,
    private val settingsRepository: SettingsRepository,
    private val appLabelResolver: AppLabelResolver,
    private val notificationController: OverlayServiceNotificationController,
    private val notificationId: Int,
) {
    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var trackingPackage: String? = null

    @Volatile
    private var timerVisible: Boolean = false

    @Volatile
    private var touchMode: TimerTouchMode = TimerTouchMode.Drag

    private val jobs = mutableListOf<Job>()

    fun start() {
        if (jobs.isNotEmpty()) return

        jobs += scope.launch {
            overlayCoordinator.trackingPackageFlow.collect { pkg ->
                trackingPackage = pkg
                requestRefresh()
            }
        }

        jobs += scope.launch {
            overlayCoordinator.timerVisibleFlow.collect { visible ->
                timerVisible = visible
                requestRefresh()
            }
        }

        val overlaySettingsShared = settingsRepository.observeOverlaySettings()
            .shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1,
            )

        jobs += scope.launch {
            overlaySettingsShared.map { it.touchMode }.collect { mode ->
                touchMode = mode
                requestRefresh()
            }
        }

        jobs += scope.launch {
            overlaySettingsShared.map { it.timerTimeMode }.collect {
                // 表示モードが変わったら通知も更新（数値の意味が変わる）
                requestRefresh()
            }
        }

        // 1 秒ごとの経過時間更新
        jobs += scope.launch {
            while (isActive) {
                delay(1_000)
                if (trackingPackage != null) {
                    requestRefresh()
                }
            }
        }

        // 通知を実際に更新する単一ループ
        jobs += scope.launch {
            refresh.collect {
                publishNotification()
            }
        }

        requestRefresh()
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun requestRefresh() {
        refresh.tryEmit(Unit)
    }

    private fun publishNotification() {
        val pkg = trackingPackage
        val state = if (pkg == null) {
            OverlayNotificationUiState(
                isTracking = false,
                trackingAppLabel = null,
                elapsedLabel = null,
                isTimerVisible = false,
                touchMode = touchMode,
            )
        } else {
            val label = appLabelResolver.labelOf(pkg) ?: pkg
            val elapsedMillis = overlayCoordinator.currentTimerDisplayMillis() ?: 0L
            OverlayNotificationUiState(
                isTracking = true,
                trackingAppLabel = label,
                elapsedLabel = formatDurationForTimerBubble(elapsedMillis),
                isTimerVisible = timerVisible,
                touchMode = touchMode,
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
