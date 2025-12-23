package com.example.refocus.domain.overlay

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.SuggestionMode

/**
 * ドメイン層から見た「オーバーレイ UI コントローラ」の抽象。
 * WindowManager / Compose には依存しない。
 */
data class SuggestionOverlayUiModel(
    val title: String,
    val mode: SuggestionMode,
    val autoDismissMillis: Long,
    val interactionLockoutMillis: Long,
    val onSnoozeLater: () -> Unit,
    val onDisableThisSession: () -> Unit,
    val onDismissOnly: () -> Unit,
)

interface OverlayUiGateway {

    /**
     * 設定変更の反映（フォントサイズ・色・タッチモードなど）。
     */
    fun applySettings(customize: Customize)

    /**
     * タイマーオーバーレイ表示。
     *
     * @param displayMillisProvider 表示用の時間を取得する関数
     * @param visualMillisProvider 演出用の時間を取得する関数
     * @param onPositionChanged ドラッグ後の位置変更をドメイン側に伝えるコールバック
     */
    fun showTimer(
        displayMillisProvider: (Long) -> Long,
        visualMillisProvider: (Long) -> Long,
        onPositionChanged: (x: Int, y: Int) -> Unit,
    )

    /**
     * タイマーオーバーレイ非表示。
     */
    fun hideTimer()

    /**
     * 提案オーバーレイ表示。
     */
    suspend fun showSuggestion(model: SuggestionOverlayUiModel): Boolean

    /**
     * 提案オーバーレイ非表示。
     */
    fun hideSuggestion()
}
