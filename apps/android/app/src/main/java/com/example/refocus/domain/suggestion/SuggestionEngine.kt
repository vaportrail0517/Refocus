package com.example.refocus.domain.suggestion

import com.example.refocus.core.model.Settings

/**
 * 「今、提案カードを出すべきかどうか」を判定する純粋ロジック。
 *
 * ここでは OverlayService の内部状態には触らず、
 * 必要な情報だけを引数で受け取る。
 */
class SuggestionEngine {

    data class Input(
        val elapsedMillis: Long,
        val sinceForegroundMillis: Long,
        val settings: Settings,
        val nowMillis: Long,
        val snoozedUntilMillis: Long?,
        val isOverlayShown: Boolean,
        val disabledForThisSession: Boolean,
    )

    /**
     * 提案すべき条件をすべて満たしているかどうか。
     */
    fun shouldShow(input: Input): Boolean {
        // すでに表示中なら新しく出さない
        if (input.isOverlayShown) return false
        // このセッションでは提案しない設定なら終了
        if (input.disabledForThisSession) return false

        val s = input.settings

        // 提案自体が OFF の場合は何もしない
        if (!s.suggestionEnabled) return false

        // グローバルクールダウン中ならスキップ
        val snoozedUntil = input.snoozedUntilMillis
        if (snoozedUntil != null && input.nowMillis < snoozedUntil) {
            return false
        }

        // トリガーとなる連続利用時間
        val triggerMs = suggestionTriggerThresholdMillis(s)
        if (triggerMs == Long.MAX_VALUE) return false
        if (input.elapsedMillis < triggerMs) return false

        // 前面安定時間（復帰直後でないこと）チェック
        val stableMs = suggestionForegroundStableThresholdMillis(s)
        if (input.sinceForegroundMillis < stableMs) return false

        return true
    }

    private fun suggestionTriggerThresholdMillis(settings: Settings): Long {
        if (!settings.suggestionEnabled) {
            return Long.MAX_VALUE
        }
        val seconds = settings.suggestionTriggerSeconds
        if (seconds <= 0) {
            return Long.MAX_VALUE
        }
        return seconds.toLong() * 1_000L
    }

    private fun suggestionForegroundStableThresholdMillis(settings: Settings): Long {
        val seconds = settings.suggestionForegroundStableSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }
}
