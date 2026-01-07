package com.example.refocus.core.model

/**
 * 提案（Suggestion）とミニゲームの表示順序。
 *
 * - BeforeSuggestion: ミニゲーム完了後に提案を表示する
 * - AfterSuggestion: 提案を閉じた後にミニゲームを表示する
 */
enum class MiniGameOrder {
    BeforeSuggestion,
    AfterSuggestion,
}
