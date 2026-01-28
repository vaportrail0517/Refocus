package com.example.refocus.ui.minigame.games.minesweeper

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

internal val minesweeperEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.Minesweeper,
                title = "マインスイーパー",
                description = "地雷を避けて安全マスを開く",
                rules =
                    listOf(
                        "数字は周囲8マスの地雷の数です",
                        "「開く」モードではタップでマスを開きます",
                        "地雷が確定したマスには「旗」モードでタップすることで旗を立てられます",
                        "制限時間内に安全マスをすべて開くとクリアです",
                    ),
                timeLimitSeconds = 60,
                primaryActionLabel = "開始",
            ),
        content = { seed, onFinished, modifier ->
            Game(
                seed = seed,
                onFinished = onFinished,
                modifier = modifier,
            )
        },
    )
