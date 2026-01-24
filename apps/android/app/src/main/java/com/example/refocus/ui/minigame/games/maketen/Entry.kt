package com.example.refocus.ui.minigame.games.maketen

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val makeTenEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.MakeTen,
                title = "make 10",
                description = "4つの数字と四則演算と括弧で10を作る",
                timeLimitSeconds = 60,
                rules =
                    listOf(
                        "4つの数字全てをそれぞれ1回ずつ使って10を作ってください",
                        "四則演算と括弧が使えます",
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
