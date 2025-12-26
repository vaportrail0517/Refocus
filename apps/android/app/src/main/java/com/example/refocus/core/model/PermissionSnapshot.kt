package com.example.refocus.core.model

/**
 * 直近に観測した権限状態のスナップショット．
 *
 * - 変更検知（差分イベント記録）に使う
 * - DB（タイムライン）とは別に保持する（実体は DataStore 等）
 */
data class PermissionSnapshot(
    val usageGranted: Boolean,
    val overlayGranted: Boolean,
    val lastCheckedAtMillis: Long,
) {
    fun hasAllCorePermissions(): Boolean = usageGranted && overlayGranted
}
