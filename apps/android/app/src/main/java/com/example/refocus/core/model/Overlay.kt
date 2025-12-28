package com.example.refocus.core.model

enum class TimerTouchMode {
    Drag, // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

/**
 * タイマーの成長モード。
 * p: 0〜1（timeToMaxSeconds に対する経過割合）
 *
 * - Linear: 線形
 * - FastToSlow: 初めは速く、大きくなるにつれてゆっくり
 * - SlowToFast: 初めはゆっくり、後半は速く
 * - SlowFastSlow: 真ん中あたりで一番速い（スローインアウト）
 */
enum class TimerGrowthMode {
    Linear,
    FastToSlow,
    SlowToFast,
    SlowFastSlow,
}

/**
 * タイマー背景色のモード。
 *
 * - Fixed: 単色
 * - GradientTwo: 2色グラデーション
 * - GradientThree: 3色グラデーション
 */
enum class TimerColorMode {
    Fixed,
    GradientTwo,
    GradientThree,
}

/**
 * タイマーに表示する時間のモード。
 *
 * - SessionElapsed: 現在の論理セッションの経過時間（従来）
 * - TodayThisTarget: 現在表示中の対象アプリの「今日の累計使用時間」
 * - TodayAllTargets: 全対象アプリの「今日の累計使用時間」
 */
enum class TimerTimeMode {
    SessionElapsed,
    TodayThisTarget,
    TodayAllTargets,
}

/**
 * タイマーの演出（サイズ・色など）に使う時間基準。
 *
 * - SessionElapsed: 論理セッションの経過時間を使う（セッション開始で 0 から）
 * - FollowDisplayTime: 「タイマーに表示する時間」と同じ時間基準を使う
 */
enum class TimerVisualTimeBasis {
    SessionElapsed,
    FollowDisplayTime,
}

/**
 * 「やりたいこと」を表すモデル。
 *
 * - id: 永続化用の一意ID（Room の主キーと対応）
 * - title: ユーザーが入力したテキスト
 * - createdAtMillis: 作成時刻
 * - kind: 休憩/やりたいこと/タスク などの種別（将来利用）
 * - timeSlots: 好ましい時間帯（複数選択）
 *
 * timeSlots のルール:
 * - 空は不可（空になったら {Anytime} に正規化）
 * - Anytime は他と排他的（含まれていたら {Anytime} に正規化）
 */
data class Suggestion(
    val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long,
    val kind: SuggestionMode = SuggestionMode.Generic,
    val timeSlots: Set<SuggestionTimeSlot> = setOf(SuggestionTimeSlot.Anytime),
    val durationTag: SuggestionDurationTag = SuggestionDurationTag.Medium,
    val priority: SuggestionPriority = SuggestionPriority.Normal,
)

enum class SuggestionMode {
    Generic,
    Rest,
    // Want,
    // Task,
}

enum class SuggestionTimeSlot {
    Anytime, // いつでも
    Dawn, // 早朝（例: 4〜7）
    Morning, // 午前（例: 8〜11）
    Noon, // 昼（例: 11〜14）
    Afternoon, // 午後（例: 13〜17）
    Evening, // 夕方（例: 17〜19）
    Night, // 夜（例: 19〜23）
    LateNight, // 深夜（例: 23〜3）
}

enum class SuggestionDurationTag {
    Short, // 〜15分くらい
    Medium, // 15〜40分くらい
    Long, // 40分〜
}

enum class SuggestionPriority {
    Low,
    Normal,
    High,
}

enum class SuggestionDecision {
    Snoozed, // あとで
    Dismissed, // 閉じた（自動タイムアウト含む）
    DisabledForSession, // このセッションでは非表示
}

data class SuggestionInstance(
    val suggestionEventId: Long, // SessionEvent.id (SuggestionShown 側)
    val sessionId: Long,
    val packageName: String,
    val shownAtMillis: Long,
    val decision: SuggestionDecision?, // null の場合もあり得ると想定
    val decisionAtMillis: Long?,
    val endAtMillis: Long?,
    val timeToEndMillis: Long?, // endAt - shownAt
    val endedSoon: Boolean?, // end が無い時は null
)
