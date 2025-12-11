package com.example.refocus.domain.overlay

import com.example.refocus.core.util.TimeSource

/**
 * オーバーレイ用の「対象アプリセッション」をランタイム上だけで追跡するトラッカー。
 *
 * - Timeline / SessionProjector と同じ gracePeriodMillis を使って
 *   「どこまでを同一セッションとみなすか」を決める
 * - TimerOverlayController には computeElapsedFor() で「セッション先頭からの経過時間」を渡す
 * - SuggestionEngine には sinceForegroundMillis() で「最後に前面になってからの経過時間」も渡せる
 *
 * 永続化は行わず、あくまで Service 存続中の一時的な状態だけを扱う。
 */
class OverlaySessionTracker(
    private val timeSource: TimeSource,
) {

    private data class State(
        val packageName: String,
        var initialElapsedMillis: Long,
        var lastForegroundElapsedRealtime: Long,
        var lastLeaveAtMillis: Long?,
    )

    private val states = mutableMapOf<String, State>()

    fun clear() {
        states.clear()
    }

    /**
     * 対象アプリに入ったタイミングで呼び出す。
     *
     * @param initialElapsedIfNew
     *   まだ State が存在しない場合に「過去のセッションから引き継ぐ初期値」として使う。
     *
     * @return true の場合は「新しいセッションとして開始」,
     *         false の場合は「猶予時間以内の復帰として継続」。
     */
    fun onEnterTargetApp(
        packageName: String,
        gracePeriodMillis: Long,
        initialElapsedIfNew: Long = 0L,
    ): Boolean {
        val nowMillis = timeSource.nowMillis()
        val nowElapsed = timeSource.elapsedRealtime()

        val existing = states[packageName]
        if (existing == null) {
            // 完全な新規（ランタイム上はまだ見たことがないパッケージ）
            states[packageName] = State(
                packageName = packageName,
                initialElapsedMillis = initialElapsedIfNew.coerceAtLeast(0L),
                lastForegroundElapsedRealtime = nowElapsed,
                lastLeaveAtMillis = null,
            )
            // 「ランタイムとしては新規セッション」なので true
            return true
        }

        val lastLeave = existing.lastLeaveAtMillis
        val continues =
            lastLeave != null && (nowMillis - lastLeave) <= gracePeriodMillis

        if (!continues) {
            // 猶予時間を超えていたら、ランタイム上は新しいセッションとして扱う。
            // （ここでは initialElapsedIfNew は使わない。
            //    すでにランタイム中に積み上げた initialElapsedMillis がある前提）
            existing.initialElapsedMillis = 0L
        }

        existing.lastForegroundElapsedRealtime = nowElapsed
        existing.lastLeaveAtMillis = null

        // continues == true なら「継続セッション」なので false を返す
        return !continues
    }

    fun onLeaveTargetApp(packageName: String) {
        val state = states[packageName] ?: return
        val nowElapsed = timeSource.elapsedRealtime()
        val delta = (nowElapsed - state.lastForegroundElapsedRealtime)
            .coerceAtLeast(0L)

        state.initialElapsedMillis += delta
        state.lastLeaveAtMillis = timeSource.nowMillis()
    }

    fun computeElapsedFor(
        packageName: String,
        nowElapsedRealtime: Long,
    ): Long? {
        val state = states[packageName] ?: return null
        val delta = (nowElapsedRealtime - state.lastForegroundElapsedRealtime)
            .coerceAtLeast(0L)
        return state.initialElapsedMillis + delta
    }

    fun sinceForegroundMillis(
        packageName: String,
        nowElapsedRealtime: Long,
    ): Long? {
        val state = states[packageName] ?: return null
        return (nowElapsedRealtime - state.lastForegroundElapsedRealtime)
            .coerceAtLeast(0L)
    }
}

