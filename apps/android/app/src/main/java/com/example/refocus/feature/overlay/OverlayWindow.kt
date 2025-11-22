package com.example.refocus.feature.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


enum class OverlayColorMode {
    SingleColor,
    Threshold,
    Gradient
}

@Composable
fun OverlayTimerBubble(
    modifier: Modifier = Modifier,
    settings: OverlaySettings,
    // SessionManager から経過時間をもらうための provider
    elapsedMillisProvider: (Long) -> Long
) {
    val timeSource: TimeSource = remember { SystemTimeSource() }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    // 1 秒ごとに SessionManager に「今の経過時間」を問い合わせる
    LaunchedEffect(Unit) {
        while (true) {
            val nowElapsed = timeSource.elapsedRealtime()
            elapsedMillis = elapsedMillisProvider(nowElapsed)
            delay(1000L)
        }
    }
    val elapsedMinutes = elapsedMillis / 1000f / 60f
    val progress = if (settings.timeToMaxMinutes > 0) {
        (elapsedMinutes / settings.timeToMaxMinutes).coerceIn(0f, 1f)
    } else {
        1f
    }
    val fontSizeSp = settings.minFontSizeSp +
            (settings.maxFontSizeSp - settings.minFontSizeSp) * progress
    Box(
        modifier = modifier
            .alpha(0.9f)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = formatDuration(elapsedMillis),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSizeSp.sp
        )
    }
}

// 00:00 / 12:34 / 1:23:45 みたいな表記
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

enum class SuggestionOverlayMode {
    Goal,   // 「やりたいこと」モード
    Rest    // 「休憩」モード
}

@Composable
fun SuggestionOverlay(
    title: String,
    mode: SuggestionOverlayMode,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 8_000L,
    interactionLockoutMillis: Long = 400L,
    onSnoozeLater: () -> Unit,
    onDisableThisSession: () -> Unit,
    onDismissOnly: () -> Unit,
) {
    val headerText: String
    val labelText: String
    val bodyText: String

    when (mode) {
        SuggestionOverlayMode.Goal -> {
            // 「一息つく」より「これをやってみよう」に寄せる
            headerText = "そろそろ、これをやってみませんか？"
            labelText = "やりたいこと"
            bodyText = "このまま続ける前に、一度やりたいことに時間を使ってみるのもおすすめです。"
        }

        SuggestionOverlayMode.Rest -> {
            // ヘッダは「休憩のきっかけ」っぽく、タイトルとは別の役割にする
            headerText = "集中してきたので、ひと休みしませんか？"
            labelText = "休憩の提案"
            bodyText =
                "画面から少し離れて、肩や首を軽く伸ばしたり、水分補給をしてみるのもおすすめです。"
        }
    }

    // 一定時間後に自動で閉じる
    LaunchedEffect(Unit) {
        delay(autoDismissMillis)
        onDismissOnly()
    }

    // 表示直後の誤タップ／誤スワイプを防ぐためのロックアウト
    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(interactionLockoutMillis) {
        delay(interactionLockoutMillis)
        interactive = true
    }

    val cardOffset = remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .offset {
                    IntOffset(
                        cardOffset.value.x.roundToInt(),
                        cardOffset.value.y.roundToInt()
                    )
                }
                // ★ カードだけがスワイプで消える
                .pointerInput(interactive) {
                    // interactive が変わるたびにブロックが再登録される
                    detectDragGestures(
                        onDragEnd = {
                            if (!interactive) {
                                // ロックアウト中は何もせず、その場に留める
                                cardOffset.value = Offset.Zero
                                return@detectDragGestures
                            }
                            val distance = cardOffset.value.getDistance()
                            val threshold = 200f // この距離以上スワイプで消える
                            if (distance > threshold) {
                                onDismissOnly()
                            } else {
                                cardOffset.value = Offset.Zero
                            }
                        },
                        onDragCancel = {
                            // キャンセル時は元の位置に戻す
                            cardOffset.value = Offset.Zero
                        }
                    ) { change, dragAmount ->
                        if (!interactive) {
                            // ロックアウト中はドラッグを一切反映しない
                            change.consume()
                            return@detectDragGestures
                        }
                        change.consume()
                        cardOffset.value += dragAmount
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(min = 260.dp)
            ) {
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            if (interactive) {
                                onSnoozeLater()
                            }
                        },
                        enabled = interactive
                    ) {
                        Text("また後で")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (interactive) {
                                onDisableThisSession()
                            }
                        },
                        enabled = interactive
                    ) {
                        Text("このセッション中は再度提案しない")
                    }
                }
            }
        }
    }
}
