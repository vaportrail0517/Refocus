package com.example.refocus.system.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.system.AppLaunchIntents
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
    lateinit var settingsCommand: SettingsCommand

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var isTileReceiverRegistered: Boolean = false

    private val tileStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != QsTileStateBroadcaster.ACTION_TILE_STATE_CHANGED) return
            val expectedRunning = intent.getBooleanExtra(
                QsTileStateBroadcaster.EXTRA_EXPECTED_RUNNING,
                OverlayService.isRunning,
            )
            updateTile(runningOverride = expectedRunning)
        }
    }

    private fun registerTileStateReceiverIfNeeded() {
        if (isTileReceiverRegistered) return
        val filter = IntentFilter(QsTileStateBroadcaster.ACTION_TILE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tileStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(tileStateReceiver, filter)
        }
        isTileReceiverRegistered = true
    }

    private fun unregisterTileStateReceiverIfNeeded() {
        if (!isTileReceiverRegistered) return
        try {
            unregisterReceiver(tileStateReceiver)
        } catch (_: IllegalArgumentException) {
        } finally {
            isTileReceiverRegistered = false
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        registerTileStateReceiverIfNeeded()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterTileStateReceiverIfNeeded()
    }

    override fun onClick() {
        super.onClick()

        val context = applicationContext
        if (!PermissionHelper.hasAllCorePermissions(context)) {
            RefocusLog.d(TAG) { "Core permissions missing. Open app." }
            openApp()
            return
        }

        val currentlyRunning = OverlayService.isRunning
        if (currentlyRunning) {
            scope.launch {
                try {
                    settingsCommand.setOverlayEnabled(
                        enabled = false,
                        source = "tile",
                        reason = "toggle_off"
                    )
                } catch (e: Exception) {
                    RefocusLog.e(
                        TAG,
                        e
                    ) { "Failed to set overlayEnabled=false via SettingsCommand" }
                }
                context.stopOverlayService()
                // サービスの onDestroy より先に UI を更新できるように，期待値で反映する．
                updateTile(runningOverride = false)
            }
        } else {
            scope.launch {
                try {
                    settingsCommand.setOverlayEnabled(
                        enabled = true,
                        source = "tile",
                        reason = "toggle_on"
                    )
                } catch (e: Exception) {
                    RefocusLog.e(TAG, e) { "Failed to set overlayEnabled=true via SettingsCommand" }
                }
                context.startOverlayService()
                // startForegroundService 直後は isRunning の更新が遅れる可能性があるため，期待値で反映する．
                updateTile(runningOverride = true)
            }
        }
    }

    override fun onDestroy() {
        unregisterTileStateReceiverIfNeeded()
        super.onDestroy()
        scope.cancel()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = AppLaunchIntents.mainActivity(this).apply {
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
