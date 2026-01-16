package com.example.refocus.ui.minigame.games.memoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// 【修正1】表示崩れを防ぐため、確実に表示できる主要な絵文字のみを厳選したリスト
private val SAFE_EMOJI_POOL = listOf(
    // 動物
    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
    "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤", "🦆",
    "🐙", "🐠", "🦀", "🐬", "🐳", "🐊", "🐢", "🦕", "🐘", "🦒",
    // 食べ物
    "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍒", "🍑",
    "🍍", "🥝", "🍅", "🍆", "🥑", "🌽", "🥕", "🍄", "🍞", "🍖",
    "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🍜", "🍝", "🍙", "🍚",
    "🍛", "🍣", "🍱", "🍦", "🍭", "🍫", "🍩", "🍪", "🎂", "🍰",
    // 乗り物・活動
    "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸",
    "🚗", "🚕", "🚙", "🚌", "🚑", "🚓", "🚒", "🚲", "🚀", "✈️",
    "🚁", "🚂", "🚤", "⚓", "⌚", "⏰", "⏳", "💡", "💣", "🎈",
    "🎀", "🎁", "📱", "💻", "📷", "🎥", "📺", "📻", "⏰", "🔑"
)

private data class MemojiGameData(
    val targets: List<String>,
    val options: List<String>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 【修正3】リトライ時に問題を新しくするため、seedを可変にする
    var currentProblemSeed by remember(seed) { mutableLongStateOf(seed) }

    // ゲームデータの生成（seedが変わると再生成される）
    val gameData = remember(currentProblemSeed) {
        val rng = Random(currentProblemSeed)
        val targetCount = 5
        val optionCount = 20

        // 安全なリストから選択
        val targets = SAFE_EMOJI_POOL.shuffled(rng).take(targetCount)
        val dummies = (SAFE_EMOJI_POOL - targets.toSet()).shuffled(rng).take(optionCount - targetCount)
        val options = (targets + dummies).shuffled(rng)

        MemojiGameData(targets, options)
    }

    // 全体の経過時間（問題が変わってもリセットしない）
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // 現在のフェーズ管理
    var isMemorizing by remember(currentProblemSeed) { mutableStateOf(true) }
    var memorizeTimeLeft by remember(currentProblemSeed) { mutableIntStateOf(5) }

    // 入力・判定管理
    var inputSequence by remember(currentProblemSeed) { mutableStateOf(emptyList<String>()) }
    var isError by remember { mutableStateOf(false) } // 「不正解」表示用
    var isSuccess by remember { mutableStateOf(false) }

    // 定数
    val giveUpThreshold = 60 // 60秒後に終了可能

    // 全体タイマー（1秒ごとにカウントアップ）
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    // 記憶フェーズのカウントダウン
    LaunchedEffect(isMemorizing, currentProblemSeed) {
        if (isMemorizing) {
            while (memorizeTimeLeft > 0) {
                delay(1000L)
                memorizeTimeLeft--
            }
            isMemorizing = false // 回答フェーズへ
        }
    }

    // エラー表示後のリトライ処理
    LaunchedEffect(isError) {
        if (isError) {
            delay(1500L) // 1.5秒だけ「残念でした」を表示
            // 【修正3】新しい問題にしてリトライ
            isError = false
            currentProblemSeed = Random.nextLong()
        }
    }

    fun onEmojiClick(emoji: String) {
        if (isError || isSuccess || isMemorizing) return
        // 5個入力し終わるまでは判定しない
        if (inputSequence.size < 5) {
            inputSequence = inputSequence + emoji

            // 5個入力完了時に判定
            if (inputSequence.size == 5) {
                if (inputSequence == gameData.targets) {
                    isSuccess = true
                } else {
                    isError = true
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSuccess) {
            // --- 成功画面 ---
            Text(
                "Excellent!",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Row {
                inputSequence.forEach { emoji ->
                    Text(emoji, fontSize = 32.sp, modifier = Modifier.padding(4.dp))
                }
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("閉じる")
            }
        } else if (isMemorizing) {
            // --- 記憶フェーズ ---
            Text("あと ${memorizeTimeLeft} 秒", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))
            Text("この順番を覚えて！", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                gameData.targets.forEach { emoji ->
                    Text(emoji, fontSize = 40.sp, modifier = Modifier.padding(4.dp))
                }
            }
            // 記憶中も「見切れる」のを防ぐためダミーのスペーサーを入れるか、中央寄せで対応
        } else {
            // --- 回答フェーズ ---

            // 【修正2】スクロール可能な領域を作成して、下のボタンが見切れるのを防ぐ
            Column(
                modifier = Modifier
                    .weight(1f) // 画面の余った領域を使う
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 上部ステータス
                if (elapsedSeconds < giveUpThreshold) {
                    Text("Time: ${elapsedSeconds}s", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                } else {
                    Text("Time: ${elapsedSeconds}s (終了可能)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(16.dp))

                // 入力欄
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
                            "不正解...新しい問題へ",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        // 5枠表示
                        for (i in 0 until 5) {
                            val char = inputSequence.getOrNull(i) ?: "❓"
                            Text(
                                text = char,
                                fontSize = 32.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                // 選択肢を削除するボタン（入力中のみ表示）
                if (!isError && inputSequence.isNotEmpty()) {
                    TextButton(onClick = {
                        inputSequence = emptyList()
                    }) {
                        Text("入力をクリア")
                    }
                } else {
                    Spacer(Modifier.height(48.dp)) // ボタン分の高さ確保
                }

                // 選択肢一覧
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.Center,
                    maxItemsInEachRow = 5
                ) {
                    gameData.options.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(56.dp) // 少し小さくして画面収まりを良くする
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onEmojiClick(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 26.sp)
                        }
                    }
                }
            } // Scrollable Column End

            Spacer(Modifier.height(8.dp))

            // --- 終了ボタン（下部に固定） ---
            if (elapsedSeconds >= giveUpThreshold) {
                Button(
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("ギブアップ（閉じる）")
                }
            } else {
                // プレースホルダー（レイアウトがガタつかないように）
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "あと ${giveUpThreshold - elapsedSeconds}秒で終了可能",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}
