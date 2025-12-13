// core/model/Suggestion.kt
package com.example.refocus.core.model

/**
 * 「やりたいこと」を表すモデル。
 *
 * - id: 永続化用の一意ID（Room の主キーと対応）
 * - title: ユーザーが入力したテキスト
 * - createdAtMillis: 作成時刻
 * - kind: 休憩/やりたいこと/タスク などの種別（将来利用）
 * - timeSlot: 好ましい時間帯（将来利用）
 */
data class Suggestion(
    val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long,
    val kind: SuggestionKind = SuggestionKind.Generic,
    val timeSlot: SuggestionTimeSlot = SuggestionTimeSlot.Anytime,
    val durationTag: SuggestionDurationTag = SuggestionDurationTag.Medium,
    val priority: SuggestionPriority = SuggestionPriority.Normal,
)

enum class SuggestionKind {
    Generic,
    Rest,
    Want,
    Task,
}

enum class SuggestionTimeSlot {
    Anytime,        // いつでも
    Dawn,           // 早朝（例: 4〜7）
    Morning,        // 午前（例: 8〜11）
    Noon,           // 昼（例: 11〜14）
    Afternoon,      // 午後（例: 13〜17）
    Evening,        // 夕方（例: 17〜19）
    Night,          // 夜（例: 19〜23）
    LateNight,      // 深夜（例: 23〜3）
}


enum class SuggestionDurationTag {
    Short,   // 〜15分くらい
    Medium,  // 15〜40分くらい
    Long,    // 40分〜
}

enum class SuggestionPriority {
    Low,
    Normal,
    High,
}

enum class SuggestionDecision {
    Snoozed,            // あとで
    Dismissed,          // 閉じた（自動タイムアウト含む）
    DisabledForSession, // このセッションでは非表示
}

data class SuggestionInstance(
    val suggestionEventId: Long,        // SessionEvent.id (SuggestionShown 側)
    val sessionId: Long,
    val packageName: String,
    val shownAtMillis: Long,

    val decision: SuggestionDecision?,  // null の場合もあり得ると想定
    val decisionAtMillis: Long?,

    val endAtMillis: Long?,
    val timeToEndMillis: Long?,         // endAt - shownAt
    val endedSoon: Boolean?,            // end が無い時は null
)

