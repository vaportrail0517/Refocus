package com.example.refocus.ui.minigame.games.memoji

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val memojiEntry = MiniGameEntry(
    descriptor = MiniGameDescriptor(
        kind = MiniGameKind.Memoji,
        title = "Memoji",
        description = "5秒間で絵文字の順番を覚え、正しい順序で選択してください。",
    ),
    content = { seed, onFinished, modifier ->
        Game(
            seed = seed,
            onFinished = onFinished,
            modifier = modifier,
        )
    }
)
