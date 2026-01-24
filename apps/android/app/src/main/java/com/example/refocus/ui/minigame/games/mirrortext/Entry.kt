package com.example.refocus.ui.minigame.games.mirrortext

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val mirrorTextEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.MirrorText,
                title = "鏡文字デコード",
                description = "反転した英文を読み取って入力",
                timeLimitSeconds = 60,
                rules =
                    listOf(
                        "左右反転した文を読み取り，入力してください",
                        "クリアするか時間切れになると終了します",
                    ),
            ),
        content = { seed, onFinished, modifier ->
            Game(
                seed = seed,
                onFinished = onFinished,
                modifier = modifier,
            )
        },
    )
