package com.example.refocus.domain.overlay

import com.example.refocus.core.model.OverlaySuggestionMode
import com.example.refocus.core.model.Settings

/**
 * ドメイン層から見た「オーバーレイ UI コントローラ」の抽象。
 * WindowManager / Compose には依存しない。
 */
data class SuggestionOverlayUiModel(
    val title: String,
    val mode: OverlaySuggestionMode,
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
    fun applySettings(settings: Settings)

    /**
     * タイマーオーバーレイ表示。
     *
     * @param elapsedMillisProvider SessionManager などから経過時間を取得する関数
     * @param onPositionChanged ドラッグ後の位置変更をドメイン側に伝えるコールバック
     */
    fun showTimer(
        elapsedMillisProvider: (Long) -> Long,
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
