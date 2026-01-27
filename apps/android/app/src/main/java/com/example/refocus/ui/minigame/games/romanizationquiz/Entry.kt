package com.example.refocus.ui.minigame.games.romanizationquiz

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor
import com.example.refocus.ui.minigame.catalog.MiniGameEntry

private const val TIME_LIMIT_SECONDS = 40

internal val romanizationQuizEntry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.RomanizationQuiz,
                title = "英語表記推理クイズ",
                description = "ヒントから英語表記を当てる",
                timeLimitSeconds = TIME_LIMIT_SECONDS,
                estimatedSeconds = TIME_LIMIT_SECONDS,
                rules =
                    listOf(
                        "上のヒント（4つ前後の『単語 - 英語表記』）を見比べて，文字と音の対応を推理します",
                        "中央の単語に対応する英語表記を四択から選びます",
                        "制限時間内に1問に回答します",
                    ),
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
