package com.example.refocus.ui.minigame.games.lightsout

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.refocus.ui.minigame.components.MiniGameHeader
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val GRID_SIZE: Int = 4
private const val CELL_COUNT: Int = GRID_SIZE * GRID_SIZE

private const val SCRAMBLE_MIN: Int = 5
private const val SCRAMBLE_MAX: Int = 9

private enum class LightsOutPhase {
    Playing,
    Solved,
    TimeUp,
}

private val TOGGLE_MASKS: IntArray = buildToggleMasks()

@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialBoard = remember(seed) { generateInitialBoard(seed = seed) }

    var board by remember(seed) { mutableIntStateOf(initialBoard) }
    var moves by remember(seed) { mutableIntStateOf(0) }
    var phase by remember(seed) { mutableStateOf(LightsOutPhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(LIGHTS_OUT_TIME_LIMIT_SECONDS) }

    fun tryMove(index: Int) {
        if (phase != LightsOutPhase.Playing) return
        if (remainingSeconds <= 0) return

        board = board xor TOGGLE_MASKS[index]
        moves += 1

        if (board == 0) {
            phase = LightsOutPhase.Solved
        }
    }

    fun resetBoard() {
        if (phase != LightsOutPhase.Playing) return
        board = initialBoard
        moves = 0
    }

    LaunchedEffect(seed) {
        while (true) {
            if (phase != LightsOutPhase.Playing) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase != LightsOutPhase.Playing) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0 && phase == LightsOutPhase.Playing) {
                phase = LightsOutPhase.TimeUp
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniGameHeader(
            title = "ライツアウト",
            subtitle =
                when (phase) {
                    LightsOutPhase.Playing -> "全消灯を目指す"
                    LightsOutPhase.Solved -> "クリア"
                    LightsOutPhase.TimeUp -> "時間切れ"
                },
            rightTop = "残り ${remainingSeconds.coerceAtLeast(0)}秒",
            rightBottom = "手数 $moves",
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight)
            Board(
                board = board,
                enabled = phase == LightsOutPhase.Playing && remainingSeconds > 0,
                onCellClick = { idx -> tryMove(idx) },
                modifier = Modifier.size(side),
            )
        }

        when (phase) {
            LightsOutPhase.Playing -> {
                OutlinedButton(
                    onClick = { resetBoard() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                ) {
                    Text(text = "リセット")
                }
            }

            LightsOutPhase.Solved,
            LightsOutPhase.TimeUp,
            -> {
                ResultFooter(
                    phase = phase,
                    moves = moves,
                    onFinished = onFinished,
                )
            }
        }
    }
}

@Composable
private fun ResultFooter(
    phase: LightsOutPhase,
    moves: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val message =
            when (phase) {
                LightsOutPhase.Solved -> "クリアしました．"
                LightsOutPhase.TimeUp -> "時間切れです．"
                LightsOutPhase.Playing -> ""
            }

        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "手数: $moves",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Button(
            onClick = onFinished,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}

@Composable
private fun Board(
    board: Int,
    enabled: Boolean,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = 8.dp
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (r in 0 until GRID_SIZE) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (c in 0 until GRID_SIZE) {
                    val idx = r * GRID_SIZE + c
                    val isOn = (board ushr idx) and 1 == 1
                    Cell(
                        isOn = isOn,
                        enabled = enabled,
                        onClick = { onCellClick(idx) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cell(
    isOn: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor =
        if (isOn) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val containerColor by animateColorAsState(targetValue = targetColor, label = "lightsOutCellColor")

    Surface(
        modifier =
            modifier
                .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {}
}

/**
 * 各セルの「押下で反転する集合」をビットマスクで持つ。
 *
 * bit i（0..15）が 1 なら ON とみなす。
 * セル i を押したときの反転は board xor TOGGLE_MASKS[i] で表現できる。
 */
private fun buildToggleMasks(): IntArray {
    fun index(r: Int, c: Int): Int = r * GRID_SIZE + c

    val masks = IntArray(CELL_COUNT)
    for (r in 0 until GRID_SIZE) {
        for (c in 0 until GRID_SIZE) {
            var m = 0
            fun add(rr: Int, cc: Int) {
                if (rr !in 0 until GRID_SIZE) return
                if (cc !in 0 until GRID_SIZE) return
                m = m or (1 shl index(rr, cc))
            }
            add(r, c)
            add(r - 1, c)
            add(r + 1, c)
            add(r, c - 1)
            add(r, c + 1)
            masks[index(r, c)] = m
        }
    }
    return masks
}

/**
 * seed から決定的に「必ず解ける」盤面を生成する。
 *
 * - 初期全消灯（0）からランダム手を N 回適用して作る（可解性を担保）
 * - 連続同一セルを避ける（無意味な相殺を減らす）
 * - 結果が全消灯になった場合は 1 手追加して回避する
 */
private fun generateInitialBoard(seed: Long): Int {
    val rng = Random(seed)
    val scrambleCount = rng.nextInt(from = SCRAMBLE_MIN, until = SCRAMBLE_MAX + 1)

    var board = 0
    var prev = -1

    repeat(scrambleCount) {
        var idx: Int
        var guard = 0
        do {
            idx = rng.nextInt(CELL_COUNT)
            guard += 1
        } while (idx == prev && guard <= 10)

        board = board xor TOGGLE_MASKS[idx]
        prev = idx
    }

    if (board == 0) {
        val idx = ((prev + 1).takeIf { it in 0 until CELL_COUNT }) ?: 0
        board = board xor TOGGLE_MASKS[idx]
    }

    return board
}
