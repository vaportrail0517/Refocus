package com.example.refocus.ui.minigame.games.maketen

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val makeTenEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.MakeTen,
                title = "make ten",
                description = "4つの数字と記号で10を作ります．",
                timeLimitSeconds = 60,
                rules =
                    listOf(
                        "4つの数字はそれぞれ1回だけ使います．",
                        "＋ − × ÷ と括弧が使えます．",
                        "式が10になればクリアです．",
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
