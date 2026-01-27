package com.example.refocus.ui.minigame.games.morsetreeword

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val morseTreeWordEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.MorseTreeWord,
                title = "モールスツリー",
                description = "点と線で木を辿って単語を完成",
                timeLimitSeconds = 30,
                rules =
                    listOf(
                        "画面下の『・』『－』で樹形図を辿ります",
                        "指定された単語の文字に到達すると自動で次へ進みます",
                        "詰まったら『戻る』で1手戻せます",
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
