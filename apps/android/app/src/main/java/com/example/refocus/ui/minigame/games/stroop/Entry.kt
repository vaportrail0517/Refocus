package com.example.refocus.ui.minigame.games.stroop

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val stroopEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.Stroop,
                title = "ストループ",
                description = "色と言葉の干渉に注意して答える",
                rules =
                    listOf(
                        "指示が「色」のときは，文字の色を選びます",
                        "指示が「意味」のときは，書かれている色名を選びます",
                        "制限時間内にできるだけ多く答えます",
                    ),
                timeLimitSeconds = 25,
                estimatedSeconds = 20,
                primaryActionLabel = "開始",
                canSkipBeforeStart = true,
            ),
        content = { seed, onFinished, modifier ->
            Game(
                seed = seed,
                onFinished = onFinished,
                modifier = modifier,
            )
        },
    )
