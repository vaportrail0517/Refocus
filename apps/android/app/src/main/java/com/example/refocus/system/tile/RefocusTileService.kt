package com.example.refocus.system.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.refocus.app.MainActivity
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.system.overlay.OverlayService
import com.example.refocus.system.overlay.startOverlayService
import com.example.refocus.system.overlay.stopOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RefocusTileService : TileService() {

    companion object {
        private const val TAG = "RefocusTileService"
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        val context = applicationContext
        if (!PermissionHelper.hasAllCorePermissions(context)) {
            Log.d(TAG, "Core permissions missing. Open app.")
            openApp()
            return
        }

        val currentlyRunning = OverlayService.isRunning
        if (currentlyRunning) {
            scope.launch {
                try {
                    settingsRepository.setOverlayEnabled(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set overlayEnabled=false", e)
                }
                context.stopOverlayService()
                // サービスの onDestroy より先に UI を更新できるように，期待値で反映する．
                updateTile(runningOverride = false)
            }
        } else {
            scope.launch {
                try {
                    settingsRepository.setOverlayEnabled(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set overlayEnabled=true", e)
                }
                context.startOverlayService()
                // startForegroundService 直後は isRunning の更新が遅れる可能性があるため，期待値で反映する．
                updateTile(runningOverride = true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // startActivityAndCollapse(Intent) は deprecated なので，新APIが使える場合は PendingIntent 版を使う．
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(runningOverride: Boolean? = null) {
        val tile = qsTile ?: return

        val enabled = PermissionHelper.hasAllCorePermissions(applicationContext)
        val running = runningOverride ?: OverlayService.isRunning

        tile.state = when {
            !enabled -> Tile.STATE_UNAVAILABLE
            running -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
