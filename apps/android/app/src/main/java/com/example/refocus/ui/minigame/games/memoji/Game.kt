package com.example.refocus.ui.minigame.games.memoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// 絵文字のプール（動物、食べ物、顔など）
private val EMOJI_POOL = listOf(
    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
    "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🦆",
    "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍒", "🍑",
    "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸",
    "😀", "😂", "😎", "😍", "🤔", "😴", "🥶", "🤯", "🥳", "🥺"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rng = remember(seed) { Random(seed) }

    // 問題データの生成
    val targetCount = 5
    val optionCount = 20

    // 正解の絵文字リスト（順番あり）
    val targetEmojis = remember(seed) {
        EMOJI_POOL.shuffled(rng).take(targetCount)
    }

    // 選択肢（正解 + ダミー）をシャッフル
    val options = remember(seed) {
        val dummies = EMOJI_POOL.minus(targetEmojis.toSet())
            .shuffled(rng)
            .take(optionCount - targetCount)
        (targetEmojis + dummies).shuffled(rng)
    }

    // ゲームの状態
    var isMemorizing by remember { mutableStateOf(true) }
    var timeLeft by remember { mutableIntStateOf(5) } // 記憶時間 5秒
    var inputSequence by remember { mutableStateOf(emptyList<String>()) }
    var isError by remember { mutableStateOf(false) }

    // タイマー処理
    LaunchedEffect(isMemorizing) {
        if (isMemorizing) {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            isMemorizing = false // 時間切れで回答フェーズへ
        }
    }

    // 入力判定処理
    fun onEmojiClick(emoji: String) {
        if (isError) {
            // エラー表示中は入力をリセットして再開
            isError = false
            inputSequence = emptyList()
            return
        }

        val nextIndex = inputSequence.size
        // 正しい順番で選べているかチェック
        if (nextIndex < targetEmojis.size && targetEmojis[nextIndex] == emoji) {
            val newInput = inputSequence + emoji
            inputSequence = newInput

            // 全問正解ならクリア
            if (newInput.size == targetEmojis.size) {
                onFinished()
            }
        } else {
            // 間違い
            isError = true
            // 少し待ってリセットするUXも考えられますが、今回はシンプルにユーザーが次タップしたらリセット
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isMemorizing) {
            // --- 記憶フェーズ ---
            Text("あと ${timeLeft} 秒", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))
            Text("この順番を覚えて！", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))

            // 覚えるべき絵文字を横に並べる
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                targetEmojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 40.sp,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

        } else {
            // --- 回答フェーズ ---

            // 現在の入力状況表示
            Text("順番通りにタップ", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        if (isError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isError) {
                    Text(
                        "間違い！タップしてリトライ",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // 入力済みの絵文字 + 未入力のプレースホルダー
                    for (i in 0 until targetCount) {
                        val char = inputSequence.getOrNull(i) ?: "❓"
                        Text(
                            text = char,
                            fontSize = 32.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // 選択肢グリッド (FlowRow)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
                maxItemsInEachRow = 5 // 1行に5つ程度
            ) {
                options.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onEmojiClick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 28.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ギブアップ用
            TextButton(onClick = onFinished) {
                Text("閉じる")
            }
        }
    }
}
