package com.example.refocus.ui.minigame.games.memoji

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.ui.minigame.components.MiniGameHeader
import kotlinx.coroutines.delay
import kotlin.random.Random

private val EMOJI_POOL =
    listOf(
        "🐶",
        "🐱",
        "🐭",
        "🐹",
        "🐰",
        "🦊",
        "🐻",
        "🐼",
        "🐨",
        "🐯",
        "🦁",
        "🐮",
        "🐷",
        "🐸",
        "🐵",
        "🐔",
        "🐧",
        "🐦",
        "🐤",
        "🦆",
        "🍎",
        "🍊",
        "🍋",
        "🍌",
        "🍉",
        "🍇",
        "🍓",
        "🍈",
        "🍒",
        "🍑",
        "⚽",
        "🏀",
        "🏈",
        "⚾",
        "🎾",
        "🏐",
        "🏉",
        "🎱",
        "🏓",
        "🏸",
        "😀",
        "😂",
        "😎",
        "😍",
        "🤔",
        "😴",
        "🥶",
        "🤯",
        "🥳",
        "🥺",
    )

private const val MEMORIZE_SECONDS = 5
private const val TARGET_COUNT = 5
private const val OPTION_COUNT = 20

private enum class MemojiPhase {
    Memorize,
    Input,
    Result,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {

    // プールの重複が混ざっても挙動が壊れないように，ここで一度ユニーク化する
    val pool = remember { EMOJI_POOL.distinct() }

    // まず選択肢をユニークに構築し，その中から出題（順番）を選ぶ
    // これにより「選択肢内の重複」と「出題と選択肢の不整合」を構造的に防げる
    val options =
        remember(seed) {
            // options 用の RNG を分離し，出題生成の RNG 消費順に依存しないようにする
            // 0x9E3779B97F4A7C15 を符号付き Long として扱う（Kotlin の 0x...L が Long の範囲超過になるため）
            val optionsRng = Random(seed xor (-7046029254386353131L))
            pool.shuffled(optionsRng).take(minOf(OPTION_COUNT, pool.size))
        }

    val targetEmojis =
        remember(seed, options) {
            // target 用の RNG を分離し，options の RNG 消費順に依存しないようにする
            // 0xD1B54A32D192ED03 を符号付き Long として扱う
            val targetRng = Random(seed xor (-3335678366873096957L))
            options.shuffled(targetRng).take(minOf(TARGET_COUNT, options.size))
        }

    var phase by remember(seed) { mutableStateOf(MemojiPhase.Memorize) }
    var timeLeft by remember(seed) { mutableIntStateOf(MEMORIZE_SECONDS) }
    var inputSequence by remember(seed) { mutableStateOf(emptyList<String>()) }
    var isCorrect by remember(seed) { mutableStateOf(false) }

    LaunchedEffect(phase) {
        if (phase != MemojiPhase.Memorize) return@LaunchedEffect
        timeLeft = MEMORIZE_SECONDS
        while (timeLeft > 0) {
            delay(1000L)
            timeLeft -= 1
        }
        phase = MemojiPhase.Input
    }

    LaunchedEffect(phase, inputSequence) {
        if (phase != MemojiPhase.Input) return@LaunchedEffect
        if (inputSequence.size < TARGET_COUNT) return@LaunchedEffect
        isCorrect = inputSequence == targetEmojis
        phase = MemojiPhase.Result
    }

    fun onEmojiClick(emoji: String) {
        if (phase != MemojiPhase.Input) return
        if (inputSequence.size >= TARGET_COUNT) return
        inputSequence = inputSequence + emoji
    }

    fun popLast() {
        if (phase != MemojiPhase.Input) return
        if (inputSequence.isEmpty()) return
        inputSequence = inputSequence.dropLast(1)
    }

    fun resetInput() {
        if (phase != MemojiPhase.Input) return
        inputSequence = emptyList()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiniGameHeader(
            title = "Memoji",
            subtitle =
                when (phase) {
                    MemojiPhase.Memorize -> "5秒で順番を覚えます．"
                    MemojiPhase.Input -> "5回入力したら判定します．"
                    MemojiPhase.Result -> if (isCorrect) "正解" else "不正解"
                },
            rightTop =
                when (phase) {
                    MemojiPhase.Memorize -> "${timeLeft}秒"
                    MemojiPhase.Input -> "${inputSequence.size}/$TARGET_COUNT"
                    MemojiPhase.Result -> "${TARGET_COUNT}/$TARGET_COUNT"
                },
            rightBottom =
                when (phase) {
                    MemojiPhase.Memorize -> "記憶"
                    MemojiPhase.Input -> "入力"
                    MemojiPhase.Result -> "結果"
                },
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                MemojiPhase.Memorize -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "この順番を覚えてください．",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val columns = TARGET_COUNT
                            val spacing = 8.dp

                            val cellSize =
                                ((maxWidth - spacing * (columns - 1).toFloat()) / columns.toFloat())
                                    .coerceAtMost(64.dp)

                            val density = LocalDensity.current
                            // TextUnit は環境によって Comparable ではないため，value(Float) 側で clamp する
                            val emojiFontSizeSp = with(density) { (cellSize * 0.78f).toSp() }
                            val emojiFontSize = emojiFontSizeSp.value.coerceIn(22f, 46f).sp

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                maxItemsInEachRow = columns,
                                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
                            ) {
                                targetEmojis.forEach { emoji ->
                                    Box(
                                        modifier = Modifier.size(cellSize),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = emojiFontSize,
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "あと $timeLeft 秒",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                MemojiPhase.Input -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "見た順番どおりに選んでください．",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        InputPreview(
                            inputSequence = inputSequence,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val columns = 5
                                val rows = (OPTION_COUNT + columns - 1) / columns
                                val spacing = 8.dp

                                val cellSizeFromWidth =
                                    ((maxWidth - spacing * (columns - 1).toFloat()) / columns.toFloat())
                                        .coerceAtLeast(0.dp)

                                val cellSizeFromHeight =
                                    ((maxHeight - spacing * (rows - 1).toFloat()) / rows.toFloat())
                                        .coerceAtLeast(0.dp)

                                val cellSize =
                                    minOf(cellSizeFromWidth, cellSizeFromHeight)
                                        .coerceIn(40.dp, 64.dp)

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    maxItemsInEachRow = columns,
                                    horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
                                ) {
                                    options.forEach { emoji ->
                                        EmojiCell(
                                            emoji = emoji,
                                            size = cellSize,
                                            enabled = inputSequence.size < TARGET_COUNT,
                                            onClick = { onEmojiClick(emoji) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MemojiPhase.Result -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = if (isCorrect) "正解" else "不正解",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (isCorrect) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "正解",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.Center) {
                                targetEmojis.forEach { emoji ->
                                    Text(
                                        text = emoji,
                                        fontSize = 34.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }

                            Text(
                                text = "あなたの入力",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.Center) {
                                inputSequence.forEach { emoji ->
                                    Text(
                                        text = emoji,
                                        fontSize = 34.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "ボタンを押して終了します．",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        when (phase) {
            MemojiPhase.Memorize -> {
                Spacer(Modifier.height(0.dp))
            }

            MemojiPhase.Input -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { popLast() },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                        enabled = inputSequence.isNotEmpty(),
                    ) {
                        Text("一つ戻す")
                    }
                    OutlinedButton(
                        onClick = { resetInput() },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                    ) {
                        Text("リセット")
                    }
                }
            }

            MemojiPhase.Result -> {
                val finishLabel = if (isCorrect) "完了" else "終了"
                Button(
                    onClick = onFinished,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                ) {
                    Text(finishLabel)
                }
            }
        }
    }
}

@Composable
private fun InputPreview(
    inputSequence: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until TARGET_COUNT) {
            val char = inputSequence.getOrNull(i) ?: "❓"
            Text(
                text = char,
                fontSize = 30.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun EmojiCell(
    emoji: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .let {
                    if (enabled) {
                        it.clickable { onClick() }
                    } else {
                        it
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 26.sp,
        )
    }
}

private fun buildOptions(
    rng: Random,
    target: List<String>,
    pool: List<String>,
    optionCount: Int,
): List<String> {
    // optionCount が負の場合でも落ちないように防御する
    val count = optionCount.coerceAtLeast(0)

    if (target.isEmpty()) return pool.shuffled(rng).take(count)
    if (count <= target.size) return target.take(count).shuffled(rng)

    val targetSet = target.toSet()

    // ダミー候補は「target に含まれないもの」から非復元抽出する（重複を防ぐ）
    val dummySource = pool.filterNot { it in targetSet }

    // 候補が足りない場合は，重複を作らずに選べる最大数までに縮める
    val effectiveCount = minOf(count, target.size + dummySource.size)
    val need = effectiveCount - target.size

    val dummies = dummySource.shuffled(rng).take(need)

    // target は必ず全て含める
    return (target + dummies).shuffled(rng)
}
