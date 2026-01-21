package com.example.refocus.ui.minigame.games.mirrortext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.ui.minigame.components.MiniGameHeader
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TIME_LIMIT_SECONDS = 60

private enum class MirrorTextPhase {
    Playing,
    Solved,
    TimeUp,
}

private val QWERTY_ROWS =
    listOf(
        listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
        listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
        listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M'),
    )

@Composable
private fun QwertyKeyboard(
    onKeyClick: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QWERTY_ROWS.forEachIndexed { index, rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == 1) Spacer(Modifier.weight(0.5f))
                if (index == 2) Spacer(Modifier.weight(1.5f))

                rowKeys.forEach { char ->
                    KeyButton(
                        char = char,
                        enabled = enabled,
                        onClick = { onKeyClick(char) },
                    )
                }

                if (index == 1) Spacer(Modifier.weight(0.5f))
                if (index == 2) Spacer(Modifier.weight(1.5f))
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(
    char: Char,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(48.dp)
                .padding(2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = char.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
        )
    }
}

@Composable
internal fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rng = remember(seed) { Random(seed) }
    val sentenceList =
        listOf(
            "HELLO ANDROID WORLD",
            "JETPACK COMPOSE UI",
            "REFOCUS YOUR MIND",
            "QWERTY KEYBOARD TEST",
            "THE QUICK BROWN FOX",
        )
    val targetSentence = remember(seed) { sentenceList.random(rng) }

    var phase by remember(seed) { mutableStateOf(MirrorTextPhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(TIME_LIMIT_SECONDS) }
    var inputText by remember(seed) { mutableStateOf("") }

    val isCorrect = inputText == targetSentence

    LaunchedEffect(seed) {
        remainingSeconds = TIME_LIMIT_SECONDS
        phase = MirrorTextPhase.Playing
        inputText = ""
    }

    LaunchedEffect(phase, isCorrect) {
        if (phase == MirrorTextPhase.Playing && isCorrect) {
            phase = MirrorTextPhase.Solved
        }
    }

    LaunchedEffect(phase) {
        if (phase != MirrorTextPhase.Playing) return@LaunchedEffect
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
            if (phase != MirrorTextPhase.Playing) return@LaunchedEffect
        }
        if (!isCorrect) {
            phase = MirrorTextPhase.TimeUp
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ★スクロール対応：問題文エリアが圧迫されても大丈夫なようにweightとscrollを設定
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Decode this:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                text = targetSentence,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = -1f }, // 左右反転
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .background(
                        if (isCorrect) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        RoundedCornerShape(4.dp),
                    ).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = inputText,
                    fontSize = 24.sp,
                    color =
                        if (isCorrect) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AutoFitMirroredSentence(
                        text = targetSentence,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (phase == MirrorTextPhase.TimeUp) {
                    Text(
                        text = "正解：$targetSentence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        InputDisplay(
            text = inputText,
            isCorrect = isCorrect,
            enabled = phase == MirrorTextPhase.Playing,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!isCorrect) {
            // キーボード
            QwertyKeyboard(
                onKeyClick = { char ->
                    inputText += char
                },
                modifier = Modifier.wrapContentHeight(),
            )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { inputText += " " },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text("SPACE")
                    }
                    Button(
                        onClick = { if (inputText.isNotEmpty()) inputText = inputText.dropLast(1) },
                        modifier = Modifier.weight(0.6f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("DEL")
                    }
                }
            }

            // ★機能キー（SPACEとDEL）を独立して配置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { inputText += " " },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text("完了")
                }
                Button(
                    onClick = { if (inputText.isNotEmpty()) inputText = inputText.dropLast(1) },
                    modifier =
                        Modifier
                            .weight(0.5f)
                            .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("終了")
                }
            }
        } else {
            Text(
                "CORRECT!",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onFinished,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            ) {
                Text("CLOSE")
            }
        }
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val m = (totalSeconds.coerceAtLeast(0)) / 60
    val s = (totalSeconds.coerceAtLeast(0)) % 60
    return "%d:%02d".format(m, s)
}
