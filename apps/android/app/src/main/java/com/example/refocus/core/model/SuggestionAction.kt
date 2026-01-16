package com.example.refocus.core.model

/**
 * 「やりたいこと」から，そのまま次の行動に移るための遷移先を表す．
 *
 * 現時点ではドメインモデルとしての導入のみ（永続化や UI 反映は次フェーズで実装）．
 */
sealed interface SuggestionAction {
    /**
     * 遷移先なし．
     */
    data object None : SuggestionAction

    /**
     * URL を開く．
     *
     * - url は正規化済み（例: https:// が付与済み）を想定する．
     * - display は UI 表示向けの短いラベル（例: host）で，任意．
     */
    data class Url(
        val url: String,
        val display: String? = null,
    ) : SuggestionAction

    /**
     * 他アプリを起動する（ランチャー起動相当）．
     *
     * - packageName は起動対象のパッケージ名．
     * - display は UI 表示向けの短いラベル（例: アプリ名）で，任意．
     */
    data class App(
        val packageName: String,
        val display: String? = null,
    ) : SuggestionAction
}
