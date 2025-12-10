package com.example.refocus.domain.timeline

import com.example.refocus.core.model.*
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
     * @param nowMillis まだ終了していないセッションの「仮想終了時刻」に使う
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
            var lastActiveMillis: Long,
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
                lastActiveMillis = ts,
            )
        }

        fun endSession(pkg: String, ts: Long) {
            val state = activeSessions.remove(pkg) ?: return
            state.events.add(
                SessionEvent(
                    id = null,
                    sessionId = state.sessionId,
                    type = SessionEventType.End,
                    timestampMillis = ts,
                )
            )
            finished += SessionWithEvents(
                session = Session(
                    id = state.sessionId,
                    packageName = pkg,
                ),
                events = state.events.toList(),
            )
        }

        fun pauseAllActive(ts: Long) {
            activeSessions.values.forEach { state ->
                state.events.add(
                    SessionEvent(
                        id = null,
                        sessionId = state.sessionId,
                        type = SessionEventType.Pause,
                        timestampMillis = ts,
                    )
                )
            }
        }

        fun resumeCurrentForeground(ts: Long) {
            val pkg = currentForeground ?: return
            val state = activeSessions[pkg] ?: return
            state.events.add(
                SessionEvent(
                    id = null,
                    sessionId = state.sessionId,
                    type = SessionEventType.Resume,
                    timestampMillis = ts,
                )
            )
        }

        fun checkGraceTimeout(ts: Long) {
            val iterator = activeSessions.iterator()
            while (iterator.hasNext()) {
                val (pkg, state) = iterator.next()
                if (ts - state.lastActiveMillis >= stopGracePeriodMillis) {
                    state.events.add(
                        SessionEvent(
                            id = null,
                            sessionId = state.sessionId,
                            type = SessionEventType.End,
                            timestampMillis = ts,
                        )
                    )
                    finished += SessionWithEvents(
                        session = Session(
                            id = state.sessionId,
                            packageName = pkg,
                        ),
                        events = state.events.toList(),
                    )
                    iterator.remove()
                }
            }
        }

        // イベント列を順番に処理
        for (event in events.sortedBy { it.timestampMillis }) {
            val ts = event.timestampMillis

            when (event) {
                is ScreenEvent -> {
                    screenOn = (event.state == ScreenState.On)
                    if (!screenOn) {
                        // 画面 OFF → すべて Pause 扱い
                        pauseAllActive(ts)
                    } else {
                        // 画面 ON → 現在前面の対象アプリを Resume
                        resumeCurrentForeground(ts)
                    }
                }

                is ForegroundAppEvent -> {
                    // 前回の前面アプリが対象でなくなる → 最終アクティブ時刻を更新
                    val prev = currentForeground
                    if (prev != null) {
                        activeSessions[prev]?.lastActiveMillis = ts
                    }

                    currentForeground = event.packageName

                    if (screenOn && monitoringEnabled) {
                        val fg = currentForeground
                        if (fg != null && fg in targetPackages) {
                            // 新しい対象アプリが前面に来た
                            if (!activeSessions.containsKey(fg)) {
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
                    // （ここでは簡略化して常に true のままでもよい）
                }

                is ServiceLifecycleEvent -> {
                    // サービス停止時に全セッションを終了させるなどのルールを入れてもよい
                    if (event.state == ServiceState.Stopped) {
                        // 停止時点で全部 End 扱いにする例
                        val nowTs = ts
                        activeSessions.keys.toList().forEach { pkg ->
                            endSession(pkg, nowTs)
                        }
                    }
                }

                is TargetAppsChangedEvent -> {
                    // 対象から外れたアプリのセッションを終了扱いにするなど
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

            // 停止猶予のチェック
            checkGraceTimeout(ts)
        }

        // 最後まで来ても End していないセッションは nowMillis で閉じる
        val tsNow = nowMillis
        activeSessions.keys.toList().forEach { pkg ->
            endSession(pkg, tsNow)
        }

        return finished.sortedBy { it.session.id ?: 0L }
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
