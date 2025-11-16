package com.example.refocus.feature.overlay

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.refocus.R
import com.example.refocus.data.RepositoryProvider
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.feature.monitor.ForegroundAppMonitor
import com.example.refocus.permissions.PermissionHelper
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

class OverlayService : LifecycleService() {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1

        // 前面アプリのポーリング間隔（将来設定に出す）
        private const val DEFAULT_POLLING_INTERVAL_MS = 500L
        // セッションを分割するかどうかの猶予（将来設定に出す）
        private const val DEFAULT_GRACE_PERIOD_MS = 30_000L
    }

    // サービス専用のCoroutineScope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var targetsRepository: TargetsRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var foregroundAppMonitor: ForegroundAppMonitor
    private lateinit var overlayController: OverlayController

    private var currentForegroundPackage: String? = null
    private var isTimerVisible: Boolean = false

    private var pendingEndPackage: String? = null
    private var pendingEndJob: Job? = null
    private var lastLeaveAtMillis: Long? = null

    private var currentSessionStartedAtMillis: Long? = null
    private var currentSessionElapsedMillis: Long = 0L
    private var currentSessionStartElapsedRealtime: Long? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        val app = application as Application

        val repositoryProvider = RepositoryProvider(app)
        targetsRepository = repositoryProvider.targetsRepository
        sessionRepository = repositoryProvider.sessionRepository

        foregroundAppMonitor = ForegroundAppMonitor(this)

        // LifecycleService 自身を LifecycleOwner として渡す
        overlayController = OverlayController(
            context = this,
            lifecycleOwner = this,
        )

        startForegroundWithNotification()
        Log.d(TAG, "startForeground done")

        serviceScope.launch {
            try {
                sessionRepository.repairStaleSessions()
            } catch (e: Exception) {
                Log.e(TAG, "repairStaleSessions failed", e)
            }
        }

        // 権限が揃っていなければ即終了
        if (!canRunOverlay()) {
            Log.w(TAG, "canRunOverlay = false. stopSelf()")
            stopSelf()
            return
        }

        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayController.hideTimer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        // バインドは使わない
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

    /**
     * 前面アプリと対象アプリ集合の変化から導かれる「オーバーレイに対する指示」。
     * M3 でこのイベントに応じてセッションの開始/終了も処理できるようにする。
     */
    private enum class OverlayEvent {
        ShowTimer,
        HideTimer,
        NoChange
    }

    private data class OverlayTransition(
        val event: OverlayEvent,
        val previousPackage: String?,
        val newPackage: String?
    )

    private fun reduceForegroundChange(
        newForegroundPackage: String?,
        targets: Set<String>
    ): OverlayTransition {
        val previous = currentForegroundPackage
        val wasTarget = previous != null && previous in targets
        val isTargetNow = newForegroundPackage != null && newForegroundPackage in targets

        currentForegroundPackage = newForegroundPackage

        val event = when {
            !wasTarget && isTargetNow -> OverlayEvent.ShowTimer
            wasTarget && !isTargetNow -> OverlayEvent.HideTimer
            else -> OverlayEvent.NoChange
        }
        return OverlayTransition(event, previousPackage = previous, newPackage = newForegroundPackage)
    }

    private fun startMonitoring() {
        serviceScope.launch {
            combine(
                foregroundAppMonitor.foregroundAppFlow(pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS),
                targetsRepository.observeTargets()
            ) { foregroundPackage, targets ->
                foregroundPackage to targets
            }.collectLatest { (foregroundPackage, targets) ->
                Log.d(TAG, "combine: foreground=$foregroundPackage, targets=$targets")
                try {
                    val transition = reduceForegroundChange(foregroundPackage, targets)
                    when (transition.event) {
                        OverlayEvent.ShowTimer -> {
                            val nowElapsed = SystemClock.elapsedRealtime()
                            val nowMillis = System.currentTimeMillis()
                            val pkg = currentForegroundPackage
                            Log.d(TAG, "showTimer nowElapsed=$nowElapsed, nowMillis=$nowMillis")
                            if (pkg != null) {
                                if (pendingEndPackage == pkg) {
                                    Log.d(TAG, "Resumed $pkg within grace period, keep session")
                                    cancelGracePeriodIfAny()
                                    if (currentSessionStartedAtMillis == null) {
                                        currentSessionStartedAtMillis = nowMillis
                                    }
                                } else {
                                    Log.d(TAG, "Start new session for $pkg")
                                    currentSessionStartedAtMillis = nowMillis
                                    currentSessionElapsedMillis = 0L
                                    serviceScope.launch {
                                        try {
                                            sessionRepository.startSession(
                                                packageName = pkg,
                                                startedAtMillis = nowMillis
                                            )
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to start session for $pkg", e)
                                        }
                                    }
                                }
                            }
                            currentSessionStartElapsedRealtime = nowElapsed
                            withContext(Dispatchers.Main) {
                                overlayController.showTimer(initialElapsedMillis = currentSessionElapsedMillis)
                            }
                            isTimerVisible = true
                        }
                        OverlayEvent.HideTimer -> {
                            val nowMillis = System.currentTimeMillis()
                            val nowElapsed = SystemClock.elapsedRealtime()
                            val leftPackage = transition.previousPackage
                            Log.d(TAG, "hideTimer by event, nowMillis=$nowMillis, leftPackage=$leftPackage")
                            // 「最後に前面になった瞬間」からの差分を累積時間に足す
                            currentSessionStartElapsedRealtime?.let { startElapsed ->
                                val delta = nowElapsed - startElapsed
                                if (delta > 0L) {
                                    currentSessionElapsedMillis += delta
                                }
                            }
                            currentSessionStartElapsedRealtime = null
                            withContext(Dispatchers.Main) {
                                overlayController.hideTimer()
                            }
                            isTimerVisible = false
                            if (leftPackage != null) {
                                startGracePeriod(
                                    packageName = leftPackage,
                                    leaveAtMillis = nowMillis
                                )
                            }
                        }
                        OverlayEvent.NoChange -> {
                            // 何もしない（ログも不要なら省略可）
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in startMonitoring loop", e)
                    // ここで落ちるとサービスごと死ぬので握りつぶす
                    withContext(Dispatchers.Main) {
                        overlayController.hideTimer()
                    }
                    isTimerVisible = false
                }
            }
        }
    }

    /**
     * 現在の対象アプリを「t ミリ秒後にセッション終了する」という猶予状態にする。
     * 猶予中に再度 ShowTimer が来た場合はキャンセルされる。
     */
    private fun startGracePeriod(
        packageName: String,
        leaveAtMillis: Long
    ) {
        // 既存の猶予があればキャンセル
        pendingEndJob?.cancel()
        pendingEndPackage = packageName
        lastLeaveAtMillis = leaveAtMillis
        pendingEndJob = serviceScope.launch {
            try {
                delay(DEFAULT_GRACE_PERIOD_MS)
                // 猶予中に同じアプリへ戻ってきた場合は cancelGracePeriodIfAny() で
                // pendingEndPackage が null にされているはず
                if (pendingEndPackage == null) {
                    Log.d(TAG, "Grace period job: already canceled for $packageName")
                    return@launch
                }
                // packageName が変わっていても、とりあえず endActiveSession は冪等なので呼んでしまってよい
                Log.d(TAG, "Grace period expired for $packageName, ending session")
                val startedAt = currentSessionStartedAtMillis
                val effectiveEndMillis = if (startedAt != null) {
                    // 「開始時刻 + 画面表示時間の合計」で終了時刻を算出
                    startedAt + currentSessionElapsedMillis
                } else {
                    // 保険：開始時刻が取れないときは従来通り
                    leaveAtMillis
                }
                sessionRepository.endActiveSession(
                    packageName = packageName,
                    endedAtMillis = effectiveEndMillis
                )
                currentSessionStartedAtMillis = null
                currentSessionElapsedMillis = 0L
                currentSessionStartElapsedRealtime = null
            } catch (e: CancellationException) {
                Log.d(TAG, "Grace period canceled for $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Error in grace period job", e)
            } finally {
                // 状態リセットは finally で必ず実行する
                pendingEndJob = null
                pendingEndPackage = null
                lastLeaveAtMillis = null
            }
        }
    }


    /**
     * 猶予状態を解除する（ユーザが猶予時間内に同じアプリへ戻ってきたなど）。
     */
    private fun cancelGracePeriodIfAny() {
        pendingEndJob?.cancel()
        pendingEndJob = null
        pendingEndPackage = null
        lastLeaveAtMillis = null
    }
}
