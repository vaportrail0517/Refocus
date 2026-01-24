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
                description = "絵文字の順番を記憶",
                rules =
                    listOf(
                        "絵文字列が5秒間表示されるので覚えてください",
                        "その後，記憶した絵文字列を入力してください",
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
