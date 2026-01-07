package com.example.refocus.domain.overlay.port

import com.example.refocus.core.model.MiniGameKind

/**
 * ミニゲーム表示のための UI モデル。
 *
 * - 結果（正誤など）はドメインで扱わない（要件：記録しない）。
 * - 問題生成などのゲーム内部ロジックは system 側（UI 実装）に閉じる。
 */
data class MiniGameOverlayUiModel(
    val kind: MiniGameKind,
    val seed: Long,
    /** ユーザが結果表示後に閉じたときに呼ばれる */
    val onFinished: () -> Unit,
)
