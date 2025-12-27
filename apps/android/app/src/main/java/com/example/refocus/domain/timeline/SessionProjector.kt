package com.example.refocus.domain.timeline

import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceConfigEvent
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.model.SettingsChangedEvent
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionDecisionEvent
import com.example.refocus.core.model.SuggestionShownEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimelineEvent

/**
 * TimelineEvent の列から Session / SessionEvent を再構成する。
 *
 * - DB には Session/SessionEvent を保存しない
 * - 停止猶予時間などの設定を変えた場合は、この Projector を再実行するだけで
 *   過去のセッション解釈が変わる
 */
object SessionProjector {

    data class SessionWithEvents(
        val session: Session,
        val events: List<SessionEvent>,
    )

    /**
     * @param events   時刻昇順のイベント列（同一 timestamp の順序は問わない）
     * @param stopGracePeriodMillis 停止猶予時間
     * @param nowMillis 「いま」の時刻。停止猶予をまたいで復帰していないセッションは
     *                  この時刻までに十分時間が経っていれば終了扱いにする。
     */
    fun projectSessions(
        events: List<TimelineEvent>,
        stopGracePeriodMillis: Long,
        nowMillis: Long,
    ): List<SessionWithEvents> {

        var currentForeground: String? = null
        var screenOn: Boolean = true
        var monitoringEnabled: Boolean = true
        var serviceRunning: Boolean = true

        // TargetAppsChangedEvent から復元される「その時点の対象アプリ集合」。
        // seed（ウィンドウ開始より前の直前イベント）を events に含めることで，
        // ウィンドウ内の ForegroundAppEvent も正しく解釈できる。
        var currentTargetPackages: Set<String> = emptySet()
        val permissionStates = mutableMapOf<PermissionKind, PermissionState>()

        fun isTarget(pkg: String?): Boolean = pkg != null && pkg in currentTargetPackages

        // app -> アクティブなセッション状態
        data class ActiveState(
            val sessionId: Long,
            val events: MutableList<SessionEvent>,
            /**
             * そのアプリが「アクティブでなくなった」時刻。
             * - フォアグラウンドから外れた
             * - 画面 OFF になった
             * などのタイミングでセットされる。
             *
             * null のときは「いまもアクティブ中」。
             */
            var lastInactiveAtMillis: Long?,
        )

        val activeSessions = mutableMapOf<String, ActiveState>()
        val finished = mutableListOf<SessionWithEvents>()

        var nextSessionId = 1L

        /**
         * セッション境界には影響させず「その時点でアクティブなセッション」にイベントだけ付与する。
         * （提案の表示/操作など）
         */
        fun appendEventIfActive(pkg: String, type: SessionEventType, ts: Long) {
            val state = activeSessions[pkg] ?: return
            state.events.add(
                SessionEvent(
                    id = null,
                    sessionId = state.sessionId,
                    type = type,
                    timestampMillis = ts,
                )
            )
        }

        fun startSession(pkg: String, ts: Long) {
            if (pkg !in currentTargetPackages) return
            if (activeSessions.containsKey(pkg)) return

            val id = nextSessionId++
            val evs = mutableListOf<SessionEvent>()
            evs.add(
                SessionEvent(
                    id = null,
                    sessionId = id,
                    type = SessionEventType.Start,
                    timestampMillis = ts,
                )
            )
            activeSessions[pkg] = ActiveState(
                sessionId = id,
                events = evs,
                lastInactiveAtMillis = null,
            )
        }

        fun endSession(pkg: String, endTimestamp: Long) {
            val state = activeSessions.remove(pkg) ?: return
            state.events.add(
                SessionEvent(
                    id = null,
                    sessionId = state.sessionId,
                    type = SessionEventType.End,
                    timestampMillis = endTimestamp,
                )
            )
            finished += SessionWithEvents(
                session = Session(
                    id = state.sessionId,
                    packageName = pkg,
                ),
                events = state.events.sortedBy { it.timestampMillis },
            )
        }

        /**
         * アプリが「アクティブでなくなった」ことを記録（Pause + 非アクティブ開始時刻）。
         */
        fun markInactive(pkg: String, ts: Long) {
            val state = activeSessions[pkg] ?: return
            // すでに非アクティブなら何もしない
            if (state.lastInactiveAtMillis != null) return

            state.lastInactiveAtMillis = ts
            state.events.add(
                SessionEvent(
                    id = null,
                    sessionId = state.sessionId,
                    type = SessionEventType.Pause,
                    timestampMillis = ts,
                )
            )
        }

        fun pauseAllActive(ts: Long) {
            activeSessions.keys.forEach { pkg ->
                markInactive(pkg, ts)
            }
        }

        fun recomputeMonitoringEnabled(ts: Long) {
            val enabled =
                MonitoringStateProjector.isMonitoringEnabled(serviceRunning, permissionStates)
            if (enabled == monitoringEnabled) return
            monitoringEnabled = enabled
            if (!monitoringEnabled) {
                // 監視不能になった瞬間を「一時離脱」として扱う
                pauseAllActive(ts)
                // 監視不能期間に foreground は変わり得るので信用しない
                currentForeground = null
            }
        }

        fun resumeCurrentForeground(ts: Long) {
            val pkg = currentForeground ?: return
            if (!isTarget(pkg)) return
            val state = activeSessions[pkg] ?: return
            // 画面 ON / 再フォアグラウンドで復帰
            state.lastInactiveAtMillis = null
            state.events.add(
                SessionEvent(
                    id = null,
                    sessionId = state.sessionId,
                    type = SessionEventType.Resume,
                    timestampMillis = ts,
                )
            )
        }

        /**
         * 指定時刻までに停止猶予時間を超えて非アクティブだったセッションを終了扱いにする。
         *
         * セッションの終了時刻自体は「非アクティブになった瞬間（lastInactiveAtMillis）」に
         * 揃えておき、停止猶予はあくまで「同じセッションを再利用するための窓」として使う。
         */
        fun applyGraceTimeout(cutoffTs: Long) {
            val toClose = activeSessions
                .mapNotNull { (pkg, state) ->
                    val inactiveAt = state.lastInactiveAtMillis ?: return@mapNotNull null
                    if (cutoffTs - inactiveAt >= stopGracePeriodMillis) {
                        pkg to inactiveAt
                    } else {
                        null
                    }
                }

            for ((pkg, endAt) in toClose) {
                endSession(pkg, endAt)
            }
        }

        // イベント列を時刻順に処理
        for (event in events.sortedBy { it.timestampMillis }) {
            val ts = event.timestampMillis

            // まず、ここまでに停止猶予を超えているセッションがあれば閉じる
            applyGraceTimeout(ts)

            when (event) {
                is ScreenEvent -> {
                    screenOn = (event.state == ScreenState.On)
                    if (!screenOn) {
                        // 画面 OFF → すべて Pause 扱い（非アクティブ開始）
                        pauseAllActive(ts)
                    } else if (monitoringEnabled) {
                        // 画面 ON → （監視できている時だけ）現在前面の対象アプリを Resume
                        resumeCurrentForeground(ts)
                    }
                }

                is ForegroundAppEvent -> {
                    val prev = currentForeground
                    val newPkg = event.packageName

                    // 前の前面アプリが「その時点で対象」だった場合は「離脱」とみなす
                    if (prev != null && prev != newPkg && isTarget(prev)) {
                        markInactive(prev, ts)
                    }

                    currentForeground = newPkg

                    if (screenOn && monitoringEnabled) {
                        val fg = currentForeground
                        if (isTarget(fg)) {
                            val pkg = fg!!
                            if (!activeSessions.containsKey(pkg)) {
                                // 停止猶予内に復帰していれば ActiveState が残っているはず、
                                // そうでなければ新しいセッションを開始
                                startSession(pkg, ts)
                            } else {
                                // すでにセッション中なら Resume 扱い
                                resumeCurrentForeground(ts)
                            }
                        }
                    }
                }

                is PermissionEvent -> {
                    permissionStates[event.permission] = event.state
                    recomputeMonitoringEnabled(ts)
                }

                is ServiceLifecycleEvent -> {
                    serviceRunning = (event.state == ServiceState.Started)
                    recomputeMonitoringEnabled(ts)
                }

                is TargetAppsChangedEvent -> {
                    // その時点の対象集合を更新する（セッション境界の正はこの集合によって決まる）
                    currentTargetPackages = event.targetPackages

                    // 安全策：もし対象から外れたアプリのセッションが残っていたら，ここで閉じる
                    // （通常は Refocus 操作中に対象変更するため，対象アプリを前面で計測中のケースは起きにくい想定）
                    activeSessions.keys
                        .filter { it !in currentTargetPackages }
                        .forEach { pkg ->
                            markInactive(pkg, ts)
                            endSession(pkg, ts)
                        }
                }

                is SuggestionShownEvent -> {
                    // セッション境界は変えず、アクティブなセッションに「表示された」事実だけ載せる
                    appendEventIfActive(event.packageName, SessionEventType.SuggestionShown, ts)
                }

                is SuggestionDecisionEvent -> {
                    // 操作も同様に「アクティブなセッション」に付与する
                    val t = when (event.decision) {
                        SuggestionDecision.Snoozed ->
                            SessionEventType.SuggestionSnoozed

                        SuggestionDecision.Dismissed ->
                            SessionEventType.SuggestionDismissed

                        SuggestionDecision.DisabledForSession ->
                            SessionEventType.SuggestionDisabledForSession
                    }
                    appendEventIfActive(event.packageName, t, ts)
                }

                is SettingsChangedEvent -> Unit // セッション境界には影響させない

                is ServiceConfigEvent -> Unit // サービス設定変更はセッション境界には影響させない
            }
        }

        // すべてのイベント処理が終わった段階でもう一度猶予切れ判定を行う
        applyGraceTimeout(nowMillis)

        // まだ activeSessions に残っているものは
        // - いまもアクティブ中
        // - 「停止猶予内で一時離脱中」
        // のどちらかなので、未終了セッションとして返す。
        val ongoing = activeSessions.map { (pkg, state) ->
            SessionWithEvents(
                session = Session(
                    id = state.sessionId,
                    packageName = pkg,
                ),
                events = state.events.sortedBy { it.timestampMillis },
            )
        }

        return (finished + ongoing).sortedBy { it.session.id ?: 0L }
    }
}
