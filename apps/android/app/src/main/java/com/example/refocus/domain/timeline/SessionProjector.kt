package com.example.refocus.domain.timeline

import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.SettingsChangedEvent
import com.example.refocus.core.model.SuggestionDecisionEvent
import com.example.refocus.core.model.SuggestionShownEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.domain.session.SessionDurationCalculator
import java.time.Instant
import java.time.ZoneId

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
     * @param targetPackages 対象アプリの packageName セット
     * @param stopGracePeriodMillis 停止猶予時間
     * @param nowMillis 「いま」の時刻。停止猶予をまたいで復帰していないセッションは
     *                  この時刻までに十分時間が経っていれば終了扱いにする。
     */
    fun projectSessions(
        events: List<TimelineEvent>,
        targetPackages: Set<String>,
        stopGracePeriodMillis: Long,
        nowMillis: Long,
    ): List<SessionWithEvents> {

        var currentForeground: String? = null
        var screenOn: Boolean = true
        var monitoringEnabled: Boolean = true // Permission / 設定を見て将来拡張

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

        fun startSession(pkg: String, ts: Long) {
            if (pkg !in targetPackages) return
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

        fun resumeCurrentForeground(ts: Long) {
            val pkg = currentForeground ?: return
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
                    } else {
                        // 画面 ON → 現在前面の対象アプリを Resume
                        resumeCurrentForeground(ts)
                    }
                }

                is ForegroundAppEvent -> {
                    val prev = currentForeground
                    val newPkg = event.packageName

                    // 前の前面アプリが対象だった場合は「離脱」とみなす
                    if (prev != null && prev != newPkg && prev in targetPackages) {
                        markInactive(prev, ts)
                    }

                    currentForeground = newPkg

                    if (screenOn && monitoringEnabled) {
                        val fg = currentForeground
                        if (fg != null && fg in targetPackages) {
                            if (!activeSessions.containsKey(fg)) {
                                // 停止猶予内に復帰していれば ActiveState が残っているはず、
                                // そうでなければ新しいセッションを開始
                                startSession(fg, ts)
                            } else {
                                // すでにセッション中なら Resume 扱い
                                resumeCurrentForeground(ts)
                            }
                        }
                    }
                }

                is PermissionEvent -> {
                    // 必須権限が失われたら monitoringEnabled を false にするなど、
                    // 詳細ルールは後で詰める。
                    // 今は UsageStats / Overlay が両方 Granted のときだけ true とするイメージ。
                }

                is ServiceLifecycleEvent -> {
                    // サービス停止時に全セッションを終了させる
                    if (event.state == ServiceState.Stopped) {
                        val nowTs = ts
                        activeSessions.keys.toList().forEach { pkg ->
                            endSession(pkg, nowTs)
                        }
                    }
                }

                is TargetAppsChangedEvent -> {
                    // 対象から外れたアプリのセッションを終了扱いにする
                    activeSessions.keys
                        .filter { it !in event.targetPackages }
                        .forEach { pkg -> endSession(pkg, ts) }
                }

                is SuggestionShownEvent,
                is SuggestionDecisionEvent,
                is SettingsChangedEvent -> {
                    // セッション構築には直接関与しない
                }
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

    /**
     * SessionWithEvents から SessionPart を生成するヘルパー。
     * 既存の SessionPartGenerator を置き換える用途を想定。
     */
    fun generateSessionParts(
        sessionsWithEvents: List<SessionWithEvents>,
        zoneId: ZoneId,
    ): List<SessionPart> {
        val result = mutableListOf<SessionPart>()
        for ((session, events) in sessionsWithEvents) {
            val segments =
                SessionDurationCalculator.buildActiveSegments(events, nowMillis = Long.MAX_VALUE)
            // date 切りを SessionPartGenerator と同様にやる（詳細は既存実装に合わせる）
            for (segment in segments) {
                val startInstant = Instant.ofEpochMilli(segment.startMillis)
                val endInstant = Instant.ofEpochMilli(segment.endMillis)
                val startZdt = startInstant.atZone(zoneId)
                val endZdt = endInstant.atZone(zoneId)
                val date = startZdt.toLocalDate()
                val startMinutes = startZdt.hour * 60 + startZdt.minute
                val endMinutes = endZdt.hour * 60 + endZdt.minute

                result += SessionPart(
                    sessionId = session.id ?: -1L,
                    packageName = session.packageName,
                    date = date,
                    startDateTime = startInstant,
                    endDateTime = endInstant,
                    startMinutesOfDay = startMinutes,
                    endMinutesOfDay = endMinutes,
                    durationMillis = segment.endMillis - segment.startMillis,
                )
            }
        }
        return result
    }
}
