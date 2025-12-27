package com.example.refocus.domain.overlay.port

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
        /**
         * show/hide の呼び出し順序が並び替わった場合でも，古い hide を無視できるようにするためのトークン．
         * 通常は packageName を渡す．
         *
         * null の場合は「順序保護なし」として扱う．
         */
        token: String? = null,
        displayMillisProvider: (Long) -> Long,
        visualMillisProvider: (Long) -> Long,
        onPositionChanged: (x: Int, y: Int) -> Unit,
    )

    /**
     * タイマーオーバーレイ非表示。
     */
    fun hideTimer(
        /**
         * showTimer の token と同一の場合のみ hide を実行する．
         * null の場合は常に hide を実行する．
         */
        token: String? = null,
    )

    /**
     * 提案オーバーレイ表示。
     */
    suspend fun showSuggestion(model: SuggestionOverlayUiModel): Boolean

    /**
     * 提案オーバーレイ非表示。
     */
    fun hideSuggestion()
}