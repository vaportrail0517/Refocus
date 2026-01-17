package com.example.refocus.domain.overlay.model

/**
 * セッション復元における「提案ゲート」．
 *
 * - lastDecisionElapsedMillis: 提案に対する意思決定（Snooze/Dismiss/Open など）が最後に行われた時点の「セッション累積」．
 *
 * 提案を恒久的に止めるゲートは持たず，抑制はクールダウンで表現する．
 */
data class SessionSuggestionGate(
    val lastDecisionElapsedMillis: Long? = null,
)

/**
 * Timeline から復元した「現在セッション」のブートストラップ情報．
 */
data class SessionBootstrapFromTimeline(
    val initialElapsedMillis: Long,
    val isOngoingSession: Boolean,
    val gate: SessionSuggestionGate,
)
