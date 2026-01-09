package com.example.refocus.domain.overlay.orchestration

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
        // 論理セッションの累積（前回までに積み上がった分）
        var accumulatedElapsedMillis: Long,
        // 「いま前面にいる区間」の開始点（elapsedRealtime）
        var activeStartElapsedRealtime: Long,
        // 「前面安定時間」の開始点（elapsedRealtime）
        var foregroundStableStartElapsedRealtime: Long,
        // UI 表示中などで「計測だけ止めたい」区間の開始点（elapsedRealtime）
        var uiPauseAtElapsedRealtime: Long?,
        // 猶予判定用（elapsedRealtime）
        var lastLeaveAtElapsedRealtime: Long?,
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
        val nowElapsed = timeSource.elapsedRealtime()

        val existing = states[packageName]
        if (existing == null) {
            // 完全な新規（ランタイム上はまだ見たことがないパッケージ）
            states[packageName] =
                State(
                    packageName = packageName,
                    accumulatedElapsedMillis = initialElapsedIfNew.coerceAtLeast(0L),
                    activeStartElapsedRealtime = nowElapsed,
                    foregroundStableStartElapsedRealtime = nowElapsed,
                    uiPauseAtElapsedRealtime = null,
                    lastLeaveAtElapsedRealtime = null,
                )
            // 「ランタイムとしては新規セッション」なので true
            return true
        }

        val lastLeave = existing.lastLeaveAtElapsedRealtime
        val continues =
            lastLeave != null && (nowElapsed - lastLeave) <= gracePeriodMillis

        if (!continues) {
            // 猶予時間を超えていたら、ランタイム上は新しいセッションとして扱う。
            // （ここでは initialElapsedIfNew は使わない。
            //    すでにランタイム中に積み上げた initialElapsedMillis がある前提）
            existing.accumulatedElapsedMillis = 0L
        }

        // セッション継続/新規いずれでも「前面区間の開始」はここから
        existing.activeStartElapsedRealtime = nowElapsed
        // 方針B: 前面安定時間も「アプリを開いたら必ずここから」
        existing.foregroundStableStartElapsedRealtime = nowElapsed
        existing.uiPauseAtElapsedRealtime = null
        existing.lastLeaveAtElapsedRealtime = null

        // continues == true なら「継続セッション」なので false を返す
        return !continues
    }

    fun onLeaveTargetApp(packageName: String) {
        val state = states[packageName] ?: return
        // すでに leave 済みなら二重加算しない
        if (state.lastLeaveAtElapsedRealtime != null) return

        val nowElapsed = timeSource.elapsedRealtime()

        // UI 表示中などで計測を止めている場合は，離脱時点での追加加算は不要
        if (state.uiPauseAtElapsedRealtime != null) {
            state.lastLeaveAtElapsedRealtime = nowElapsed
            return
        }

        val delta =
            (nowElapsed - state.activeStartElapsedRealtime)
                .coerceAtLeast(0L)

        state.accumulatedElapsedMillis += delta
        state.lastLeaveAtElapsedRealtime = nowElapsed
    }

    /**
     * 提案やミニゲームなど，Refocus 側 UI を表示している間は「計測だけ止めたい」ため，
     * 現在の累積に確定してから以降の加算を止める．
     */
    fun onUiPause(
        packageName: String,
        nowElapsedRealtime: Long = timeSource.elapsedRealtime(),
    ) {
        val state = states[packageName] ?: return
        if (state.lastLeaveAtElapsedRealtime != null) return
        if (state.uiPauseAtElapsedRealtime != null) return

        val delta = (nowElapsedRealtime - state.activeStartElapsedRealtime).coerceAtLeast(0L)
        state.accumulatedElapsedMillis += delta
        state.uiPauseAtElapsedRealtime = nowElapsedRealtime
    }

    /**
     * UI 表示が終わったら，その時点から再び加算を再開する．
     */
    fun onUiResume(
        packageName: String,
        nowElapsedRealtime: Long = timeSource.elapsedRealtime(),
    ) {
        val state = states[packageName] ?: return
        if (state.lastLeaveAtElapsedRealtime != null) return
        if (state.uiPauseAtElapsedRealtime == null) return

        state.activeStartElapsedRealtime = nowElapsedRealtime
        state.uiPauseAtElapsedRealtime = null
    }

    fun computeElapsedFor(
        packageName: String,
        nowElapsedRealtime: Long,
    ): Long? {
        val state = states[packageName] ?: return null
        // すでに離脱しているなら「積み上げ済み」だけ返す（= 離脱後に伸びない）
        if (state.lastLeaveAtElapsedRealtime != null) return state.accumulatedElapsedMillis
        // UI 表示中は「積み上げ済み」だけ返す（= 表示中に伸びない）
        if (state.uiPauseAtElapsedRealtime != null) return state.accumulatedElapsedMillis

        val delta = (nowElapsedRealtime - state.activeStartElapsedRealtime).coerceAtLeast(0L)
        return state.accumulatedElapsedMillis + delta
    }

    fun sinceForegroundMillis(
        packageName: String,
        nowElapsedRealtime: Long,
    ): Long? {
        val state = states[packageName] ?: return null
        if (state.lastLeaveAtElapsedRealtime != null) return 0L

        val stableBase = state.foregroundStableStartElapsedRealtime
        // UI 表示中は，表示開始時点で止める（= その間は伸びない）
        val cap = state.uiPauseAtElapsedRealtime ?: nowElapsedRealtime
        return (cap - stableBase).coerceAtLeast(0L)
    }

    /**
     * packageName が変わらないまま「再び前面になった」ことを検知した場合に呼ぶ。
     * セッション累積は触らず、前面安定の起点だけ更新する（= 方針B）。
     */
    fun onForegroundReconfirmed(
        packageName: String,
        nowElapsedRealtime: Long = timeSource.elapsedRealtime(),
    ) {
        val state = states[packageName] ?: return
        // 前面扱いのままでも「安定起点」はここからにする
        state.foregroundStableStartElapsedRealtime = nowElapsedRealtime
    }
}
