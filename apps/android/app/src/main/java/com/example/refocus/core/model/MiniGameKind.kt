package com.example.refocus.core.model

/**
 * ミニゲームの種類。
 *
 * 将来追加する場合はここに列挙子を追加する。
 * 表示や実行の具体は system 側のレジストリで解決する想定。
 */
enum class MiniGameKind {
    FlashAnzan,
    MakeTen,
    EightPuzzle,
    MirrorText,
    Memoji,
    Minesweeper,
}
