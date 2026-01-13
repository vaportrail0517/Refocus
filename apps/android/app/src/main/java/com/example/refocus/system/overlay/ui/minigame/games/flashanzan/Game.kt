package com.example.refocus.system.overlay.ui.minigame.games.flashanzan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.system.overlay.ui.minigame.components.NumericKeypad
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * フラッシュ暗算（最小実装）。
 *
 * 要件：
 * - スタートはユーザ操作（ボタンタップ）
 * - 固定の表示時間で数字を順に表示（残り時間表示なし）
 * - 入力は数字キーパッド
 * - ユーザが回答したら正誤を表示し，タップで閉じる
 * - 正誤などの結果は記録しない（ドメインには返さない）
 */
@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rng = remember(seed) { Random(seed) }

    // 仕様固定（設定には出さない）
    // - 1桁または2桁（1..60）を 5 個
    // - 少し遅めの表示速度
    val count = 5
    val min = 1
    val max = 50
    val showMillis = 1000L
    val blankMillis = 1000L

    val numbers =
        remember(seed, count, min, max) { List(count) { rng.nextInt(min, max + 1) } }
    val answer = remember(numbers) { numbers.sum() }

    var phase by remember { mutableStateOf(Phase.Ready) }
    var displayValue by remember { mutableStateOf<Int?>(null) }
    var shownCount by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var isCorrect by remember { mutableStateOf(false) }

    // 表示フェーズでは，「数字を showMillis だけ表示」→「必ず消す」→（次があれば）空白 blankMillis
    // を繰り返す．同じ数字が連続しても必ず一度消えるため，区切りが分かる．
    LaunchedEffect(phase, numbers) {
        if (phase != Phase.Showing) return@LaunchedEffect

        displayValue = null
        shownCount = 0
        for (i in numbers.indices) {
            displayValue = numbers[i]
            shownCount = i + 1
            delay(showMillis)
            displayValue = null
            if (i != numbers.lastIndex) {
                delay(blankMillis)
            }
        }
        phase = Phase.Input
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Header(
            title = "フラッシュ暗算",
            subtitle =
                when (phase) {
                    Phase.Ready -> "表示される 5 つの数字の合計を計算してください．"
                    Phase.Showing -> ""
                    Phase.Input -> "合計を入力してください．"
                    Phase.Result -> ""
                },
        )

        Spacer(Modifier.height(12.dp))

        ProgressDots(
            total = count,
            filled =
                when (phase) {
                    Phase.Ready -> 0
                    Phase.Showing -> shownCount
                    Phase.Input -> count
                    Phase.Result -> count
                },
        )

        Spacer(Modifier.height(16.dp))

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
                Phase.Ready -> {
                    Text(
                        text = "",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Phase.Showing -> {
                    val toShow = displayValue?.toString() ?: " "
                    Text(
                        text = toShow,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Phase.Input -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "合計",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (input.isEmpty()) " " else input,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Phase.Result -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isCorrect) "正解" else "不正解",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "正解：$answer",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (phase) {
            Phase.Ready -> {
                Button(
                    onClick = {
                        input = ""
                        displayValue = null
                        shownCount = 0
                        phase = Phase.Showing
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                ) {
                    Text("開始")
                }
                Spacer(Modifier.height(12.dp))

                NumericKeypad(
                    onDigit = {},
                    onBackspace = {},
                    onOk = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Phase.Showing -> {
                NumericKeypad(
                    onDigit = {},
                    onBackspace = {},
                    onOk = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Phase.Input -> {
                NumericKeypad(
                    onDigit = { d ->
                        if (input.length < 6) input += d.toString()
                    },
                    onBackspace = {
                        if (input.isNotEmpty()) input = input.dropLast(1)
                    },
                    onOk = {
                        val value = input.toIntOrNull() ?: 0
                        isCorrect = value == answer
                        phase = Phase.Result
                    },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Phase.Result -> {
                Button(
                    onClick = onFinished,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                ) {
                    Text("閉じる")
                }
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProgressDots(
    total: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = 10.dp,
    dotSpacing: Dp = 8.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val isFilled = i < filled
            val base =
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
            Box(
                modifier =
                    if (isFilled) {
                        base.background(MaterialTheme.colorScheme.primary)
                    } else {
                        base.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    },
            )
            if (i != total - 1) {
                Spacer(Modifier.width(dotSpacing))
            }
        }
    }
}

private enum class Phase {
    Ready,
    Showing,
    Input,
    Result,
}
