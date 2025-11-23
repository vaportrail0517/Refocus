package com.example.refocus.feature.overlay

import android.annotation.SuppressLint
import android.app.Application
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
import com.example.refocus.core.model.Settings
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.RepositoryProvider
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.domain.session.SessionManager
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.system.monitor.ForegroundAppMonitor
import com.example.refocus.system.monitor.ForegroundAppMonitorProvider
import com.example.refocus.system.permissions.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private lateinit var sessionManager: SessionManager
    private val suggestionEngine = SuggestionEngine()

    private var currentForegroundPackage: String? = null
    private var overlayPackage: String? = null

    @Volatile
    private var overlaySettings: Settings = Settings()

    @Volatile
    private var suggestionSnoozedUntilMillis: Long? = null

    @Volatile
    private var isSuggestionOverlayShown: Boolean = false

    @Volatile
    private var isScreenOn: Boolean = true

    private var screenReceiverRegistered: Boolean = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SHUTDOWN -> {
                    Log.d(TAG, "ACTION_SCREEN_OFF / SHUTDOWN received")
                    // 画面OFFとみなす
                    isScreenOn = false
                    // 現在の対象アプリを「前面離脱」として扱う
                    handleScreenOff()
                }

                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "ACTION_USER_PRESENT / SCREEN_ON received")
                    // ★ 画面ONに戻ったことを記録
                    isScreenOn = true
                }
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
        sessionManager = SessionManager(
            sessionRepository = sessionRepository,
            timeSource = timeSource,
            scope = serviceScope,
            logTag = TAG
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
        sessionManager.clear()
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

    private suspend fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        // ここで DB 復元込みの初期状態を作る（値は返り値として使わないが、
        //   ActiveSessionState.initialElapsedMillis がここで確定する）
        val initialElapsed = sessionManager.onEnterForeground(
            packageName = packageName,
            nowMillis = nowMillis,
            nowElapsedRealtime = nowElapsed
        ) ?: return
        overlayPackage = packageName
        // SessionManager + packageName を閉じ込めた provider
        val elapsedProvider: (Long) -> Long = { nowElapsedRealtime ->
            sessionManager.computeElapsedFor(
                packageName = packageName,
                nowElapsedRealtime = nowElapsedRealtime
            ) ?: initialElapsed // 万一 null になっても初期値でフォロー
        }
        withContext(Dispatchers.Main) {
            overlayController.showTimer(
                elapsedMillisProvider = elapsedProvider,
                onPositionChanged = ::onOverlayPositionChanged
            )
        }
    }

    private fun onLeaveForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        // 先にオーバーレイを閉じる
        if (overlayPackage == packageName) {
            overlayPackage = null
            serviceScope.launch(Dispatchers.Main) {
                overlayController.hideTimer()
                overlayController.hideSuggestionOverlay()
                clearSuggestionOverlayState()
            }
        }
        // セッション管理は SessionManager に委譲
        val grace = overlaySettings.gracePeriodMillis
        sessionManager.onLeaveForeground(
            packageName = packageName,
            nowMillis = nowMillis,
            nowElapsedRealtime = nowElapsed,
            gracePeriodMillis = grace
        )
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
            ) { targets, foregroundRaw ->
                // 画面OFF中は「foreground なし」とみなす
                val foregroundEffective = if (isScreenOn) foregroundRaw else null
                targets to foregroundEffective
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
                                packageName = foregroundPackage!!,
                                nowMillis = nowMillis,
                                nowElapsed = nowElapsed
                            )
                        }
                        // 対象 → 非対象
                        prevIsTarget && !nowIsTarget -> {
                            onLeaveForeground(
                                packageName = previous!!,
                                nowMillis = nowMillis,
                                nowElapsed = nowElapsed
                            )
                        }
                        // 対象A → 対象B
                        prevIsTarget && previous != foregroundPackage -> {
                            onLeaveForeground(
                                packageName = previous!!,
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
                            // 非対象→非対象 / 対象→同じ対象 は何もしない
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
                    withContext(Dispatchers.Main) {
                        overlayController.hideTimer()
                    }
                    overlayPackage = null
                }
            }
        }
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

    /**
     * 画面OFF時に呼ばれる。overlay の有無に関係なく、
     * 強制的に onLeaveForeground と同じ処理を走らせる。
     */
    private fun handleScreenOff() {
        val pkg = currentForegroundPackage ?: return
        val nowMillis = timeSource.nowMillis()
        val nowElapsed = timeSource.elapsedRealtime()
        Log.d(TAG, "handleScreenOff: treat $pkg as leave foreground due to screen off")
        // とにかく今前面と認識している対象を leave させる
        onLeaveForeground(
            packageName = pkg,
            nowMillis = nowMillis,
            nowElapsed = nowElapsed
        )
        // 「いま foreground にいるアプリ」はいないことにする
        currentForegroundPackage = null
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

    private fun suggestionTriggerThresholdMillis(settings: Settings): Long {
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

    private fun suggestionForegroundStableThresholdMillis(settings: Settings): Long {
        val seconds = settings.suggestionForegroundStableSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionTimeoutMillis(settings: Settings): Long {
        val seconds = settings.suggestionTimeoutSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionCooldownMillis(settings: Settings): Long {
        val seconds = settings.suggestionCooldownSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionInteractionLockoutMillis(settings: Settings): Long {
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
        sessionManager.markSuggestionDisabledForThisSession(packageName)
        Log.d(TAG, "Suggestion disabled for this session: $packageName")
    }


    private fun maybeShowSuggestionIfNeeded(
        packageName: String,
        nowMillis: Long,
        nowElapsedRealtime: Long
    ) {
        val elapsed = sessionManager.computeElapsedFor(
            packageName = packageName,
            nowElapsedRealtime = nowElapsedRealtime
        ) ?: return
        val sinceForegroundMs = sessionManager.sinceForegroundMillis(
            packageName = packageName,
            nowElapsedRealtime = nowElapsedRealtime
        )
        val input = SuggestionEngine.Input(
            elapsedMillis = elapsed,
            sinceForegroundMillis = sinceForegroundMs,
            settings = overlaySettings,
            nowMillis = nowMillis,
            snoozedUntilMillis = suggestionSnoozedUntilMillis,
            isOverlayShown = isSuggestionOverlayShown,
            disabledForThisSession = sessionManager.isSuggestionDisabledForThisSession(packageName),
        )
        if (!suggestionEngine.shouldShow(input)) {
            return
        }
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
                // モードとタイトルを決定
                val (title, mode) = if (suggestion != null) {
                    suggestion.title to OverlaySuggestionMode.Goal
                } else {
                    "画面から少し離れて休憩する" to OverlaySuggestionMode.Rest
                }
                withContext(Dispatchers.Main) {
                    isSuggestionOverlayShown = true
                    overlayController.showSuggestionOverlay(
                        title = title,
                        mode = mode,
                        autoDismissMillis = suggestionTimeoutMillis(overlaySettings),
                        interactionLockoutMillis = suggestionInteractionLockoutMillis(
                            overlaySettings
                        ),
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
