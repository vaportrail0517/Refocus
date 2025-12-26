package com.example.refocus.system.permissions

import android.content.Context
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionSnapshot
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.permissions.PermissionSnapshotStore
import com.example.refocus.domain.timeline.EventRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 必須権限（UsageStats / Overlay）の状態変化を検知し，タイムラインへイベントとして記録する．
 *
 * 設計方針
 * - 「差分検知」に必要な直近スナップショットは DataStore に保持する
 * - タイムライン（Room）には「変化があったときだけ」 PermissionEvent を追記する
 */
@Singleton
class PermissionStateWatcher @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val timeSource: TimeSource,
    private val permissionSnapshotStore: PermissionSnapshotStore,
    private val eventRecorder: EventRecorder,
) {

    private val mutex = Mutex()

    /**
     * 現在の権限状態をチェックし，前回スナップショットとの差分があれば PermissionEvent を記録する．
     *
     * @return 現在のスナップショット
     */
    suspend fun checkAndRecord(): PermissionSnapshot = mutex.withLock {
        val now = timeSource.nowMillis()

        val usageGranted = safePermissionCheck {
            PermissionHelper.hasUsageAccess(appContext)
        }

        val overlayGranted = safePermissionCheck {
            PermissionHelper.hasOverlayPermission(appContext)
        }

        val current = PermissionSnapshot(
            usageGranted = usageGranted,
            overlayGranted = overlayGranted,
            lastCheckedAtMillis = now,
        )

        val previous = permissionSnapshotStore.readOrNull()

        if (previous == null) {
            // 初回は「現状をタイムラインで説明できる」ように，ベースラインとして 2 種類を記録する．
            recordPermissionEvent(PermissionKind.UsageStats, usageGranted)
            recordPermissionEvent(PermissionKind.Overlay, overlayGranted)
            permissionSnapshotStore.write(current)
            return@withLock current
        }

        val changed = mutableListOf<Pair<PermissionKind, Boolean>>()
        if (previous.usageGranted != current.usageGranted) {
            changed.add(PermissionKind.UsageStats to current.usageGranted)
        }
        if (previous.overlayGranted != current.overlayGranted) {
            changed.add(PermissionKind.Overlay to current.overlayGranted)
        }

        if (changed.isNotEmpty()) {
            for ((kind, granted) in changed) {
                recordPermissionEvent(kind, granted)
            }
            // イベント記録が成功した場合のみスナップショット更新（失敗時は次回再試行できる）
            permissionSnapshotStore.write(current)
        }

        return@withLock current
    }

    private suspend fun recordPermissionEvent(kind: PermissionKind, granted: Boolean) {
        val state = if (granted) PermissionState.Granted else PermissionState.Revoked
        try {
            eventRecorder.onPermissionChanged(kind, state)
        } catch (e: Exception) {
            RefocusLog.e("Permissions", e) { "Failed to record permission change: $kind -> $state" }
            throw e
        }
    }

    private inline fun safePermissionCheck(block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (e: Exception) {
            RefocusLog.w("Permissions", e) { "Permission check failed. treat as not granted" }
            false
        }
    }
}
