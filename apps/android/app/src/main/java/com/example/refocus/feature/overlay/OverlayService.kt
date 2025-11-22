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
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import androidx.lifecycle.LifecycleService
import com.example.refocus.R
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.data.RepositoryProvider
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.system.monitor.ForegroundAppMonitor
import com.example.refocus.system.monitor.ForegroundAppMonitorProvider
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
import com.example.refocus.core.model.SessionEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import com.example.refocus.core.util.TimeSource
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.data.repository.SuggestionsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first

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
    private val suggestionsRepository: SuggestionsRepository
        get() = repositoryProvider.suggestionsRepository

    private lateinit var foregroundAppMonitor: ForegroundAppMonitor
    private lateinit var overlayController: OverlayController

    private var currentForegroundPackage: String? = null
    private var overlayPackage: String? = null
    @Volatile
    private var overlaySettings: OverlaySettings = OverlaySettings()

    @Volatile
    private var suggestionSnoozedUntilMillis: Long? = null

    @Volatile
    private var suggestionDisabledForYmd: Int? = null

    @Volatile
    private var lastSuggestionShownAtMillis: Long? = null

    @Volatile
    private var isSuggestionOverlayShown: Boolean = false


    private data class RunningSessionState(
        val packageName: String,
        var initialElapsedMillis: Long,            // DB から復元した経過時間
        var lastForegroundElapsedRealtime: Long?,
        var pendingEndJob: Job?,
        var lastLeaveAtMillis: Long?,
        // 「このセッション中は表示しない」が押されたかどうか
        var suggestionDisabledForThisSession: Boolean = false,
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
        overlayController.hideSuggestionOverlay()
        clearSuggestionOverlayState()
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

    private fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        // すでにメモリ上に状態がある（＝アプリ切り替えなどで戻ってきた）場合
        val existingState = sessionStates[packageName]
        if (existingState != null) {
            val wasPaused = existingState.lastLeaveAtMillis != null
            // 猶予中の終了ジョブをキャンセルし、前面復帰扱いに戻す
            existingState.pendingEndJob?.cancel()
            existingState.pendingEndJob = null
            existingState.lastLeaveAtMillis = null
            existingState.lastForegroundElapsedRealtime = nowElapsed
            // DB に Pause → Resume イベントを記録
            if (wasPaused) {
                serviceScope.launch {
                    try {
                        sessionRepository.recordResume(
                            packageName = packageName,
                            resumedAtMillis = nowMillis
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record resume for $packageName", e)
                    }
                }
            }
            // オーバーレイ表示を再開
            overlayPackage = packageName
            serviceScope.launch(Dispatchers.Main) {
                overlayController.showTimer(
                    initialElapsedMillis = existingState.initialElapsedMillis,
                    onPositionChanged = ::onOverlayPositionChanged
                )
            }
            return
        }
        // ここから「新規セッション」または「再起動直後の復元」パス
        val newState = RunningSessionState(
            packageName = packageName,
            initialElapsedMillis = 0L,
            lastForegroundElapsedRealtime = nowElapsed,
            pendingEndJob = null,
            lastLeaveAtMillis = null,
            suggestionDisabledForThisSession = false,
        )
        sessionStates[packageName] = newState
        serviceScope.launch {
            try {
                // まず active セッションの最後のイベント種別を取得
                val lastEventType = sessionRepository.getLastEventTypeForActiveSession(packageName)
                val hadActiveSession = lastEventType != null
                // active セッションがある場合のみ、DB から経過時間を復元
                val restoredDuration = if (hadActiveSession) {
                    sessionRepository.getActiveSessionDuration(
                        packageName = packageName,
                        nowMillis = nowMillis
                    )
                } else {
                    null
                }
                newState.initialElapsedMillis = restoredDuration ?: 0L
                // active セッションがなければ新規開始、
                // あれば startSession 側が既存セッションをそのまま返す想定
                sessionRepository.startSession(
                    packageName = packageName,
                    startedAtMillis = nowMillis
                )
                // 「最後のイベントが Pause で止まっていた」場合のみ Resume を打つ
                if (lastEventType == SessionEventType.Pause) {
                    try {
                        sessionRepository.recordResume(
                            packageName = packageName,
                            resumedAtMillis = nowMillis
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record resume(after restore) for $packageName", e)
                    }
                }
                overlayPackage = packageName
                withContext(Dispatchers.Main) {
                    overlayController.showTimer(
                        initialElapsedMillis = newState.initialElapsedMillis,
                        onPositionChanged = ::onOverlayPositionChanged
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start/restore session for $packageName", e)
            }
        }
    }

    private fun onLeaveForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        val state = sessionStates[packageName] ?: return
        // 「前面にいた時間」を RunningSessionState に反映
        state.lastForegroundElapsedRealtime?.let { startElapsed ->
            val delta = nowElapsed - startElapsed
            if (delta > 0L) {
                // ここで initialElapsedMillis に累積する
                state.initialElapsedMillis += delta
            }
        }
        state.lastForegroundElapsedRealtime = null
        state.lastLeaveAtMillis = nowMillis
        // 中断イベント
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
        // オーバーレイ停止
        if (overlayPackage == packageName) {
            overlayPackage = null
            serviceScope.launch(Dispatchers.Main) {
                overlayController.hideTimer()
                overlayController.hideSuggestionOverlay()
                clearSuggestionOverlayState()
            }
        }
        // 猶予タイマー開始
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
                    if (nowIsTarget && foregroundPackage != null) {
                        maybeShowSuggestionIfNeeded(
                            packageName = foregroundPackage,
                            nowMillis = nowMillis,
                            nowElapsedRealtime = nowElapsed
                        )
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
                // 終了時刻は「離脱した瞬間」で OK（startedAtMillis との大小比較も不要）
                val endedAt = leaveAt
                sessionRepository.endActiveSession(
                    packageName = packageName,
                    endedAtMillis = endedAt
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

    private fun currentYmd(nowMillis: Long = timeSource.nowMillis()): Int {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return sdf.format(Date(nowMillis)).toInt()
    }

    // RunningSessionState から「今この瞬間の連続使用時間」を計算
    private fun computeElapsedForState(
        state: RunningSessionState,
        nowElapsedRealtime: Long
    ): Long {
        val base = state.initialElapsedMillis
        val lastStart = state.lastForegroundElapsedRealtime
        return if (lastStart != null) {
            base + (nowElapsedRealtime - lastStart).coerceAtLeast(0L)
        } else {
            base
        }
    }

    private fun suggestionTriggerThresholdMillis(settings: OverlaySettings): Long {
        if (!settings.suggestionEnabled) {
            // 提案オフ
            return Long.MAX_VALUE
        }
        val seconds = settings.suggestionTriggerSeconds
        if (seconds <= 0) {
            return Long.MAX_VALUE
        }
        return seconds.toLong() * 1_000L
    }

    private fun suggestionForegroundStableThresholdMillis(settings: OverlaySettings): Long {
        val seconds = settings.suggestionForegroundStableSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionTimeoutMillis(settings: OverlaySettings): Long {
        val seconds = settings.suggestionTimeoutSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionCooldownMillis(settings: OverlaySettings): Long {
        val seconds = settings.suggestionCooldownSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionInteractionLockoutMillis(settings: OverlaySettings): Long {
        // 念のため 0 未満にならないよう補正
        return settings.suggestionInteractionLockoutMillis.coerceAtLeast(0L)
    }

    private fun handleSuggestionSnooze() {
        clearSuggestionOverlayState()
        val now = timeSource.nowMillis()
        val cooldownMs = suggestionCooldownMillis(overlaySettings)
        suggestionSnoozedUntilMillis = now + cooldownMs
        Log.d(TAG, "Suggestion snoozed until $suggestionSnoozedUntilMillis")
    }
    private fun handleSuggestionSnoozeLater() {
        handleSuggestionSnooze()
    }

    private fun handleSuggestionDismissOnly() {
        // スワイプやタイムアウトから来る。内部的には同じスヌーズロジック。
        handleSuggestionSnooze()
    }

    private fun handleSuggestionDisableThisSession() {
        clearSuggestionOverlayState()
        val packageName = overlayPackage ?: return
        val state = sessionStates[packageName] ?: return
        state.suggestionDisabledForThisSession = true
        Log.d(TAG, "Suggestion disabled for this session: $packageName")
    }


    private fun maybeShowSuggestionIfNeeded(
        packageName: String,
        nowMillis: Long,
        nowElapsedRealtime: Long
    ) {
        // 提案自体が OFF なら何もしない
        if (!overlaySettings.suggestionEnabled) return
        val state = sessionStates[packageName] ?: return
        // すでにカードを表示中なら新しいカードは出さない
        if (isSuggestionOverlayShown) return
        // このセッションではもう出さない設定なら終了
        if (state.suggestionDisabledForThisSession) return
        // グローバルクールダウン中なら終了
        val snoozedUntil = suggestionSnoozedUntilMillis
        if (snoozedUntil != null && nowMillis < snoozedUntil) return
        // セッション全体での連続利用時間を算出
        val elapsed = computeElapsedForState(state, nowElapsedRealtime)
        val triggerMs = suggestionTriggerThresholdMillis(overlaySettings)
        if (triggerMs == Long.MAX_VALUE) return
        if (elapsed < triggerMs) return
        // 前面安定時間（復帰直後でないこと）チェック
        val lastFg = state.lastForegroundElapsedRealtime
        val sinceForegroundMs = if (lastFg != null) {
            (nowElapsedRealtime - lastFg).coerceAtLeast(0L)
        } else {
            0L
        }
        val stableMs = suggestionForegroundStableThresholdMillis(overlaySettings)
        if (sinceForegroundMs < stableMs) return
        // ここまで来たら「何かしら提案してよい条件」は満たしている
        serviceScope.launch {
            try {
                val suggestion = suggestionsRepository.observeSuggestion().first()
                val hasSuggestion = suggestion != null
                // やりたいこともなく、休憩提案も OFF の場合は何も表示しない
                if (!hasSuggestion && !overlaySettings.restSuggestionEnabled) {
                    Log.d(TAG, "No suggestion and restSuggestion disabled, skip overlay")
                    return@launch
                }
                // 表示タイトルを決める
                val title = suggestion?.title ?: "少し休憩しませんか？"
                withContext(Dispatchers.Main) {
                    lastSuggestionShownAtMillis = nowMillis
                    isSuggestionOverlayShown = true
                    overlayController.showSuggestionOverlay(
                        title = title,
                        autoDismissMillis = suggestionTimeoutMillis(overlaySettings),
                        interactionLockoutMillis = suggestionInteractionLockoutMillis(overlaySettings),
                        onSnoozeLater = { handleSuggestionSnoozeLater() },
                        onDisableThisSession = { handleSuggestionDisableThisSession() },
                        onDismissOnly = { handleSuggestionDismissOnly() },
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show suggestion overlay for $packageName", e)
                isSuggestionOverlayShown = false
            }
        }
    }

    private fun clearSuggestionOverlayState() {
        isSuggestionOverlayShown = false
        // suggestionSnoozedUntilMillis はここでは触らない
        // （スヌーズ状態は handleSuggestionSnooze 系で管理）
    }
}
