package com.example.refocus.system.permissions

import android.content.Context
import com.example.refocus.core.model.PermissionSnapshot
import com.example.refocus.domain.permissions.port.PermissionStatusProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI などから「現在の権限状態」を取得するための system 実装．
 *
 * ポイント
 * - 初期表示用に同期で読めるスナップショット（readCurrentInstant）を提供する
 * - 画面復帰などで最新化したい場合は refreshAndRecord を呼び，タイムラインへのイベント記録も行う
 */
@Singleton
class AndroidPermissionStatusProvider
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val permissionStateWatcher: PermissionStateWatcher,
    ) : PermissionStatusProvider {
        override fun readCurrentInstant(): PermissionSnapshot {
            val usageGranted =
                safePermissionCheck {
                    PermissionHelper.hasUsageAccess(appContext)
                }
            val overlayGranted =
                safePermissionCheck {
                    PermissionHelper.hasOverlayPermission(appContext)
                }
            val notificationGranted =
                safePermissionCheck {
                    PermissionHelper.hasNotificationPermission(appContext)
                }

            // lastCheckedAtMillis は「イベント記録用の厳密値」ではないため 0 にしておく
            return PermissionSnapshot(
                usageGranted = usageGranted,
                overlayGranted = overlayGranted,
                notificationGranted = notificationGranted,
                lastCheckedAtMillis = 0L,
            )
        }

        override suspend fun refreshAndRecord(): PermissionSnapshot = permissionStateWatcher.checkAndRecord()

        private inline fun safePermissionCheck(block: () -> Boolean): Boolean =
            try {
                block()
            } catch (_: Exception) {
                false
            }
    }
