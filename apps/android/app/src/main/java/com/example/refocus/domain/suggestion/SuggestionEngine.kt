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
        val lastDecisionElapsedMillis: Long?,
        val isOverlayShown: Boolean,
        val disabledForThisSession: Boolean,
    )

    /**
     * 提案すべき条件をすべて満たしているかどうか。
     */
    fun shouldShow(input: Input): Boolean {
        if (input.isOverlayShown) return false
        if (input.disabledForThisSession) return false

        val s = input.settings
        if (!s.suggestionEnabled) return false

        val triggerMs = suggestionTriggerThresholdMillis(s)
        if (triggerMs == Long.MAX_VALUE) return false
        if (input.elapsedMillis < triggerMs) return false

        val stableMs = suggestionForegroundStableThresholdMillis(s)
        if (input.sinceForegroundMillis < stableMs) return false

        // 変更: クールダウンは「セッション累積」で判定する
        val last = input.lastDecisionElapsedMillis
        if (last != null) {
            val cooldownMs = suggestionCooldownMillis(s)
            if (input.elapsedMillis < last + cooldownMs) return false
        }

        return true
    }

    private fun suggestionCooldownMillis(settings: Settings): Long {
        val seconds = settings.suggestionCooldownSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionTriggerThresholdMillis(settings: Settings): Long {
        if (!settings.suggestionEnabled) return Long.MAX_VALUE
        val seconds = settings.suggestionTriggerSeconds
        if (seconds <= 0) return Long.MAX_VALUE
        return seconds.toLong() * 1_000L
    }

    private fun suggestionForegroundStableThresholdMillis(settings: Settings): Long {
        val seconds = settings.suggestionForegroundStableSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }
}
