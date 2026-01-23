package com.example.refocus.ui.minigame.games.flashanzan

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val flashAnzanEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.FlashAnzan,
                title = "フラッシュ暗算",
                description = "表示される数字の合計を計算します．",
                rules =
                    listOf(
                        "5つの数字が順番に表示されます．",
                        "合計を入力して判定します．",
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
