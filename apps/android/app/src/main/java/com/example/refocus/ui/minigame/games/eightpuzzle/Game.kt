package com.example.refocus.ui.minigame.games.eightpuzzle

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialTiles: List<Int> =
        remember(seed) {
            generateInitialTiles(seed = seed)
        }

    val tiles: SnapshotStateList<Int> =
        remember(seed) {
            initialTiles.toMutableStateList()
        }

    var blankIndex by remember(seed) { mutableIntStateOf(initialTiles.indexOf(0)) }
    var moveCount by remember(seed) { mutableIntStateOf(0) }

    var phase by remember(seed) { mutableStateOf(EightPuzzlePhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(TIME_LIMIT_SECONDS) }

    var backHintVisible by remember(seed) { mutableStateOf(false) }

    // system overlay（WindowManager + ComposeView）では BackHandler の owner が存在しないことがある．
    // その場合に BackHandler を呼ぶと例外でクラッシュするため，owner があるときだけ登録する．
    // なお，Refocus のミニゲーム overlay は FLAG_NOT_FOCUSABLE のため，そもそも Back を受け取れない端末が多い．
    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    if (backDispatcherOwner != null) {
        // クリアまたは時間切れまでは閉じられない要件により，プレイ中の Back を無効化する．
        BackHandler(enabled = phase == EightPuzzlePhase.Playing) {
            backHintVisible = true
        }
    }

    LaunchedEffect(backHintVisible) {
        if (!backHintVisible) return@LaunchedEffect
        delay(1_800)
        backHintVisible = false
    }

    LaunchedEffect(seed) {
        while (true) {
            if (phase != EightPuzzlePhase.Playing) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase != EightPuzzlePhase.Playing) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0 && phase == EightPuzzlePhase.Playing) {
                phase = EightPuzzlePhase.TimeUp
            }
        }
    }

    val isSolved by remember(tiles) {
        derivedStateOf {
            tiles.size == GOAL_TILES.size && tiles.indices.all { i -> tiles[i] == GOAL_TILES[i] }
        }
    }

    LaunchedEffect(isSolved, phase, remainingSeconds) {
        if (phase != EightPuzzlePhase.Playing) return@LaunchedEffect
        if (remainingSeconds <= 0) return@LaunchedEffect
        if (isSolved) {
            phase = EightPuzzlePhase.Solved
        }
    }

    fun resetBoardKeepTime() {
        tiles.clear()
        tiles.addAll(initialTiles)
        blankIndex = initialTiles.indexOf(0)
        moveCount = 0
    }

    fun tryMove(index: Int) {
        if (phase != EightPuzzlePhase.Playing) return
        if (remainingSeconds <= 0) return
        if (!isAdjacent(index, blankIndex)) return

        val moved = tiles[index]
        tiles[index] = 0
        tiles[blankIndex] = moved
        blankIndex = index
        moveCount += 1
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(
            phase = phase,
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
            moveCount = moveCount,
        )

        Board(
            tiles = tiles,
            enabled = phase == EightPuzzlePhase.Playing && remainingSeconds > 0,
            onTapTile = { idx -> tryMove(idx) },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )

        if (phase == EightPuzzlePhase.Playing) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "空白に隣接するタイルをタップして動かします．",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (backHintVisible) {
                    Text(
                        text = "クリアするか，時間切れになるまで終了できません．",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = { resetBoardKeepTime() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(text = "リセット")
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))

            ResultFooter(
                phase = phase,
                onFinished = onFinished,
            )
        }
    }
}

@Composable
private fun Header(
    phase: EightPuzzlePhase,
    remainingSeconds: Int,
    moveCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "8パズル",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle =
                when (phase) {
                    EightPuzzlePhase.Playing -> "配置を揃える"
                    EightPuzzlePhase.Solved -> "クリア"
                    EightPuzzlePhase.TimeUp -> "時間切れ"
                }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = formatSeconds(remainingSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "手数 $moveCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Board(
    tiles: SnapshotStateList<Int>,
    enabled: Boolean,
    onTapTile: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (r in 0 until 3) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (c in 0 until 3) {
                    val idx = r * 3 + c
                    val v = tiles.getOrNull(idx) ?: 0

                    Tile(
                        value = v,
                        enabled = enabled && v != 0,
                        onClick = { onTapTile(idx) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(
    value: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    if (value == 0) {
        Surface(
            modifier = modifier,
            border = border,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {}
        return
    }

    Surface(
        modifier =
            modifier
                .clickable(enabled = enabled, onClick = onClick),
        border = border,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ResultFooter(
    phase: EightPuzzlePhase,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val message =
            when (phase) {
                EightPuzzlePhase.Solved -> "クリア"
                EightPuzzlePhase.TimeUp -> "時間切れ"
                EightPuzzlePhase.Playing -> ""
            }

        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Button(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "完了")
        }
    }
}

private const val TIME_LIMIT_SECONDS: Int = 60

private val GOAL_TILES: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7, 8, 0)

private fun formatSeconds(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

private fun isAdjacent(
    a: Int,
    b: Int,
): Boolean {
    val ar = a / 3
    val ac = a % 3
    val br = b / 3
    val bc = b % 3
    return abs(ar - br) + abs(ac - bc) == 1
}

private fun generateInitialTiles(seed: Long): List<Int> {
    val rng = Random(seed)

    val tiles = GOAL_TILES.toMutableList()
    var blankIndex = tiles.indexOf(0)

    var prevBlankIndex: Int? = null

    // ゴール状態から合法手を積み重ねることで必ず可解な盤面にする．
    val shuffleMoves = 30 + (abs((seed % 21).toInt())) // 30〜50

    repeat(shuffleMoves) {
        val neighbors = adjacentIndices(blankIndex)
        val candidates =
            prevBlankIndex
                ?.let { p -> neighbors.filter { it != p } }
                ?.takeIf { it.isNotEmpty() }
                ?: neighbors

        val next = candidates[rng.nextInt(candidates.size)]
        val moved = tiles[next]
        tiles[next] = 0
        tiles[blankIndex] = moved

        prevBlankIndex = blankIndex
        blankIndex = next
    }

    // たまたまゴールに戻っていたら，もう1手だけ進める．
    if (tiles == GOAL_TILES) {
        val neighbors = adjacentIndices(blankIndex)
        val next = neighbors[rng.nextInt(neighbors.size)]
        val moved = tiles[next]
        tiles[next] = 0
        tiles[blankIndex] = moved
    }

    return tiles
}

private fun adjacentIndices(index: Int): List<Int> {
    val r = index / 3
    val c = index % 3

    val out = mutableListOf<Int>()
    if (r > 0) out += (r - 1) * 3 + c
    if (r < 2) out += (r + 1) * 3 + c
    if (c > 0) out += r * 3 + (c - 1)
    if (c < 2) out += r * 3 + (c + 1)
    return out
}

private enum class EightPuzzlePhase {
    Playing,
    Solved,
    TimeUp,
}
