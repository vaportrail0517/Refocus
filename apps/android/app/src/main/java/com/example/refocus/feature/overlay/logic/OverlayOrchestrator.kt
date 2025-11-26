package com.example.refocus.feature.overlay.logic

import android.util.Log
import com.example.refocus.core.model.OverlaySuggestionMode
import com.example.refocus.core.model.Settings
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.domain.session.SessionManager
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.feature.overlay.controller.SuggestionOverlayController
import com.example.refocus.feature.overlay.controller.TimerOverlayController
import com.example.refocus.system.monitor.ForegroundAppMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OverlayService から「ロジック部分」を切り出したオーケストレータ。
 *
 * - 前面アプリの監視
 * - SessionManager とのやりとり
 * - TimerOverlay / SuggestionOverlay の表示制御
 * を担当する。
 *
 * Android Service / Notification / BroadcastReceiver などの OS 依存部分は
 * OverlayService 側に残す。
 */
class OverlayOrchestrator(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val targetsRepository: TargetsRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val suggestionsRepository: SuggestionsRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val suggestionEngine: SuggestionEngine,
    private val timerOverlayController: TimerOverlayController,
    private val suggestionOverlayController: SuggestionOverlayController,
    private val sessionManager: SessionManager,
) {

    companion object {
        private const val TAG = "OverlayOrchestrator"
    }

    @Volatile
    private var currentForegroundPackage: String? = null

    @Volatile
    private var overlayPackage: String? = null

    @Volatile
    private var overlaySettings: Settings = Settings()

    @Volatile
    private var suggestionSnoozedUntilMillis: Long? = null

    @Volatile
    private var isSuggestionOverlayShown: Boolean = false

    // SettingsDataStore からの設定 Flow を shareIn して共有する
    private val settingsFlow: SharedFlow<Settings> =
        settingsRepository.observeOverlaySettings()
            .shareIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                replay = 1
            )
    private val screenOnState = MutableStateFlow(true)

    val screenOnFlow: StateFlow<Boolean> get() = screenOnState

    /**
     * 外側から「画面ON/OFF」を伝えるためのメソッド。
     * BroadcastReceiver 側から呼ばれる。
     */
    fun setScreenOn(isOn: Boolean) {
        screenOnState.value = isOn
    }

    /**
     * 画面OFF時に呼び出す。前面にいた対象アプリを「離脱」として扱う。
     */
    fun onScreenOff() {
        val pkg = currentForegroundPackage ?: return
        val nowMillis = timeSource.nowMillis()
        val nowElapsed = timeSource.elapsedRealtime()
        Log.d(TAG, "onScreenOff: treat $pkg as leave foreground due to screen off")
        onLeaveForeground(
            packageName = pkg,
            nowMillis = nowMillis,
            nowElapsed = nowElapsed
        )
        currentForegroundPackage = null
    }

    /**
     * 監視と設定購読を開始。
     * OverlayService.onCreate から呼ばれる想定。
     */
    fun start() {
        observeOverlaySettings()
        startMonitoringForeground()
    }

    /**
     * Service 破棄時に呼ぶ。
     * SessionManager / Overlay を片付ける。
     */
    fun stop() {
        sessionManager.clear()
        timerOverlayController.hideTimer()
        suggestionOverlayController.hideSuggestionOverlay()
        clearSuggestionOverlayState()
        // scope 自体のキャンセルは Service 側で行う
    }

    private fun observeOverlaySettings() {
        scope.launch {
            try {
                var first = true
                settingsFlow.collect { settings ->
                    overlaySettings = settings
                    withContext(Dispatchers.Main) {
                        timerOverlayController.overlaySettings = settings
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
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startMonitoringForeground() {
        scope.launch {
            val targetsFlow = targetsRepository.observeTargets()
            val foregroundFlow = settingsFlow
                .flatMapLatest { settings ->
                    foregroundAppMonitor.foregroundAppFlow(
                        pollingIntervalMs = settings.pollingIntervalMillis
                    )
                }
            val screenOnFlow = screenOnState

            combine(
                targetsFlow,
                foregroundFlow,
                screenOnFlow
            ) { targets, foregroundRaw, isScreenOn ->
                // ここではまだ foregroundRaw を捨てないで一緒に返す
                Triple(targets, foregroundRaw, isScreenOn)
            }.collectLatest { (targets, foregroundRaw, isScreenOn) ->
                // 画面OFF中は「foreground なし」とみなす
                val foregroundPackage = if (isScreenOn) foregroundRaw else null
                Log.d(
                    TAG,
                    "combine: raw=$foregroundRaw, screenOn=$isScreenOn, effective=$foregroundPackage, targets=$targets"
                )
                try {
                    val previous = currentForegroundPackage
                    val prevIsTarget = previous != null && previous in targets
                    val nowIsTarget = foregroundPackage != null && foregroundPackage in targets
                    val nowMillis = timeSource.nowMillis()
                    val nowElapsed = timeSource.elapsedRealtime()

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
                        timerOverlayController.hideTimer()
                        suggestionOverlayController.hideSuggestionOverlay()
                    }
                    overlayPackage = null
                    clearSuggestionOverlayState()
                }
            }
        }
    }

    private suspend fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        // SessionManager に「前面に入った」ことを通知し、初期経過時間を取得
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
            ) ?: initialElapsed
        }

        withContext(Dispatchers.Main) {
            timerOverlayController.showTimer(
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
            scope.launch(Dispatchers.Main) {
                timerOverlayController.hideTimer()
                suggestionOverlayController.hideSuggestionOverlay()
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

    private fun onOverlayPositionChanged(x: Int, y: Int) {
        scope.launch {
            try {
                settingsRepository.updateOverlaySettings { current ->
                    current.copy(positionX = x, positionY = y)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save overlay position", e)
            }
        }
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
            disabledForThisSession = sessionManager.isSuggestionDisabledForThisSession(
                packageName
            ),
        )

        if (!suggestionEngine.shouldShow(input)) {
            return
        }

        scope.launch {
            try {
                val suggestion = suggestionsRepository.observeSuggestion().firstOrNull()
                val hasSuggestion = suggestion != null

                if (!hasSuggestion && !overlaySettings.restSuggestionEnabled) {
                    Log.d(TAG, "No suggestion and restSuggestion disabled, skip overlay")
                    return@launch
                }

                val (title, mode) = if (suggestion != null) {
                    suggestion.title to OverlaySuggestionMode.Goal
                } else {
                    "画面から少し離れて休憩する" to OverlaySuggestionMode.Rest
                }

                withContext(Dispatchers.Main) {
                    isSuggestionOverlayShown = true
                    suggestionOverlayController.showSuggestionOverlay(
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