package com.example.refocus.ui.minigame.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.refocus.core.model.MiniGameKind

/**
 * ミニゲーム実装を「種類（Kind）」から引けるようにするための最小カタログ。
 *
 * - ゲーム本体（Composable）は ui 側に閉じる
 * - アプリ UI（デバッグ用）では descriptor のみを使って一覧表示できる
 */
typealias MiniGameContent =
    @Composable (
        seed: Long,
        onFinished: () -> Unit,
        modifier: Modifier,
    ) -> Unit

typealias MiniGameIntroExtraContent = @Composable () -> Unit

data class MiniGameDescriptor(
    val kind: MiniGameKind,
    val title: String,
    val description: String,
    /**
     * 開始画面などで表示する，ゲームのルール・操作説明（箇条書き想定）．
     *
     * 例：
     * - "制限時間は60秒です．"
     * - "合計を入力します．"
     */
    val rules: List<String> = emptyList(),
    /**
     * 制限時間（秒）．制限時間がないゲームは null．
     * 開始画面での説明や，今後の統一的な表示のために保持する．
     */
    val timeLimitSeconds: Int? = null,
    /**
     * 想定所要時間（秒）．開始画面での目安表示などで利用する想定．
     */
    val estimatedSeconds: Int? = null,
    /**
     * 開始画面の主ボタン文言．デフォルトは "開始"．
     */
    val primaryActionLabel: String = "開始",
    /**
     * 開始前にスキップ導線を出すかどうか．倫理・透明性の観点から原則 true．
     */
    val canSkipBeforeStart: Boolean = false,
)

data class MiniGameEntry(
    val descriptor: MiniGameDescriptor,
    val content: MiniGameContent,
    val introExtraContent: MiniGameIntroExtraContent? = null,
)
