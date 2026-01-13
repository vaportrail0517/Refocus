package com.example.refocus.system.overlay.ui.minigame.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.refocus.core.model.MiniGameKind

/**
 * ミニゲーム実装を「種類（Kind）」から引けるようにするための最小カタログ。
 *
 * - ゲーム本体（Composable）は system 側に閉じる
 * - アプリ UI（デバッグ用）では descriptor のみを使って一覧表示できる
 */
typealias MiniGameContent =
    @Composable (
        seed: Long,
        onFinished: () -> Unit,
        modifier: Modifier,
    ) -> Unit

data class MiniGameDescriptor(
    val kind: MiniGameKind,
    val title: String,
    val description: String,
)

data class MiniGameEntry(
    val descriptor: MiniGameDescriptor,
    val content: MiniGameContent,
)
