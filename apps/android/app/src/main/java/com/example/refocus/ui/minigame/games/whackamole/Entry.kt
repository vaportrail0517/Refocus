package com.example.refocus.ui.minigame.games.whackamole

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val whackAMoleEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.WhackAMole,
                title = "モグラたたき",
                description = "出てきた丸をすばやくタップします",
                rules =
                    listOf(
                        "丸が出ている間にタップしてください",
                        "制限時間が経過すると終了です",
                    ),
                timeLimitSeconds = 15,
                estimatedSeconds = null,
                primaryActionLabel = "開始",
                canSkipBeforeStart = false,
            ),
        content = { seed, onFinished, modifier ->
            Game(
                seed = seed,
                onFinished = onFinished,
                modifier = modifier,
            )
        },
    )
