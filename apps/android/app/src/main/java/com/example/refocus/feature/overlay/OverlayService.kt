package com.example.refocus.feature.overlay

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.refocus.R
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.data.RepositoryProvider
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.feature.monitor.ForegroundAppMonitor
import com.example.refocus.feature.monitor.ForegroundAppMonitorProvider
import com.example.refocus.system.permissions.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import android.content.IntentFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import com.example.refocus.core.util.TimeSource
import com.example.refocus.core.util.SystemTimeSource

class OverlayService : LifecycleService() {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    // サービス専用のCoroutineScope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val timeSource: TimeSource = SystemTimeSource()

    private lateinit var repositoryProvider: RepositoryProvider
    private val targetsRepository: TargetsRepository
        get() = repositoryProvider.targetsRepository
    private val sessionRepository: SessionRepository
        get() = repositoryProvider.sessionRepository
    private val settingsRepository: SettingsRepository
        get() = repositoryProvider.settingsRepository

    private lateinit var foregroundAppMonitor: ForegroundAppMonitor
    private lateinit var overlayController: OverlayController

    private var currentForegroundPackage: String? = null
    private var overlayPackage: String? = null
    @Volatile
    private var overlaySettings: OverlaySettings = OverlaySettings()

    private data class RunningSessionState(
        val packageName: String,
        var startedAtMillis: Long,
        var elapsedMillis: Long,
        var lastForegroundElapsedRealtime: Long?, // 前面にいたときの timeSource.elapsedRealtime()
        var pendingEndJob: Job?,                  // 猶予中のジョブ（なければ null）
        var lastLeaveAtMillis: Long?,             // 最後に前面から離れた時刻
    )

    // packageName → RunningSessionState のマップ
    private val sessionStates = mutableMapOf<String, RunningSessionState>()

    private var screenReceiverRegistered: Boolean = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "ACTION_SCREEN_OFF received")
                    handleScreenOff()
                }
                Intent.ACTION_SHUTDOWN -> {
                    Log.d(TAG, "ACTION_SHUTDOWN received")
                    handleScreenOff()
                }
                // 必要であれば将来ここで USER_PRESENT なども扱う
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "onCreate")
        val app = application as Application
        repositoryProvider = RepositoryProvider(app)
        foregroundAppMonitor = ForegroundAppMonitorProvider.get(this)
        overlayController = OverlayController(
            context = this,
            lifecycleOwner = this,
        )
        startForegroundWithNotification()
        Log.d(TAG, "startForeground done")
        serviceScope.launch {
            try {
                var first = true
                settingsRepository.observeOverlaySettings().collect { settings ->
                    overlaySettings = settings
                    withContext(Dispatchers.Main) {
                        overlayController.overlaySettings = settings
                    }
                    if (first) {
                        first = false
                        try {
                            sessionRepository.repairActiveSessionsAfterRestart(
                                gracePeriodMillis = settings.gracePeriodMillis,
                                nowMillis = timeSource.nowMillis()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "repairActiveSessionsAfterRestart failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeOverlaySettings failed", e)
            }
        }
        // 権限が揃っていなければ即終了
        if (!canRunOverlay()) {
            Log.w(TAG, "canRunOverlay = false. stopSelf()")
            stopSelf()
            return
        }
        registerScreenReceiver()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        overlayController.hideTimer()
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
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun canRunOverlay(): Boolean {
        val hasUsage = PermissionHelper.hasUsageAccess(this)
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        Log.d(TAG, "hasUsage=$hasUsage, hasOverlay=$hasOverlay")
        return hasUsage && hasOverlay
    }

    private fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        // すでに状態があればそれを使い、なければ新規作成
        val state = sessionStates.getOrPut(packageName) {
            RunningSessionState(
                packageName = packageName,
                startedAtMillis = nowMillis,
                elapsedMillis = 0L,
                lastForegroundElapsedRealtime = null,
                pendingEndJob = null,
                lastLeaveAtMillis = null
            )
        }
        val wasPaused = state.lastLeaveAtMillis != null
        // このアプリ用の猶予ジョブが動いていたらキャンセル（猶予中に戻ってきた）
        state.pendingEndJob?.cancel()
        state.pendingEndJob = null
        state.lastLeaveAtMillis = null
        // 前面に復帰したタイミングを覚えておく（leave 時に差分を足す）
        state.lastForegroundElapsedRealtime = nowElapsed
        // DB 上のセッションを開始（既に active があればそのまま返る）
        serviceScope.launch {
            try {
                sessionRepository.startSession(
                    packageName = packageName,
                    startedAtMillis = state.startedAtMillis
                )
                // 中断からの復帰であれば「再開イベント」を記録する
                if (wasPaused) {
                    try {
                        sessionRepository.recordResume(
                            packageName = packageName,
                            resumedAtMillis = nowMillis
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record resume for $packageName", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session for $packageName", e)
            }
        }
        // オーバーレイの表示対象を切り替え
        overlayPackage = packageName
        serviceScope.launch(Dispatchers.Main) {
            overlayController.showTimer(
                initialElapsedMillis = state.elapsedMillis,
                onPositionChanged = ::onOverlayPositionChanged
            )
        }
    }

    private fun onLeaveForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        val state = sessionStates[packageName] ?: return
        // 「前面にいた時間」を累積
        state.lastForegroundElapsedRealtime?.let { startElapsed ->
            val delta = nowElapsed - startElapsed
            if (delta > 0L) {
                state.elapsedMillis += delta
            }
        }
        state.lastForegroundElapsedRealtime = null
        state.lastLeaveAtMillis = nowMillis
        // ここで「中断イベント」を記録する
        serviceScope.launch {
            try {
                sessionRepository.recordPause(
                    packageName = packageName,
                    pausedAtMillis = nowMillis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record pause for $packageName", e)
            }
        }
        // もし今表示中のアプリならオーバーレイを閉じる
        if (overlayPackage == packageName) {
            overlayPackage = null
            serviceScope.launch(Dispatchers.Main) {
                overlayController.hideTimer()
            }
        }
        // このアプリに対して猶予タイマーを開始
        startGraceTimerFor(state)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startMonitoring() {
        serviceScope.launch {
            val targetsFlow = targetsRepository.observeTargets()
            val foregroundFlow = settingsRepository
                .observeOverlaySettings()
                .flatMapLatest { settings ->
                    foregroundAppMonitor.foregroundAppFlow(
                        pollingIntervalMs = settings.pollingIntervalMillis
                    )
                }
            combine(
                targetsFlow,
                foregroundFlow
            ) { targets, foregroundPackage ->
                targets to foregroundPackage
            }.collectLatest { (targets, foregroundPackage) ->
                Log.d(TAG, "combine: foreground=$foregroundPackage, targets=$targets")
                try {
                    val previous = currentForegroundPackage
                    val prevIsTarget = previous != null && previous in targets
                    val nowIsTarget = foregroundPackage != null && foregroundPackage in targets
                    val nowMillis = timeSource.nowMillis()
                    val nowElapsed = timeSource.elapsedRealtime()
                    // currentForegroundPackage を更新
                    currentForegroundPackage = foregroundPackage
                    when {
                        // 非対象 → 対象
                        !prevIsTarget && nowIsTarget -> {
                            onEnterForeground(
                                packageName = foregroundPackage,
                                nowMillis = nowMillis,
                                nowElapsed = nowElapsed
                            )
                        }
                        // 対象 → 非対象
                        prevIsTarget && !nowIsTarget -> {
                            onLeaveForeground(
                                packageName = previous,
                                nowMillis = nowMillis,
                                nowElapsed = nowElapsed
                            )
                        }
                        // 対象A → 対象B
                        prevIsTarget && previous != foregroundPackage -> {
                            onLeaveForeground(
                                packageName = previous,
                                nowMillis = nowMillis,
                                nowElapsed = nowElapsed
                            )
                            onEnterForeground(
                                packageName = foregroundPackage!!,
                                nowMillis = nowMillis,
                                nowElapsed = nowElapsed
                            )
                        }
                        else -> {
                            // 非対象→非対象 / 対象→同じ対象 (permissions など) は何もしない
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in startMonitoring loop", e)
                    // ここで落ちるとサービスごと死ぬので握りつぶす
                    withContext(Dispatchers.Main) {
                        overlayController.hideTimer()
                    }
                    overlayPackage = null
                }
            }
        }
    }


    private fun startGraceTimerFor(
        state: RunningSessionState
    ) {
        state.pendingEndJob?.cancel()
        val packageName = state.packageName
        state.pendingEndJob = serviceScope.launch {
            var ended = false
            try {
                val leaveAt = state.lastLeaveAtMillis ?: timeSource.nowMillis()
                val grace = overlaySettings.gracePeriodMillis
                val targetEndOfGrace = leaveAt + grace
                val now = timeSource.nowMillis()
                val delayMillis = (targetEndOfGrace - now).coerceAtLeast(0L)
                delay(delayMillis)
                Log.d(TAG, "Grace expired for $packageName, ending session")
                val endedAt = leaveAt.coerceAtLeast(state.startedAtMillis)
                val duration = state.elapsedMillis
                sessionRepository.endActiveSession(
                    packageName = packageName,
                    endedAtMillis = endedAt,
                    durationMillis = duration
                )
                ended = true
            } catch (_: CancellationException) {
                Log.d(TAG, "Grace canceled for $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Error in grace timer for $packageName", e)
            } finally {
                state.pendingEndJob = null
                state.lastLeaveAtMillis = null
                if (ended) {
                    sessionStates.remove(packageName)
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SHUTDOWN)
            // 後々 USER_PRESENT を扱いたくなったらここに addAction(Intent.ACTION_USER_PRESENT)
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

    /**
     * 画面OFF時に呼ばれる。
     * 対象アプリが前面かつオーバーレイ表示中なら、
     * 強制的に onLeaveForeground と同じ処理を走らせる。
     */
    private fun handleScreenOff() {
        val pkg = currentForegroundPackage
        val nowMillis = timeSource.nowMillis()
        val nowElapsed = timeSource.elapsedRealtime()

        // 現在オーバーレイを出しているアプリだけ対象にする
        if (pkg != null && pkg == overlayPackage) {
            Log.d(TAG, "handleScreenOff: treat $pkg as leave foreground due to screen off")
            onLeaveForeground(
                packageName = pkg,
                nowMillis = nowMillis,
                nowElapsed = nowElapsed
            )
            currentForegroundPackage = null
        }
    }

    private fun onOverlayPositionChanged(x: Int, y: Int) {
        serviceScope.launch {
            try {
                settingsRepository.updateOverlaySettings { current ->
                    current.copy(positionX = x, positionY = y)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save overlay position", e)
            }
        }
    }
}
