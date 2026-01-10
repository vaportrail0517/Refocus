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
import com.example.refocus.domain.monitor.port.ForegroundAppObserver
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.domain.overlay.runtime.OverlayCoordinator
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.gateway.di.getOverlayHealthStoreOrNull
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.notification.OverlayNotificationUiState
import com.example.refocus.system.notification.OverlayServiceNotificationController
import com.example.refocus.system.overlay.service.OverlayCorePermissionSupervisor
import com.example.refocus.system.overlay.service.OverlayScreenStateReceiver
import com.example.refocus.system.overlay.service.OverlayServiceComponentsFactory
import com.example.refocus.system.overlay.service.OverlayServiceIntentHandler
import com.example.refocus.system.overlay.service.OverlayServiceNotificationDriver
import com.example.refocus.system.overlay.service.OverlayServiceRunSupervisor
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.system.permissions.PermissionStateWatcher
import com.example.refocus.system.tile.QsTileStateBroadcaster
import com.example.refocus.system.tile.RefocusTileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : LifecycleService() {
    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1

        private const val HEARTBEAT_INTERVAL_MS = 10_000L

        private const val ERROR_SUMMARY_MAX = 160

        const val ACTION_STOP = "com.example.refocus.action.OVERLAY_STOP"
        const val ACTION_TOGGLE_TIMER_VISIBILITY =
            "com.example.refocus.action.OVERLAY_TOGGLE_TIMER_VISIBILITY"
        const val ACTION_TOGGLE_TOUCH_MODE = "com.example.refocus.action.OVERLAY_TOGGLE_TOUCH_MODE"

        const val ACTION_SELF_HEAL = "com.example.refocus.action.OVERLAY_SELF_HEAL"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default +
                CoroutineExceptionHandler { _, throwable ->
                    // ここで握りつぶさないと，例外がプロセス全体のクラッシュへ波及する可能性がある．
                    if (throwable is CancellationException) return@CoroutineExceptionHandler
                    RefocusLog.e(TAG, throwable) { "Uncaught coroutine exception in serviceScope" }
                },
        )

    private val componentsFactory = OverlayServiceComponentsFactory()

    @Inject
    lateinit var timeSource: TimeSource

    @Inject
    lateinit var overlayHealthStore: OverlayHealthStore

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
    private lateinit var miniGameOverlayController: MiniGameOverlayController
    private lateinit var overlayUiController: WindowOverlayUiController
    private lateinit var overlayCoordinator: OverlayCoordinator

    private lateinit var notificationController: OverlayServiceNotificationController

    private var permissionSupervisor: OverlayCorePermissionSupervisor? = null
    private var screenStateReceiver: OverlayScreenStateReceiver? = null
    private var notificationDriver: OverlayServiceNotificationDriver? = null
    private var intentHandler: OverlayServiceIntentHandler? = null
    private var runSupervisor: OverlayServiceRunSupervisor? = null

    private var heartbeatJob: Job? = null

    @Volatile
    private var isStopping: Boolean = false

    private val stopEventRecorded = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        RefocusLog.d(TAG) { "onCreate" }

        startHeartbeatUpdater()

        lifecycleScope.launch {
            try {
                eventRecorder.onServiceStarted()
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record service start event" }
            }
        }

        try {
            val components =
                componentsFactory.create(
                    context = this,
                    lifecycleOwner = this,
                    scope = serviceScope,
                    timeSource = timeSource,
                    overlayHealthStore = overlayHealthStore,
                    targetsRepository = targetsRepository,
                    settingsRepository = settingsRepository,
                    settingsCommand = settingsCommand,
                    suggestionsRepository = suggestionsRepository,
                    foregroundAppObserver = foregroundAppObserver,
                    suggestionEngine = suggestionEngine,
                    suggestionSelector = suggestionSelector,
                    eventRecorder = eventRecorder,
                    timelineRepository = timelineRepository,
                )

            timerOverlayController = components.timerOverlayController
            suggestionOverlayController = components.suggestionOverlayController
            miniGameOverlayController = components.miniGameOverlayController
            overlayUiController = components.overlayUiController
            overlayCoordinator = components.overlayCoordinator
            notificationController = components.notificationController
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
                            RefocusLog.e(TAG, e) {
                                "Failed to check/record permission state before stopping"
                            }
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
                    actionSelfHeal = ACTION_SELF_HEAL,
                    onStopRequested = ::stopFromUserAction,
                )

            overlayCoordinator.start()

            // 起動に成功した場合は直近エラー要約をクリアする
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    overlayHealthStore.update { it.copy(lastErrorSummary = null) }
                } catch (e: Exception) {
                    RefocusLog.w(TAG, e) { "Failed to clear lastErrorSummary" }
                }
            }
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "onCreate initialization failed" }

            // 健全性ストアに致命的エラーを記録する（best-effort）
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    overlayHealthStore.update { it.copy(lastErrorSummary = summarizeError(e)) }
                } catch (storeError: Exception) {
                    RefocusLog.w(TAG, storeError) { "Failed to write lastErrorSummary" }
                }
            }

            // 可能な範囲で後始末して停止する
            isRunning = false
            isStopping = true

            heartbeatJob?.cancel()
            heartbeatJob = null

            try {
                if (this::overlayCoordinator.isInitialized) {
                    overlayCoordinator.stop()
                }
            } catch (_: Exception) {
                // no-op
            }
            try {
                notificationDriver?.stop()
            } catch (_: Exception) {
                // no-op
            }
            try {
                removeForegroundNotification()
            } catch (_: Exception) {
                // no-op
            }

            lifecycleScope.launch { stopSelf() }
            return
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val handled = intentHandler?.handle(intent) == true

        // LifecycleService の内部処理（lifecycle dispatch）を維持する
        super.onStartCommand(intent, flags, startId)

        // STOP は sticky で復帰させない
        if (handled && intent?.action == ACTION_STOP) return START_NOT_STICKY

        // 監視サービスは常駐を前提にするため，sticky を明示する
        return START_STICKY
    }

    override fun onDestroy() {
        recordServiceStoppedBestEffort(reason = "onDestroy")

        isRunning = false
        isStopping = true

        heartbeatJob?.cancel()
        heartbeatJob = null

        // 念のため，Foreground 通知を確実に消す
        notificationDriver?.stop()
        removeForegroundNotification()

        if (this::overlayCoordinator.isInitialized) {
            overlayCoordinator.stop()
        }

        screenStateReceiver?.unregister()
        permissionSupervisor?.stop()
        runSupervisor?.stop()
        runSupervisor = null

        requestTileStateRefresh()
        QsTileStateBroadcaster.notifyExpectedRunning(this, expectedRunning = false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun recordServiceStoppedBestEffort(reason: String) {
        if (!stopEventRecorded.compareAndSet(false, true)) return

        // onDestroy をブロックしないために，独立 scope で best-effort 記録する．
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                eventRecorder.onServiceStopped()
                RefocusLog.d(TAG) { "Recorded service stop event (reason=$reason)" }
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record service stop event (reason=$reason)" }
            }
        }
    }

    private fun startHeartbeatUpdater() {
        if (heartbeatJob?.isActive == true) return

        heartbeatJob =
            serviceScope.launch(Dispatchers.IO) {
                while (isActive && !isStopping) {
                    try {
                        val nowElapsed = timeSource.elapsedRealtime()
                        val nowWall = timeSource.nowMillis()

                        overlayHealthStore.update { current ->
                            current.copy(
                                lastHeartbeatElapsedRealtimeMillis = nowElapsed,
                                lastHeartbeatWallClockMillis = nowWall,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        RefocusLog.e(TAG, e) { "Failed to write overlay heartbeat" }
                    }

                    delay(HEARTBEAT_INTERVAL_MS)
                }
            }
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
        if (!beginStopping()) return

        // stopSelf は main で呼ぶ
        lifecycleScope.launch {
            stopSelf()
        }
    }

    private fun stopFromUserAction() {
        if (!beginStopping()) return

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

    private fun beginStopping(): Boolean {
        if (isStopping) return false
        isStopping = true

        recordServiceStoppedBestEffort(reason = "beginStopping")

        QsTileStateBroadcaster.notifyExpectedRunning(this, expectedRunning = false)

        // 先に通知更新を止めてから foreground を外す．
        // stopForeground + cancel を併用して確実に消す．
        notificationDriver?.stop()
        removeForegroundNotification()
        return true
    }

    private fun removeForegroundNotification() {
        // Foreground 状態を外し，通知を取り除く．
        // NotificationManager による通常通知更新も行っているため，cancel も併用して確実に消す．
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            RefocusLog.w(TAG, e) { "Failed to stopForeground" }
        }

        if (this::notificationController.isInitialized) {
            try {
                notificationController.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                RefocusLog.w(TAG, e) { "Failed to cancel foreground notification" }
            }
        }
    }

    private fun canRunOverlay(): Boolean {
        val hasCore = PermissionHelper.hasAllCorePermissions(this)
        RefocusLog.d(TAG) { "canRunOverlay: hasCore=$hasCore" }
        return hasCore
    }

    private fun summarizeError(e: Throwable): String {
        val name = e::class.java.simpleName
        val msg = e.message
        val raw = if (msg.isNullOrBlank()) name else "$name: $msg"
        return if (raw.length <= ERROR_SUMMARY_MAX) raw else raw.take(ERROR_SUMMARY_MAX)
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

private const val OVERLAY_SERVICE_EXT_TAG = "OverlayServiceExt"

/**
 * startForegroundService が禁止される状況（Android 12+ の background start 制限など）でも，
 * 呼び出し元をクラッシュさせないための安全ラッパ．
 *
 * keep-alive など「失敗理由に応じてリトライ戦略を変えたい」呼び出し元では，
 * startOverlayService を使って例外を上に返し，その場で分類すること．
 */
fun Context.tryStartOverlayService(source: String? = null): Boolean {
    recordOverlayStartAttemptBestEffort(source)

    val intent = Intent(this, OverlayService::class.java)
    return try {
        startForegroundService(intent)
        recordOverlayStartSuccessBestEffort(source)
        true
    } catch (e: Exception) {
        recordOverlayStartFailureBestEffort(source, e)

        val suffix = if (source.isNullOrBlank()) "" else ", source=$source"
        RefocusLog.e(OVERLAY_SERVICE_EXT_TAG, e) { "Failed to start OverlayService$suffix" }
        false
    }
}

private const val START_ERROR_SUMMARY_MAX = 180

private fun Context.recordOverlayStartAttemptBestEffort(source: String?) {
    val store = getOverlayHealthStoreOrNull() ?: return
    val now = System.currentTimeMillis()

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            store.update { current ->
                current.copy(
                    lastStartAttemptWallClockMillis = now,
                    lastStartAttemptSource = source,
                )
            }
        } catch (e: Exception) {
            val suffix = if (source.isNullOrBlank()) "" else ", source=$source"
            RefocusLog.w(OVERLAY_SERVICE_EXT_TAG, e) { "Failed to record start attempt$suffix" }
        }
    }
}

private fun Context.recordOverlayStartSuccessBestEffort(source: String?) {
    val store = getOverlayHealthStoreOrNull() ?: return

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            // 成功した場合は，直近の start failure をクリアする
            store.update { current ->
                current.copy(
                    lastStartFailureWallClockMillis = null,
                    lastStartFailureSource = null,
                    lastStartFailureSummary = null,
                )
            }
        } catch (e: Exception) {
            val suffix = if (source.isNullOrBlank()) "" else ", source=$source"
            RefocusLog.w(OVERLAY_SERVICE_EXT_TAG, e) { "Failed to clear start failure$suffix" }
        }
    }
}

private fun Context.recordOverlayStartFailureBestEffort(
    source: String?,
    error: Throwable,
) {
    val store = getOverlayHealthStoreOrNull() ?: return
    val now = System.currentTimeMillis()
    val summary = summarizeStartError(error)

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            store.update { current ->
                current.copy(
                    lastStartFailureWallClockMillis = now,
                    lastStartFailureSource = source,
                    lastStartFailureSummary = summary,
                )
            }
        } catch (e: Exception) {
            val suffix = if (source.isNullOrBlank()) "" else ", source=$source"
            RefocusLog.w(OVERLAY_SERVICE_EXT_TAG, e) { "Failed to record start failure$suffix" }
        }
    }
}

private fun summarizeStartError(e: Throwable): String {
    val name = e::class.java.simpleName
    val msg = e.message
    val raw = if (msg.isNullOrBlank()) name else "$name: $msg"
    return if (raw.length <= START_ERROR_SUMMARY_MAX) raw else raw.take(START_ERROR_SUMMARY_MAX)
}

fun Context.stopOverlayService() {
    val intent = Intent(this, OverlayService::class.java)
    stopService(intent)
}

/**
 * stopService は通常例外になりにくいが，呼び出し元（Tile/Receiver 等）を安全にするためのラッパ．
 */
fun Context.tryStopOverlayService(source: String? = null): Boolean {
    val intent = Intent(this, OverlayService::class.java)
    return try {
        stopService(intent)
        true
    } catch (e: Exception) {
        val suffix = if (source.isNullOrBlank()) "" else ", source=$source"
        RefocusLog.w(OVERLAY_SERVICE_EXT_TAG, e) { "Failed to stop OverlayService$suffix" }
        false
    }
}

fun Context.requestOverlaySelfHeal() {
    val intent =
        Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SELF_HEAL
        }
    // サービスがすでに動いている前提で，foreground start ではなく startService を使う
    startService(intent)
}
