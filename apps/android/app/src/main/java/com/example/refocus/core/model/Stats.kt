package com.example.refocus.core.model

import java.time.LocalDate

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

data class DailyStats(
    val date: LocalDate,
    // セッション軸
    val sessionCount: Int,
    val averageSessionDurationMillis: Long,
    val longestSessionDurationMillis: Long,
    // 時間軸（全体）
    val totalUsageMillis: Long,
    // アプリ別統計
    val appUsageStats: List<AppUsageStats> = emptyList(),
    // 時間帯バケット統計（24時間タイムライン用）
    val timeBuckets: List<TimeBucketStats> = emptyList(),
    // 提案単位の統計
    val suggestionStats: SuggestionDailyStats? = null,
)

/**
 * 1 日の中でのアプリ別の使い方。
 */
data class AppUsageStats(
    val packageName: String,
    val totalUsageMillis: Long,
    val averageSessionDurationMillis: Long,
    val sessionCount: Int,
)

/**
 * 24 時間を固定幅バケットに切った単位。
 */
data class TimeBucketStats(
    val startMinutesOfDay: Int,  // 0, 30, 60, ...
    val endMinutesOfDay: Int,    // 30, 60, ...
    val totalUsageMillis: Long,  // バケット内の合計利用時間
    val topPackageName: String?, // バケット内で最も使われたアプリ
)

data class SuggestionDailyStats(
    val date: LocalDate,
    val totalShown: Int,

    val snoozedCount: Int,
    val dismissedCount: Int,
    val disabledForSessionCount: Int,

    val endedSoonCount: Int,        // しきい値以内に終了した提案
    val continuedCount: Int,        // しきい値より長く続いた提案
    val noEndYetCount: Int,         // セッションがまだ終わっていない or 情報不足

    // オプション: 決定別に「短時間で終了した件数」を持ちたいなら
    val endedSoonByDecision: Map<SuggestionDecision, Int> = emptyMap(),
) {
    /**
     * 「あとで」と「閉じた」をまとめた件数。
     * 統計画面ではこの値を使って 1 カテゴリとして表示する。
     */
    val skippedCount: Int
        get() = snoozedCount + dismissedCount
}
