package com.example.refocus.system.permissions

import android.content.Context
import com.example.refocus.core.model.PermissionSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI などから「現在の権限状態」を取得するための窓口．
 *
 * ポイント
 * - 初期表示用に同期で読めるスナップショット（readCurrentInstant）を提供する
 * - 画面復帰などで最新化したい場合は refreshAndRecord を呼び，タイムラインへのイベント記録も行う
 */
@Singleton
class PermissionStatusProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val permissionStateWatcher: PermissionStateWatcher,
) {

    /**
     * 現在の権限状態を同期的にチェックして返す．
     *
     * これは UI の初期表示で「とりあえず今どう見えるか」を出す用途で，
     * タイムライン記録や DataStore 更新は行わない（suspend を避けるため）．
     */
    fun readCurrentInstant(): PermissionSnapshot {
        val usageGranted = safePermissionCheck {
            PermissionHelper.hasUsageAccess(appContext)
        }
        val overlayGranted = safePermissionCheck {
            PermissionHelper.hasOverlayPermission(appContext)
        }
        val notificationGranted = safePermissionCheck {
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

    /**
     * 現在の権限状態をチェックし，差分があればタイムラインへ記録する．
     */
    suspend fun refreshAndRecord(): PermissionSnapshot {
        return permissionStateWatcher.checkAndRecord()
    }

    private inline fun safePermissionCheck(block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (_: Exception) {
            false
        }
    }
}
