package com.example.refocus.domain.session

import android.util.Log
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 「対象アプリの前面/背面の変化」に応じて
 * - セッションの開始 / Pause / Resume / 終了
 * - 経過時間の計算
 * を管理するクラス。
 *
 * OverlayService とは独立させておくことで、
 * ロジックのテストや将来の再利用をしやすくする。
 */
class SessionManager(
    private val sessionRepository: SessionRepository,
    private val timeSource: TimeSource,
    private val scope: CoroutineScope,
    private val logTag: String = "SessionManager"
) {

    data class ActiveSessionState(
        val packageName: String,
        var initialElapsedMillis: Long,            // DB から復元した経過時間
        var lastForegroundElapsedRealtime: Long?,
        var pendingEndJob: Job?,
        var lastLeaveAtMillis: Long?,
        // 「このセッション中は提案を再表示しない」が押されたかどうか
        var suggestionDisabledForThisSession: Boolean = false,
    )

    // packageName → ActiveSessionState のマップ
    private val activeSessions = mutableMapOf<String, ActiveSessionState>()

    // SessionManager.kt

    /**
     * 前面に入ったときに呼ぶ。
     *
     * @return overlay に渡す initialElapsedMillis（オーバーレイを出さなくて良い場合は null）
     */
    suspend fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsedRealtime: Long
    ): Long? {
        // すでにメモリ上に状態がある（＝アプリ切り替えなどで戻ってきた）場合
        val existingState = activeSessions[packageName]
        if (existingState != null) {
            val wasPaused = existingState.lastLeaveAtMillis != null
            // 猶予中の終了ジョブをキャンセルし、一時離脱状態を解除
            existingState.pendingEndJob?.cancel()
            existingState.pendingEndJob = null
            existingState.lastLeaveAtMillis = null
            existingState.lastForegroundElapsedRealtime = nowElapsedRealtime
            // DB に Pause → Resume を記録（ここも suspend で素直に書く）
            if (wasPaused) {
                try {
                    sessionRepository.recordResume(
                        packageName = packageName,
                        resumedAtMillis = nowMillis
                    )
                } catch (e: Exception) {
                    Log.e(logTag, "Failed to record resume for $packageName", e)
                }
            }
            return existingState.initialElapsedMillis
        }
        // ここから「新規セッション」または「再起動直後の復元」パス
        val newState = ActiveSessionState(
            packageName = packageName,
            initialElapsedMillis = 0L,
            lastForegroundElapsedRealtime = nowElapsedRealtime,
            pendingEndJob = null,
            lastLeaveAtMillis = null,
            suggestionDisabledForThisSession = false,
        )
        activeSessions[packageName] = newState
        try {
            // まず active セッションの最後のイベント種別を取得
            val lastEventType =
                sessionRepository.getLastEventTypeForActiveSession(packageName)
            val hadActiveSession = lastEventType != null
            // 既存セッションがある場合は、そこまでの経過時間を復元
            val restoredDuration = if (hadActiveSession) {
                sessionRepository.getActiveSessionDuration(
                    packageName = packageName,
                    nowMillis = nowMillis
                )
            } else {
                null
            }
            // ここまで来た時点で DB 復元は完了している
            newState.initialElapsedMillis = restoredDuration ?: 0L
            // active セッションがなければ新規開始、
            // あれば startSession 側が既存セッションをそのまま返す想定
            sessionRepository.startSession(
                packageName = packageName,
                startedAtMillis = nowMillis
            )
            // 「最後のイベントが Pause で止まっていた」場合のみ Resume を打つ
            if (lastEventType == SessionEventType.Pause) {
                sessionRepository.recordResume(
                    packageName = packageName,
                    resumedAtMillis = nowMillis
                )
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to start/restore session for $packageName", e)
            // エラー時は initialElapsedMillis = 0 のままでもよい
        }
        return newState.initialElapsedMillis
    }

    /**
     * 前面から離れたときに呼ぶ。
     * 猶予時間経過後にセッション終了まで行う。
     */
    fun onLeaveForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsedRealtime: Long,
        gracePeriodMillis: Long
    ) {
        val state = activeSessions[packageName] ?: return
        // 「前面にいた時間」を ActiveSessionState に反映
        state.lastForegroundElapsedRealtime?.let { startElapsed ->
            val delta = nowElapsedRealtime - startElapsed
            if (delta > 0L) {
                // ここで initialElapsedMillis に累積する
                state.initialElapsedMillis += delta
            }
        }
        state.lastForegroundElapsedRealtime = null
        state.lastLeaveAtMillis = nowMillis
        // 中断イベント
        scope.launch {
            try {
                sessionRepository.recordPause(
                    packageName = packageName,
                    pausedAtMillis = nowMillis
                )
            } catch (e: Exception) {
                Log.e(logTag, "Failed to record pause for $packageName", e)
            }
        }
        // 猶予タイマー開始
        startGraceTimerFor(state, gracePeriodMillis)
    }

    private fun startGraceTimerFor(
        state: ActiveSessionState,
        gracePeriodMillis: Long
    ) {
        state.pendingEndJob?.cancel()
        val packageName = state.packageName
        state.pendingEndJob = scope.launch {
            var ended = false
            try {
                val leaveAt = state.lastLeaveAtMillis ?: timeSource.nowMillis()
                val grace = gracePeriodMillis
                val targetEndOfGrace = leaveAt + grace
                val now = timeSource.nowMillis()
                val delayMillis = (targetEndOfGrace - now).coerceAtLeast(0L)
                delay(delayMillis)
                Log.d(logTag, "GraceTime expired for $packageName, ending session")
                // 終了時刻は「離脱した瞬間」で OK
                val endedAt = leaveAt
                sessionRepository.endActiveSession(
                    packageName = packageName,
                    endedAtMillis = endedAt
                )
                ended = true
            } catch (_: CancellationException) {
                Log.d(logTag, "GraceTime canceled for $packageName")
            } catch (e: Exception) {
                Log.e(logTag, "Error in grace timer for $packageName", e)
            } finally {
                state.pendingEndJob = null
                state.lastLeaveAtMillis = null
                if (ended) {
                    activeSessions.remove(packageName)
                }
            }
        }
    }

    /**
     * 「今この瞬間の連続使用時間」を計算。
     */
    fun computeElapsedFor(
        packageName: String,
        nowElapsedRealtime: Long
    ): Long? {
        val state = activeSessions[packageName] ?: return null
        val base = state.initialElapsedMillis
        val lastStart = state.lastForegroundElapsedRealtime
        return if (lastStart != null) {
            base + (nowElapsedRealtime - lastStart).coerceAtLeast(0L)
        } else {
            base
        }
    }

    /**
     * 前面に戻ってからの経過時間（安定時間判定用）。
     */
    fun sinceForegroundMillis(
        packageName: String,
        nowElapsedRealtime: Long
    ): Long {
        val state = activeSessions[packageName] ?: return 0L
        val lastStart = state.lastForegroundElapsedRealtime ?: return 0L
        return (nowElapsedRealtime - lastStart).coerceAtLeast(0L)
    }

    fun markSuggestionDisabledForThisSession(packageName: String) {
        val state = activeSessions[packageName] ?: return
        state.suggestionDisabledForThisSession = true
    }

    fun isSuggestionDisabledForThisSession(packageName: String): Boolean {
        return activeSessions[packageName]?.suggestionDisabledForThisSession == true
    }

    /**
     * Service の onDestroy などで呼び出すクリーンアップ。
     */
    fun clear() {
        activeSessions.values.forEach { state ->
            state.pendingEndJob?.cancel()
        }
        activeSessions.clear()
    }
}
