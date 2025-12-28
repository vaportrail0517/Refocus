package com.example.refocus.system.overlay

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.gateway.ForegroundAppObserver
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.notification.OverlayNotificationUiState
import com.example.refocus.system.notification.OverlayServiceNotificationController
import com.example.refocus.system.overlay.service.OverlayCorePermissionSupervisor
import com.example.refocus.system.overlay.service.OverlayScreenStateReceiver
import com.example.refocus.system.overlay.service.OverlayServiceIntentHandler
import com.example.refocus.system.overlay.service.OverlayServiceNotificationDriver
import com.example.refocus.system.overlay.service.OverlayServiceRunSupervisor
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.system.permissions.PermissionStateWatcher
import com.example.refocus.system.tile.QsTileStateBroadcaster
import com.example.refocus.system.tile.RefocusTileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    lateinit var settingsCommand: SettingsCommand

    @Inject
    lateinit var suggestionsRepository: SuggestionsRepository

    @Inject
    lateinit var foregroundAppObserver: ForegroundAppObserver

    @Inject
    lateinit var suggestionEngine: SuggestionEngine

    @Inject
    lateinit var suggestionSelector: SuggestionSelector

    @Inject
    lateinit var eventRecorder: EventRecorder

    @Inject
    lateinit var permissionStateWatcher: PermissionStateWatcher

    @Inject
    lateinit var timelineRepository: TimelineRepository

    @Inject
    lateinit var appLabelResolver: AppLabelResolver

    private lateinit var timerOverlayController: TimerOverlayController
    private lateinit var suggestionOverlayController: SuggestionOverlayController
    private lateinit var overlayUiController: WindowOverlayUiGateway
    private lateinit var overlayCoordinator: OverlayCoordinator

    private lateinit var notificationController: OverlayServiceNotificationController

    private var permissionSupervisor: OverlayCorePermissionSupervisor? = null
    private var screenStateReceiver: OverlayScreenStateReceiver? = null
    private var notificationDriver: OverlayServiceNotificationDriver? = null
    private var intentHandler: OverlayServiceIntentHandler? = null
    private var runSupervisor: OverlayServiceRunSupervisor? = null

    @Volatile
    private var isStopping: Boolean = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        RefocusLog.d(TAG) { "onCreate" }

        lifecycleScope.launch {
            try {
                eventRecorder.onServiceStarted()
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record service start event" }
            }
        }

        timerOverlayController =
            TimerOverlayController(
                context = this,
                lifecycleOwner = this,
                timeSource = timeSource,
                scope = serviceScope,
            )
        suggestionOverlayController =
            SuggestionOverlayController(
                context = this,
                lifecycleOwner = this,
            )

        overlayUiController =
            WindowOverlayUiGateway(
                scope = serviceScope,
                timerOverlayController = timerOverlayController,
                suggestionOverlayController = suggestionOverlayController,
            )

        overlayCoordinator =
            OverlayCoordinator(
                scope = serviceScope,
                timeSource = timeSource,
                targetsRepository = targetsRepository,
                settingsRepository = settingsRepository,
                settingsCommand = settingsCommand,
                suggestionsRepository = suggestionsRepository,
                foregroundAppObserver = foregroundAppObserver,
                suggestionEngine = suggestionEngine,
                suggestionSelector = suggestionSelector,
                uiController = overlayUiController,
                eventRecorder = eventRecorder,
                timelineRepository = timelineRepository,
            )

        notificationController = OverlayServiceNotificationController(this)
        startForegroundWithNotification()

        // 通知更新ドライバ
        notificationDriver =
            OverlayServiceNotificationDriver(
                scope = serviceScope,
                overlayCoordinator = overlayCoordinator,
                appLabelResolver = appLabelResolver,
                notificationController = notificationController,
                notificationId = NOTIFICATION_ID,
            ).also { it.start() }

        // overlayEnabled=false になったらサービス自体を止めて，監視を完全に停止する
        runSupervisor =
            OverlayServiceRunSupervisor(
                scope = serviceScope,
                settingsRepository = settingsRepository,
                onOverlayDisabled = ::stopFromSettingsDisabled,
            ).also { it.start() }

        requestTileStateRefresh()
        QsTileStateBroadcaster.notifyExpectedRunning(this, expectedRunning = true)

        if (!canRunOverlay()) {
            RefocusLog.w(TAG) { "canRunOverlay = false. stopSelf()" }
            lifecycleScope.launch {
                try {
                    // サービスが起動できない状況でも，権限状態の変化はタイムラインに残す
                    try {
                        permissionStateWatcher.checkAndRecord()
                    } catch (e: Exception) {
                        RefocusLog.e(
                            TAG,
                            e,
                        ) { "Failed to check/record permission state before stopping" }
                    }
                    settingsCommand.setOverlayEnabled(
                        enabled = false,
                        source = "service",
                        reason = "core_permission_missing",
                        recordEvent = false,
                    )
                } catch (e: Exception) {
                    RefocusLog.e(TAG, e) { "Failed to disable overlay before stopping" }
                }
                stopSelf()
            }
            return
        }

        // 権限状態の変化を継続的に検知し，失効時はフェイルセーフに停止する
        permissionSupervisor =
            OverlayCorePermissionSupervisor(
                scope = serviceScope,
                permissionStateWatcher = permissionStateWatcher,
                settingsCommand = settingsCommand,
                onCorePermissionMissing = { stopSelf() },
            ).also { it.start() }

        // 画面 ON/OFF 監視
        screenStateReceiver =
            OverlayScreenStateReceiver(
                context = this,
                scope = serviceScope,
                overlayCoordinator = overlayCoordinator,
                eventRecorder = eventRecorder,
                onScreenOn = { permissionSupervisor?.requestImmediateCheck(reason = "screen_on") },
            ).also {
                it.register()
                it.syncInitialScreenState()
            }

        // 通知アクションのハンドラ
        intentHandler =
            OverlayServiceIntentHandler(
                scope = serviceScope,
                overlayCoordinator = overlayCoordinator,
                settingsRepository = settingsRepository,
                settingsCommand = settingsCommand,
                actionStop = ACTION_STOP,
                actionToggleTimerVisibility = ACTION_TOGGLE_TIMER_VISIBILITY,
                actionToggleTouchMode = ACTION_TOGGLE_TOUCH_MODE,
                onStopRequested = ::stopFromUserAction,
            )

        overlayCoordinator.start()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val handled = intentHandler?.handle(intent) == true
        if (handled) {
            // STOP は sticky で復帰させない
            if (intent?.action == ACTION_STOP) return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        runBlocking {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    eventRecorder.onServiceStopped()
                } catch (e: Exception) {
                    RefocusLog.e(TAG, e) { "Failed to record service stop event" }
                }
            }
        }

        isRunning = false
        isStopping = true

        // 念のため，Foreground 通知を確実に消す
        notificationDriver?.stop()
        removeForegroundNotification()

        overlayCoordinator.stop()

        screenStateReceiver?.unregister()
        permissionSupervisor?.stop()
        runSupervisor?.stop()
        runSupervisor = null

        requestTileStateRefresh()
        QsTileStateBroadcaster.notifyExpectedRunning(this, expectedRunning = false)
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

        val initial =
            OverlayNotificationUiState(
                isTracking = false,
                trackingAppLabel = null,
                elapsedLabel = null,
                isTimerVisible = false,
                touchMode = TimerTouchMode.Drag,
            )
        val notification = notificationController.build(initial)

        // NOTE:
        // - Android 14 (API 34) 以降でのみ specialUse を指定する
        // - それ未満の OS で未知の type を渡すと例外になる可能性があるため，0 を渡す
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            type,
        )
    }

    private fun stopFromSettingsDisabled() {
        // すでに設定側で overlayEnabled=false になっている前提で，サービスを停止する
        if (isStopping) return
        isStopping = true

        QsTileStateBroadcaster.notifyExpectedRunning(this, expectedRunning = false)

        // 先に通知更新を止めてから foreground を外す
        notificationDriver?.stop()
        removeForegroundNotification()

        // stopSelf は main で呼ぶ
        lifecycleScope.launch {
            stopSelf()
        }
    }

    private fun stopFromUserAction() {
        if (isStopping) return
        isStopping = true

        QsTileStateBroadcaster.notifyExpectedRunning(this, expectedRunning = false)

        // 先に通知更新を止め，ユーザー操作の体感を良くする
        notificationDriver?.stop()
        removeForegroundNotification()

        lifecycleScope.launch {
            try {
                settingsCommand.setOverlayEnabled(
                    enabled = false,
                    source = "service",
                    reason = "user_stop",
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to disable overlay on user stop" }
            }
            stopSelf()
        }
    }

    private fun removeForegroundNotification() {
        // Foreground 状態を外し，通知を取り除く．
        // NotificationManager による通常通知更新も行っているため，cancel も併用して確実に消す．
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            RefocusLog.w(TAG, e) { "Failed to stopForeground" }
        }

        try {
            notificationController.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            RefocusLog.w(TAG, e) { "Failed to cancel foreground notification" }
        }
    }

    private fun canRunOverlay(): Boolean {
        val hasCore = PermissionHelper.hasAllCorePermissions(this)
        RefocusLog.d(TAG) { "canRunOverlay: hasCore=$hasCore" }
        return hasCore
    }

    private fun requestTileStateRefresh() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(this, RefocusTileService::class.java),
            )
        } catch (e: Exception) {
            RefocusLog.w(TAG, e) { "Failed to request QS tile state refresh" }
        }
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
