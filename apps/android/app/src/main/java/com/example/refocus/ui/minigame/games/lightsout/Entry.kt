package com.example.refocus.ui.minigame.games.lightsout

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal const val LIGHTS_OUT_TIME_LIMIT_SECONDS: Int = 45

internal val lightsOutEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.LightsOut,
                title = "ライツアウト",
                description = "ライトをすべて消すパズル",
                rules =
                    listOf(
                        "マスをタップすると，自分と上下左右のライトが反転します",
                        "すべて消灯できればクリアです",
                        "制限時間は${LIGHTS_OUT_TIME_LIMIT_SECONDS}秒です",
                    ),
                timeLimitSeconds = LIGHTS_OUT_TIME_LIMIT_SECONDS,
                canSkipBeforeStart = true,
            ),
        content = { seed, onFinished, modifier ->
            Game(
                seed = seed,
                onFinished = onFinished,
                modifier = modifier,
            )
        },
    )
