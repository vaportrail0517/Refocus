package com.example.refocus.system.overlay.service

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.system.permissions.PermissionStateWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class OverlayCorePermissionSupervisor(
    private val scope: CoroutineScope,
    private val permissionStateWatcher: PermissionStateWatcher,
    private val settingsCommand: SettingsCommand,
    private val onCorePermissionMissing: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            // 初回チェック
            checkAndHandleCorePermissions(reason = "service_start")

            // 以降は定期的に確認（差分イベントは watcher 側で抑制される）
            while (isActive) {
                delay(30_000)
                checkAndHandleCorePermissions(reason = "periodic")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun requestImmediateCheck(reason: String) {
        scope.launch { checkAndHandleCorePermissions(reason = reason) }
    }

    private suspend fun checkAndHandleCorePermissions(reason: String) {
        val snapshot = try {
            permissionStateWatcher.checkAndRecord()
        } catch (e: Exception) {
            RefocusLog.e("OverlayService", e) { "Permission check/record failed ($reason)" }
            return
        }

        if (!snapshot.hasAllCorePermissions()) {
            RefocusLog.w("OverlayService") { "core permissions missing ($reason)" }
            try {
                settingsCommand.setOverlayEnabled(
                    enabled = false,
                    source = "service",
                    reason = "core_permission_missing",
                    recordEvent = false,
                )
            } catch (e: Exception) {
                RefocusLog.e("OverlayService", e) { "Failed to disable overlay on missing permissions ($reason)" }
            }
            onCorePermissionMissing()
        }
    }
}
