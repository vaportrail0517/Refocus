package com.example.refocus.domain.overlay.model

/**
 * セッション復元における「提案ゲート」．
 *
 * - disabledForThisSession: この論理セッションではもう提案を出さない
 * - lastDecisionElapsedMillis: Snooze/Dismiss が最後に行われた時点の「セッション累積」
 */
data class SessionSuggestionGate(
    val disabledForThisSession: Boolean = false,
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
