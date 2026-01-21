package com.example.refocus.ui.minigame.games.mirrortext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(10.dp),
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(48.dp)
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .let {
                    if (enabled) {
                        it.clickable(onClick = onClick)
                    } else {
                        it
                    }
                },
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiniGameHeader(
            title = "鏡文字デコード",
            subtitle =
                when (phase) {
                    MirrorTextPhase.Playing -> "反転した文を読み取って入力します．"
                    MirrorTextPhase.Solved -> "正解"
                    MirrorTextPhase.TimeUp -> "時間切れ"
                },
            rightTop = formatSeconds(remainingSeconds.coerceAtLeast(0)),
            rightBottom = "制限 ${formatSeconds(TIME_LIMIT_SECONDS)}",
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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "この文を読み取って入力してください．",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
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

        when (phase) {
            MirrorTextPhase.Playing -> {
                QwertyKeyboard(
                    onKeyClick = { char -> inputText += char },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
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

            MirrorTextPhase.Solved -> {
                Text(
                    text = "正解",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("完了")
                }
            }

            MirrorTextPhase.TimeUp -> {
                Text(
                    text = "時間切れ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("終了")
                }
            }
        }
    }
}

@Composable
private fun AutoFitMirroredSentence(
    text: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()

        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx() }

        val baseStyle =
            MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

        val fontSize =
            remember(text, maxWidthPx, maxHeightPx) {
                if (maxWidthPx <= 0 || maxHeightPx <= 0) return@remember 30.sp

                val constraints = Constraints(maxWidth = maxWidthPx, maxHeight = maxHeightPx)
                val maxSp = 38
                val minSp = 14

                var chosen = minSp
                for (candidate in maxSp downTo minSp) {
                    val candidateSp = candidate.sp
                    val layout =
                        measurer.measure(
                            text = AnnotatedString(text),
                            style =
                                baseStyle.copy(
                                    fontSize = candidateSp,
                                    lineHeight = (candidate * 1.22f).sp,
                                ),
                            constraints = constraints,
                            overflow = TextOverflow.Clip,
                            softWrap = true,
                            maxLines = Int.MAX_VALUE,
                        )

                    if (!layout.hasVisualOverflow && layout.size.height <= maxHeightPx) {
                        chosen = candidate
                        break
                    }
                }

                chosen.sp
            }

        Text(
            text = text,
            style =
                baseStyle.copy(
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.22f).sp,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = -1f },
        )
    }
}

@Composable
private fun InputDisplay(
    text: String,
    isCorrect: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isCorrect -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        }

    Box(
        modifier =
            modifier
                .background(containerColor, RoundedCornerShape(12.dp))
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty()) {
            Text(
                text = "キーをタップして入力します．",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (isCorrect) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val m = (totalSeconds.coerceAtLeast(0)) / 60
    val s = (totalSeconds.coerceAtLeast(0)) % 60
    return "%d:%02d".format(m, s)
}
