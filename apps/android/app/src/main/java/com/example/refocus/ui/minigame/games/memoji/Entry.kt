package com.example.refocus.ui.minigame.games.memoji

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val memojiEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.Memoji,
                title = "Memoji",
                description = "絵文字の順番を覚えて入力します．",
                rules =
                    listOf(
                        "最初の5秒で絵文字の順番を覚えます．",
                        "選択肢から順番に5回入力します．",
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
