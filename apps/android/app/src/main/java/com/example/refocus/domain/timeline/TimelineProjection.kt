package com.example.refocus.domain.timeline

import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart

/**
 * TimelineEvent の列を「いまの設定」で投影した結果。
 *
 * - セッション境界（Start/Pause/Resume/End）は SessionProjector に一本化
 * - 日付境界の分割は SessionPartGenerator に一本化
 *
 * これを 1 箇所にまとめることで，
 * Overlay / 履歴 / 統計 が同じ意味のセッションを共有できる。
 */
data class TimelineProjection(
    val sessions: List<Session>,
    val sessionsWithEvents: List<SessionProjector.SessionWithEvents>,
    val eventsBySessionId: Map<Long, List<SessionEvent>>,
    val sessionParts: List<SessionPart>,
)
