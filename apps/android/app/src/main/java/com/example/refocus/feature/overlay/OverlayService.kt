package com.example.refocus.feature.overlay

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
import com.example.refocus.R
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.domain.session.SessionManager
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.system.monitor.ForegroundAppMonitor
import com.example.refocus.system.permissions.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var suggestionsRepository: SuggestionsRepository

    @Inject
    lateinit var foregroundAppMonitor: ForegroundAppMonitor

    @Inject
    lateinit var suggestionEngine: SuggestionEngine

    private lateinit var timerOverlayController: TimerOverlayController
    private lateinit var suggestionOverlayController: SuggestionOverlayController
    private lateinit var sessionManager: SessionManager
    private lateinit var overlayOrchestrator: OverlayOrchestrator

    // BroadcastReceiver はそのまま残す（後で中身を少し変更）
    private var screenReceiverRegistered: Boolean = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SHUTDOWN -> {
                    Log.d(TAG, "ACTION_SCREEN_OFF / SHUTDOWN received")
                    overlayOrchestrator.setScreenOn(false)
                    overlayOrchestrator.onScreenOff()
                }

                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "ACTION_USER_PRESENT / SCREEN_ON received")
                    overlayOrchestrator.setScreenOn(true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "onCreate")

        timerOverlayController = TimerOverlayController(
            context = this,
            lifecycleOwner = this
        )
        suggestionOverlayController = SuggestionOverlayController(
            context = this,
            lifecycleOwner = this
        )
        sessionManager = SessionManager(
            sessionRepository = sessionRepository,
            timeSource = timeSource,
            scope = serviceScope,
            logTag = TAG
        )

        overlayOrchestrator = OverlayOrchestrator(
            scope = serviceScope,
            timeSource = timeSource,
            targetsRepository = targetsRepository,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository,
            suggestionsRepository = suggestionsRepository,
            foregroundAppMonitor = foregroundAppMonitor,
            suggestionEngine = suggestionEngine,
            timerOverlayController = timerOverlayController,
            suggestionOverlayController = suggestionOverlayController,
            sessionManager = sessionManager
        )

        startForegroundWithNotification()
        Log.d(TAG, "startForeground done")

        // 権限が揃っていなければ即終了
        if (!canRunOverlay()) {
            Log.w(TAG, "canRunOverlay = false. stopSelf()")
            stopSelf()
            return
        }

        registerScreenReceiver()
        overlayOrchestrator.start()
    }

    override fun onDestroy() {
        isRunning = false
        overlayOrchestrator.stop()
        serviceScope.cancel()
        unregisterScreenReceiver()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Refocus timer",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Refocus timer overlay service"
        }
        nm.createNotificationChannel(channel)
        // アイコンはとりあえずアプリアイコンを流用
        val notification: Notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Refocus が動作しています")
                .setContentText("対象アプリ利用時に経過時間を可視化します")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()
//        startForeground
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun canRunOverlay(): Boolean {
        val hasUsage = PermissionHelper.hasUsageAccess(this)
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        Log.d(TAG, "hasUsage=$hasUsage, hasOverlay=$hasOverlay")
        return hasUsage && hasOverlay
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SHUTDOWN)
            // 画面ON / ユーザ復帰も監視
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
            // 既に解除済みなど
            Log.w(TAG, "unregisterScreenReceiver failed", e)
        }
        screenReceiverRegistered = false
    }
}
