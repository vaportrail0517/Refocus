package com.example.refocus.ui.minigame.games.hanoipuzzle


import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val hanoiPuzzleEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.HanoiPuzzle,
                title = "ハノイの塔",
                description = "上の配置を下で再現",
                timeLimitSeconds = 60,
                estimatedSeconds = 50,
                rules =
                    listOf(
                        "上の目標配置と同じ配置を，下の盤面で作ってください",
                        "円盤は1枚ずつしか動かせません",
                        "大きい円盤を小さい円盤の上には置けません",
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
