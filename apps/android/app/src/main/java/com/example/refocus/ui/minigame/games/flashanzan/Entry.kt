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
                description = "順に表示される数字の和を計算",
                rules =
                    listOf(
                        "5つの数字が順番に表示されるので，最後に計算した和を入力してください",
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
