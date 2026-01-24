package com.example.refocus.ui.minigame

/**
 * Compose UI テストで安定して参照できるようにするための testTag 定義．
 *
 * 本番挙動には影響せず，セマンティクス属性の付与のみを目的とする．
 */
object MiniGameTestTags {
    const val INTRO_ROOT = "minigame:intro:root"
    const val INTRO_START_BUTTON = "minigame:intro:start"
    const val INTRO_SKIP_BUTTON = "minigame:intro:skip"

    /**
     * Playing 状態で，ゲーム本体が Compose されていることを示すルートタグ．
     *
     * ゲーム固有 UI のタグに依存せず，ホスト側の状態遷移を検証できるようにする．
     */
    const val GAME_ROOT = "minigame:game:root"
}
