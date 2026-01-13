package com.example.refocus.system.overlay.ui.minigame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.system.overlay.ui.minigame.catalog.MiniGameRegistry
import com.example.refocus.system.overlay.ui.minigame.components.MiniGameFrame

/**
 * ミニゲーム用のフルスクリーンオーバーレイ。
 *
 * - 背景のタップは「裏のアプリに触れてしまう」事故を防ぐために吸収する（何もしない）
 * - 実際のゲーム内容は registry（kind -> implementation）で解決する
 */
@Composable
fun MiniGameHostOverlay(
    kind: MiniGameKind,
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }

    val entry = remember(kind) { MiniGameRegistry.resolve(kind) }

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
            if (entry == null) {
                UnknownMiniGame(
                    kind = kind,
                    onFinished = onFinished,
                    modifier = it,
                )
            } else {
                entry.content(seed, onFinished, it)
            }
        }
    }
}

@Composable
private fun UnknownMiniGame(
    kind: MiniGameKind,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "未対応のミニゲームです．",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "kind=$kind",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}
