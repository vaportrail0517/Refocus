package com.example.refocus.system.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.util.TimeSource
import com.example.refocus.core.util.formatDurationForTimerBubble
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.domain.overlay.OverlayCoordinator
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.monitor.ForegroundAppMonitor
import com.example.refocus.system.notification.OverlayNotificationUiState
import com.example.refocus.system.notification.OverlayServiceNotificationController
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.system.tile.RefocusTileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : LifecycleService() {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1

        const val ACTION_STOP = "com.example.refocus.action.OVERLAY_STOP"
        const val ACTION_TOGGLE_TIMER_VISIBILITY =
            "com.example.refocus.action.OVERLAY_TOGGLE_TIMER_VISIBILITY"
        const val ACTION_TOGGLE_TOUCH_MODE = "com.example.refocus.action.OVERLAY_TOGGLE_TOUCH_MODE"

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

    @Inject
    lateinit var appLabelResolver: AppLabelResolver

    private lateinit var timerOverlayController: TimerOverlayController
    private lateinit var suggestionOverlayController: SuggestionOverlayController
    private lateinit var overlayUiController: WindowOverlayUiGateway
    private lateinit var overlayCoordinator: OverlayCoordinator

    private lateinit var notificationController: OverlayServiceNotificationController

    private val notificationRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var isStopping: Boolean = false

    @Volatile
    private var notificationTrackingPackage: String? = null

    @Volatile
    private var notificationTimerVisible: Boolean = false

    @Volatile
    private var notificationTouchMode: TimerTouchMode = TimerTouchMode.Drag

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

        notificationController = OverlayServiceNotificationController(this)
        startForegroundWithNotification()
        startNotificationLoops()
        requestTileStateRefresh()

        if (!canRunOverlay()) {
            Log.w(TAG, "canRunOverlay = false. stopSelf()")
            lifecycleScope.launch {
                try {
                    settingsRepository.setOverlayEnabled(false)
                } catch (_: Exception) {
                }
                stopSelf()
            }
            return
        }

        registerScreenReceiver()
        overlayCoordinator.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFromUserAction()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_TIMER_VISIBILITY -> {
                overlayCoordinator.toggleTimerVisibilityForCurrentSession()
                notificationRefresh.tryEmit(Unit)
            }

            ACTION_TOGGLE_TOUCH_MODE -> {
                serviceScope.launch {
                    try {
                        settingsRepository.updateOverlaySettings { current ->
                            current.copy(
                                touchMode = if (current.touchMode == TimerTouchMode.Drag) {
                                    TimerTouchMode.PassThrough
                                } else {
                                    TimerTouchMode.Drag
                                }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to toggle touch mode", e)
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        runBlocking {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    eventRecorder.onServiceStopped()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record service stop event", e)
                }
            }
        }
        isRunning = false
        isStopping = true

        // 念のため，Foreground 通知を確実に消す
        removeForegroundNotification()

        overlayCoordinator.stop()
        unregisterScreenReceiver()
        requestTileStateRefresh()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundWithNotification() {
        notificationController.ensureChannel()

        val initial = OverlayNotificationUiState(
            isTracking = false,
            trackingAppLabel = null,
            elapsedLabel = null,
            isTimerVisible = false,
            touchMode = TimerTouchMode.Drag,
        )
        val notification = notificationController.build(initial)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun startNotificationLoops() {
        // 状態の購読
        serviceScope.launch {
            overlayCoordinator.trackingPackageFlow
                .collect { pkg ->
                    notificationTrackingPackage = pkg
                    notificationRefresh.tryEmit(Unit)
                }
        }

        serviceScope.launch {
            overlayCoordinator.timerVisibleFlow
                .collect { visible ->
                    notificationTimerVisible = visible
                    notificationRefresh.tryEmit(Unit)
                }
        }

        serviceScope.launch {
            settingsRepository.observeOverlaySettings()
                .map { it.touchMode }
                .collect { mode ->
                    notificationTouchMode = mode
                    notificationRefresh.tryEmit(Unit)
                }
        }

        // 1 秒ごとの経過時間更新
        serviceScope.launch {
            while (isActive) {
                delay(1_000)
                if (notificationTrackingPackage != null) {
                    notificationRefresh.tryEmit(Unit)
                }
            }
        }

        // 通知を実際に更新する単一ループ
        serviceScope.launch {
            notificationRefresh.collect {
                publishNotification()
            }
        }

        notificationRefresh.tryEmit(Unit)
    }

    private fun publishNotification() {
        if (isStopping) return

        val pkg = notificationTrackingPackage
        val state = if (pkg == null) {
            OverlayNotificationUiState(
                isTracking = false,
                trackingAppLabel = null,
                elapsedLabel = null,
                isTimerVisible = false,
                touchMode = notificationTouchMode,
            )
        } else {
            val label = appLabelResolver.labelOf(pkg) ?: pkg
            val elapsedMillis = overlayCoordinator.currentElapsedMillis() ?: 0L
            OverlayNotificationUiState(
                isTracking = true,
                trackingAppLabel = label,
                elapsedLabel = formatDurationForTimerBubble(elapsedMillis),
                isTimerVisible = notificationTimerVisible,
                touchMode = notificationTouchMode,
            )
        }

        try {
            notificationController.notify(NOTIFICATION_ID, state)
        } catch (e: SecurityException) {
            // Android 13+ で通知権限が拒否されている場合など
            Log.w(TAG, "Notification update blocked", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun stopFromUserAction() {
        if (isStopping) return
        isStopping = true

        // 先に通知を消して，ユーザー操作の体感を良くする
        removeForegroundNotification()

        lifecycleScope.launch {
            try {
                settingsRepository.setOverlayEnabled(false)
            } catch (_: Exception) {
            }
            stopSelf()
        }
    }

    private fun removeForegroundNotification() {
        // Foreground 状態を外し，通知を取り除く．
        // NotificationManager による通常通知更新も行っているため，cancel も併用して確実に消す．
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }

        try {
            notificationController.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun canRunOverlay(): Boolean {
        val hasCore = PermissionHelper.hasAllCorePermissions(this)
        Log.d(TAG, "canRunOverlay: hasCore=$hasCore")
        return hasCore
    }

    private fun requestTileStateRefresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    this,
                    ComponentName(this, RefocusTileService::class.java)
                )
            } catch (_: Exception) {
            }
        }
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
