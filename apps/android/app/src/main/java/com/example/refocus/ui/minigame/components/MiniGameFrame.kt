package com.example.refocus.ui.minigame.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ミニゲーム画面のサイズと余白を統一するための共通フレーム。
 *
 * - 端末サイズに応じて一定比率でカードサイズを決める
 * - 上限，下限を設けて，ゲームごとに大きさが変わらないようにする
 */
@Composable
fun MiniGameFrame(
    contentPadding: Dp = 20.dp,
    modifier: Modifier = Modifier,
    content: @Composable (contentModifier: Modifier) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val targetWidth =
            minOf(380.dp, maxWidth * 0.92f)
                .coerceAtLeast(280.dp)
                .coerceAtMost(maxWidth)

        val targetHeight =
            minOf(560.dp, maxHeight * 0.72f)
                .coerceAtLeast(380.dp)
                .coerceAtMost(maxHeight)

        Card(
            modifier = Modifier.width(targetWidth).height(targetHeight),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                content(Modifier.fillMaxSize())
            }
        }
    }
}
