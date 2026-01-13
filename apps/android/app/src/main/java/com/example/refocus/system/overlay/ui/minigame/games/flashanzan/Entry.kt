package com.example.refocus.system.overlay.ui.minigame.games.flashanzan

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.system.overlay.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.system.overlay.ui.minigame.catalog.MiniGameEntry

internal val flashAnzanEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.FlashAnzan,
                title = "フラッシュ暗算",
                description = "5つの数字の合計を素早く計算します．",
            ),
        content = { seed, onFinished, modifier ->
            Game(
                seed = seed,
                onFinished = onFinished,
                modifier = modifier,
            )
        },
    )
