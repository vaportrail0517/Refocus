package com.example.refocus.system.overlay.service

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.port.OverlayServiceController
import com.example.refocus.domain.overlay.port.OverlayServiceStatusProvider
import com.example.refocus.domain.permissions.port.PermissionStatusProvider
import com.example.refocus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 期待状態（overlayEnabled と権限）と実状態（サービス稼働）を突き合わせて，不一致なら修復する．
 *
 * 方針
 * - 期待状態: overlayEnabled=true かつ「コア権限（usage/overlay）」が揃っている
 * - 実状態: OverlayServiceStatusProvider（ハートビートベース）
 * - 不一致のときのみ start/stop を実行する（過剰な起動を避ける）
 */
@Singleton
class OverlayRunReconciler
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val permissionStatusProvider: PermissionStatusProvider,
        private val overlayServiceStatusProvider: OverlayServiceStatusProvider,
        private val overlayServiceController: OverlayServiceController,
        private val timeSource: TimeSource,
    ) {
        companion object {
            private const val TAG = "OverlayReconcile"

            // 画面復帰などで連続して呼ばれた場合でも，過剰に start/stop を連打しないための最小間隔
            private const val MIN_ATTEMPT_INTERVAL_MS = 5_000L
        }

        private val mutex = Mutex()

        private var lastStartAttemptElapsed: Long = 0L
        private var lastStopAttemptElapsed: Long = 0L

        suspend fun ensureConsistent(source: String) {
            mutex.withLock {
                val settings = settingsRepository.observeOverlaySettings().first()

                val permission = permissionStatusProvider.readCurrentInstant()
                val hasCorePermissions = permission.usageGranted && permission.overlayGranted

                val expectedRunning = settings.overlayEnabled && hasCorePermissions
                val actualRunning = overlayServiceStatusProvider.isRunning()

                if (expectedRunning && !actualRunning) {
                    val now = timeSource.elapsedRealtime()
                    if (now - lastStartAttemptElapsed < MIN_ATTEMPT_INTERVAL_MS) return
                    lastStartAttemptElapsed = now

                    RefocusLog.d(TAG) {
                        "expectedRunning=true but actualRunning=false. try start. source=$source"
                    }
                    overlayServiceController.startIfReady(source = "reconcile:$source")
                    return
                }

                if (!expectedRunning && actualRunning) {
                    val now = timeSource.elapsedRealtime()
                    if (now - lastStopAttemptElapsed < MIN_ATTEMPT_INTERVAL_MS) return
                    lastStopAttemptElapsed = now

                    RefocusLog.d(TAG) {
                        "expectedRunning=false but actualRunning=true. stop. source=$source"
                    }
                    overlayServiceController.stop(source = "reconcile:$source")
                }
            }
        }
    }
