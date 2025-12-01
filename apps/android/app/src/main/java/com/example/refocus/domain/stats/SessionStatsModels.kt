package com.example.refocus.domain.stats

/**
 * セッションの状態。
 *
 * - RUNNING: 今まさに前面で動いているアプリのセッション
 * - GRACE:   終了猶予中（End は来ていないが foreground ではない）
 * - FINISHED: End まで到達した完了済みセッション
 */
enum class SessionStatus {
    RUNNING,
    GRACE,
    FINISHED
}

/**
 * Pause / Resume のペア（ミリ秒単位）。
 * resumedAtMillis が null の場合は「未再開」の意味。
 */
data class PauseResumeStats(
    val pausedAtMillis: Long,
    val resumedAtMillis: Long?,
)

/**
 * 1 セッション分の統計情報（UI に依存しない純粋なドメインモデル）。
 */
data class SessionStats(
    val id: Long,
    val packageName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val durationMillis: Long,
    val status: SessionStatus,
    val pauseResumeEvents: List<PauseResumeStats>,
)
