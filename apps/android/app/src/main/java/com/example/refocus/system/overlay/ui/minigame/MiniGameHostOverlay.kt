package com.example.refocus.system.overlay.ui.minigame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.refocus.core.model.MiniGameKind

/**
 * ミニゲーム用のフルスクリーンオーバーレイ。
 *
 * - 背景のタップは「裏のアプリに触れてしまう」事故を防ぐために吸収する（何もしない）
 * - 実際のゲーム内容は kind ごとに切り替える
 */
@Composable
fun MiniGameHostOverlay(
    kind: MiniGameKind,
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                // 背景タップを吸収（閉じない）
                .clickable(
                    indication = null,
                    interactionSource = interaction,
                ) {},
        contentAlignment = Alignment.Center,
    ) {
        MiniGameFrame {
            when (kind) {
                MiniGameKind.FlashAnzan -> {
                    FlashAnzanGame(
                        seed = seed,
                        onFinished = onFinished,
                        modifier = it,
                    )
                }

                MiniGameKind.TenPuzzle -> {
                    TenPuzzleGame(
                        seed = seed,
                        onFinished = onFinished,
                        modifier = it,
                    )
                }
            }
        }
    }
}
