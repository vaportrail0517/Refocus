package com.example.refocus.system.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.refocus.R
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.domain.overlay.OverlayCoordinator
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.system.monitor.ForegroundAppMonitor
import com.example.refocus.system.permissions.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : LifecycleService() {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var timeSource: TimeSource

    @Inject
    lateinit var targetsRepository: TargetsRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var suggestionsRepository: SuggestionsRepository

    @Inject
    lateinit var foregroundAppMonitor: ForegroundAppMonitor

    @Inject
    lateinit var suggestionEngine: SuggestionEngine

    @Inject
    lateinit var suggestionSelector: SuggestionSelector

    @Inject
    lateinit var eventRecorder: EventRecorder

    @Inject
    lateinit var timelineRepository: TimelineRepository

    private lateinit var timerOverlayController: TimerOverlayController
    private lateinit var suggestionOverlayController: SuggestionOverlayController
    private lateinit var overlayUiController: WindowOverlayUiGateway
    private lateinit var overlayCoordinator: OverlayCoordinator

    // 画面 ON/OFF を受け取る BroadcastReceiver
    private var screenReceiverRegistered: Boolean = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SHUTDOWN -> {
                    Log.d(TAG, "ACTION_SCREEN_OFF / SHUTDOWN received")
                    overlayCoordinator.setScreenOn(false)
                    overlayCoordinator.onScreenOff()

                    lifecycleScope.launch {
                        try {
                            eventRecorder.onScreenOff()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to record screen off event", e)
                        }
                    }
                }

                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "ACTION_USER_PRESENT / SCREEN_ON received")
                    overlayCoordinator.setScreenOn(true)

                    lifecycleScope.launch {
                        try {
                            eventRecorder.onScreenOn()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to record screen on event", e)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "onCreate")

        lifecycleScope.launch {
            try {
                eventRecorder.onServiceStarted()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record service start event", e)
            }
        }

        timerOverlayController = TimerOverlayController(
            context = this,
            lifecycleOwner = this,
            timeSource = timeSource,
            scope = serviceScope,
        )
        suggestionOverlayController = SuggestionOverlayController(
            context = this,
            lifecycleOwner = this
        )

        overlayUiController = WindowOverlayUiGateway(
            scope = serviceScope,
            timerOverlayController = timerOverlayController,
            suggestionOverlayController = suggestionOverlayController,
        )

        overlayCoordinator = OverlayCoordinator(
            scope = serviceScope,
            timeSource = timeSource,
            targetsRepository = targetsRepository,
            settingsRepository = settingsRepository,
            suggestionsRepository = suggestionsRepository,
            foregroundAppMonitor = foregroundAppMonitor,
            suggestionEngine = suggestionEngine,
            suggestionSelector = suggestionSelector,
            uiController = overlayUiController,
            eventRecorder = eventRecorder,
            timelineRepository = timelineRepository,
        )

        startForegroundWithNotification()
        Log.d(TAG, "startForeground done")

        if (!canRunOverlay()) {
            Log.w(TAG, "canRunOverlay = false. stopSelf()")
            stopSelf()
            return
        }

        registerScreenReceiver()
        overlayCoordinator.start()
    }

    override fun onDestroy() {
        isRunning = false
        overlayCoordinator.stop()
        serviceScope.cancel()
        unregisterScreenReceiver()
        super.onDestroy()

        lifecycleScope.launch {
            try {
                eventRecorder.onServiceStopped()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record service stop event", e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundWithNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Refocus timer",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Refocus timer overlay service"
        }
        nm.createNotificationChannel(channel)

        val notification: Notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Refocus が動作しています")
                .setContentText("対象アプリ利用時に経過時間を可視化します")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun canRunOverlay(): Boolean {
        val hasCore = PermissionHelper.hasAllCorePermissions(this)
        Log.d(TAG, "canRunOverlay: hasCore=$hasCore")
        return hasCore
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "unregisterScreenReceiver failed", e)
        }
        screenReceiverRegistered = false
    }
}

fun Context.startOverlayService() {
    val intent = Intent(this, OverlayService::class.java)
    this.startForegroundService(intent)
}

fun Context.stopOverlayService() {
    val intent = Intent(this, OverlayService::class.java)
    stopService(intent)
}
