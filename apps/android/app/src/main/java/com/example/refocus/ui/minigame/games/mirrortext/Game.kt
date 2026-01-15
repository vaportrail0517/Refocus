package com.example.refocus.ui.minigame.games.mirrortext

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// --- QWERTYキーボードの定義 ---

private val QWERTY_ROWS = listOf(
    listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
    listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
    listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M')
)

@Composable
private fun QwertyKeyboard(
    onKeyClick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QWERTY_ROWS.forEachIndexed { index, rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 1) Spacer(Modifier.weight(0.5f))
                if (index == 2) Spacer(Modifier.weight(1.5f))

                rowKeys.forEach { char ->
                    KeyButton(char = char, onClick = { onKeyClick(char) })
                }

                if (index == 1) Spacer(Modifier.weight(0.5f))
                if (index == 2) Spacer(Modifier.weight(1.5f))
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(char: Char, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = char.toString(), fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

// --- ゲーム本体の実装 ---

@Composable
internal fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rng = remember(seed) { Random(seed) }
    val sentenceList = listOf(
        "HELLO ANDROID WORLD",
        "JETPACK COMPOSE UI",
        "REFOCUS YOUR MIND",
        "QWERTY KEYBOARD TEST",
        "THE QUICK BROWN FOX"
    )
    val targetSentence = remember(seed) { sentenceList.random(rng) }

    var inputText by remember(seed) { mutableStateOf("") }
    val isCorrect = inputText == targetSentence

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ★スクロール対応：問題文エリアが圧迫されても大丈夫なようにweightとscrollを設定
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Decode this:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                text = targetSentence,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = -1f } // 左右反転
            )
        }

        // 入力結果表示エリア
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .background(
                    if (isCorrect) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(4.dp)
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (inputText.isEmpty()) {
                Text("Tap keys to type...", color = Color.Gray)
            } else {
                Text(
                    text = inputText,
                    fontSize = 24.sp,
                    color = if (isCorrect) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!isCorrect) {
            // キーボード
            QwertyKeyboard(
                onKeyClick = { char ->
                    inputText += char
                },
                modifier = Modifier.wrapContentHeight()
            )

            Spacer(Modifier.height(8.dp))

            // ★機能キー（SPACEとDEL）を独立して配置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { inputText += " " },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text("SPACE")
                }
                Button(
                    onClick = { if (inputText.isNotEmpty()) inputText = inputText.dropLast(1) },
                    modifier = Modifier.weight(0.5f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DEL")
                }
            }

        } else {
            // クリア画面
            Text(
                "CORRECT!",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("CLOSE")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
