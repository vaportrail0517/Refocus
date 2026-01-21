package com.example.refocus.core.preset

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.MiniGameOrder
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.preset.CustomizePresetValues.debug
import com.example.refocus.core.preset.CustomizePresetValues.default

/**
 * 設定プリセットの具体的な値。
 *
 * - [default] は [Customize] のデフォルト値そのもの。
 * - [debug] はデバッグ時に体感しやすい値へ寄せたプリセット。
 */
object CustomizePresetValues {
    /** 通常利用時のプリセット。Customize のデフォルト値をそのまま使う。 */
    val default: Customize = Customize()

    /** デバッグ用。体感しやすい値へ寄せる。 */
    val debug: Customize =
        Customize(
            gracePeriodMillis = 10_000L,
            pollingIntervalMillis = 500L,
            minFontSizeSp = 32f,
            maxFontSizeSp = 96f,
            timeToMaxSeconds = 60,
            // アニメーションの体感をしやすくするため，デバッグでは短めに設定
            basePulseEnabled = true,
            effectsEnabled = true,
            effectIntervalSeconds = 10,
            growthMode = TimerGrowthMode.SlowFastSlow,
            colorMode = TimerColorMode.GradientThree,
            suggestionEnabled = true,
            restSuggestionEnabled = true,
            suggestionTriggerSeconds = 20,
            suggestionTimeoutSeconds = 0,
            suggestionCooldownSeconds = 20,
            suggestionForegroundStableSeconds = 10,
            suggestionInteractionLockoutMillis = 400L,
            miniGameEnabled = true,
            miniGameOrder = MiniGameOrder.BeforeSuggestion,
        )
}
