package com.example.refocus.system.overlay.ui.minigame

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun FlashAnzanGame(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rng = remember(seed) { Random(seed) }

    // パラメータは将来 Customize へ出しても良いが，まずは固定値で。
    val count = 6
    val min = 10
    val max = 99
    val showMillis = 650L
    val blankMillis = 220L

    val numbers = remember(seed) { List(count) { rng.nextInt(min, max + 1) } }
    val answer = remember(numbers) { numbers.sum() }

    var phase by remember { mutableStateOf(Phase.Ready) }
    var displayIndex by remember { mutableIntStateOf(-1) }
    var input by remember { mutableStateOf("") }
    var isCorrect by remember { mutableStateOf(false) }

    val closeInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(phase) {
        if (phase != Phase.Showing) return@LaunchedEffect

        displayIndex = 0
        while (displayIndex in numbers.indices) {
            delay(showMillis)
            // ちらつき防止のための空白（UI には表示しないが間を作る）
            displayIndex += 1
            if (displayIndex <= numbers.lastIndex) {
                delay(blankMillis)
            }
        }
        phase = Phase.Input
    }

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "フラッシュ暗算",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(8.dp))

        when (phase) {
            Phase.Ready -> {
                Text(
                    text = "表示される数字を足し算して，合計を入力してください．",
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        input = ""
                        displayIndex = -1
                        phase = Phase.Showing
                    },
                ) {
                    Text("スタート")
                }
            }

            Phase.Showing -> {
                val toShow =
                    if (displayIndex in numbers.indices) numbers[displayIndex].toString() else ""
                Text(
                    text = toShow,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "よく見てください．",
                    textAlign = TextAlign.Center,
                )
            }

            Phase.Input -> {
                Text(
                    text = "合計を入力してください．",
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (input.isEmpty()) " " else input,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))

                NumericKeypad(
                    onDigit = { d ->
                        // 先頭ゼロも許容するが，表示だけなのでそのまま
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
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "OK を押すと判定します．",
                    textAlign = TextAlign.Center,
                )
            }

            Phase.Result -> {
                val msg =
                    if (isCorrect) {
                        "正解です．"
                    } else {
                        "不正解です．正解は $answer です．"
                    }
                Text(
                    text = msg,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "タップで閉じる",
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .clickable(
                                indication = null,
                                interactionSource = closeInteraction,
                            ) { onFinished() },
                    textAlign = TextAlign.Center,
                )
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
