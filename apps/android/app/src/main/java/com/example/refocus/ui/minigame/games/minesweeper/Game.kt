package com.example.refocus.ui.minigame.games.minesweeper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.ArrayDeque
import kotlin.random.Random

@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val board = remember(seed) { generateBoard(seed = seed) }

    val cells: SnapshotStateList<CellState> =
        remember(seed) {
            List(board.totalCells) { CellState() }.toMutableStateList()
        }

    var mode by remember(seed) { mutableStateOf(MinesweeperInputMode.Reveal) }
    var phase by remember(seed) { mutableStateOf(MinesweeperPhase.Playing) }
    var explodedIndex by remember(seed) { mutableIntStateOf(-1) }

    var remainingSeconds by remember(seed) { mutableIntStateOf(TIME_LIMIT_SECONDS) }
    var revealedSafeCount by remember(seed) { mutableIntStateOf(0) }

    val safeTotal = board.totalCells - board.mineCount

    val flagsPlaced by remember(cells) {
        derivedStateOf { cells.count { it.flagged } }
    }

    fun floodRevealFrom(startIndex: Int): Int {
        if (board.isMine(startIndex)) return 0

        val q = ArrayDeque<Int>()
        q.add(startIndex)

        var opened = 0

        while (q.isNotEmpty()) {
            val idx = q.removeFirst()
            val s = cells[idx]
            if (s.revealed || s.flagged) continue
            if (board.isMine(idx)) continue

            cells[idx] = s.copy(revealed = true)
            opened += 1

            if (board.adjacentCounts[idx] == 0) {
                // 0 のマスは周囲も自動で開く（フラッドフィル）
                for (n in board.neighbors[idx]) {
                    val ns = cells[n]
                    if (!ns.revealed && !ns.flagged && !board.isMine(n)) {
                        q.add(n)
                    }
                }
            }
        }

        return opened
    }

    fun tryOpen(index: Int) {
        if (phase != MinesweeperPhase.Playing) return
        if (remainingSeconds <= 0) return

        val s = cells[index]
        if (s.revealed || s.flagged) return

        if (board.isMine(index)) {
            cells[index] = s.copy(revealed = true)
            explodedIndex = index
            phase = MinesweeperPhase.Exploded
            return
        }

        val newlyOpened = floodRevealFrom(index)
        if (newlyOpened <= 0) return

        revealedSafeCount += newlyOpened
        if (revealedSafeCount >= safeTotal) {
            phase = MinesweeperPhase.Cleared
        }
    }

    fun toggleFlag(index: Int) {
        if (phase != MinesweeperPhase.Playing) return
        if (remainingSeconds <= 0) return

        val s = cells[index]
        if (s.revealed) return

        cells[index] = s.copy(flagged = !s.flagged)
    }

    // 初期状態で 1 マス開く（盤面は seed のみで決定しつつ，初動ストレスを下げる）
    LaunchedEffect(seed) {
        val opened = floodRevealFrom(START_INDEX)
        revealedSafeCount = opened
    }

    LaunchedEffect(seed) {
        while (true) {
            if (phase != MinesweeperPhase.Playing) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase != MinesweeperPhase.Playing) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0 && phase == MinesweeperPhase.Playing) {
                phase = MinesweeperPhase.TimeUp
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(
            phase = phase,
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
            flagsPlaced = flagsPlaced,
            mineCount = board.mineCount,
        )

        ModeBar(
            mode = mode,
            enabled = phase == MinesweeperPhase.Playing && remainingSeconds > 0,
            onSelectReveal = { mode = MinesweeperInputMode.Reveal },
            onSelectFlag = { mode = MinesweeperInputMode.Flag },
        )

        if (phase == MinesweeperPhase.Playing) {
            Text(
                text = "モード切替で操作を変えられます．長押しは常に旗です．",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 盤面が大きくなりすぎるとフッターボタンが見切れるため，利用可能な高さに合わせて縮める
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight)
            Board(
                board = board,
                cells = cells,
                phase = phase,
                explodedIndex = explodedIndex,
                remainingSeconds = remainingSeconds,
                mode = mode,
                onOpen = { idx -> tryOpen(idx) },
                onToggleFlag = { idx -> toggleFlag(idx) },
                modifier = Modifier.size(side),
            )
        }

        if (phase != MinesweeperPhase.Playing) {
            ResultFooter(
                phase = phase,
                onFinished = onFinished,
            )
        }
    }
}

@Composable
private fun Header(
    phase: MinesweeperPhase,
    remainingSeconds: Int,
    flagsPlaced: Int,
    mineCount: Int,
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
                text = "マインスイーパー",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            val subtitle =
                when (phase) {
                    MinesweeperPhase.Playing -> "地雷を避けて開く"
                    MinesweeperPhase.Cleared -> "クリア"
                    MinesweeperPhase.Exploded -> "爆発"
                    MinesweeperPhase.TimeUp -> "時間切れ"
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
                text = "旗 $flagsPlaced / $mineCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeBar(
    mode: MinesweeperInputMode,
    enabled: Boolean,
    onSelectReveal: () -> Unit,
    onSelectFlag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == MinesweeperInputMode.Reveal) {
            Button(
                onClick = onSelectReveal,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text(text = "開く")
            }
        } else {
            OutlinedButton(
                onClick = onSelectReveal,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text(text = "開く")
            }
        }

        if (mode == MinesweeperInputMode.Flag) {
            Button(
                onClick = onSelectFlag,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text(text = "旗")
            }
        } else {
            OutlinedButton(
                onClick = onSelectFlag,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text(text = "旗")
            }
        }
    }
}

@Composable
private fun Board(
    board: BoardSpec,
    cells: SnapshotStateList<CellState>,
    phase: MinesweeperPhase,
    explodedIndex: Int,
    remainingSeconds: Int,
    mode: MinesweeperInputMode,
    onOpen: (index: Int) -> Unit,
    onToggleFlag: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = phase == MinesweeperPhase.Playing && remainingSeconds > 0
    val revealAll = phase != MinesweeperPhase.Playing

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CELL_GAP_DP),
    ) {
        for (r in 0 until board.size) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP),
            ) {
                for (c in 0 until board.size) {
                    val idx = r * board.size + c
                    val state = cells[idx]

                    Cell(
                        state = state,
                        adjacentCount = board.adjacentCounts[idx],
                        isMine = board.isMine(idx),
                        revealAll = revealAll,
                        isExploded = phase == MinesweeperPhase.Exploded && idx == explodedIndex,
                        enabled = enabled,
                        onTap = {
                            when (mode) {
                                MinesweeperInputMode.Reveal -> onOpen(idx)
                                MinesweeperInputMode.Flag -> onToggleFlag(idx)
                            }
                        },
                        onLongPress = { onToggleFlag(idx) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cell(
    state: CellState,
    adjacentCount: Int,
    isMine: Boolean,
    revealAll: Boolean,
    isExploded: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revealed = state.revealed || revealAll

    val showFlagIcon = !revealed && state.flagged

    val bgColor =
        when {
            isExploded -> MaterialTheme.colorScheme.errorContainer
            revealed -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

    val borderColor =
        when {
            isExploded -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }

    val label =
        when {
            revealAll && state.flagged && !isMine -> "×"
            revealed && isMine -> "●"
            revealed && adjacentCount > 0 -> adjacentCount.toString()
            showFlagIcon -> ""
            else -> ""
        }

    val gestureModifier =
        if (enabled && !revealAll) {
            Modifier.pointerInput(state, enabled, revealAll) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            }
        } else {
            Modifier
        }

    Surface(
        modifier = modifier.then(gestureModifier),
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showFlagIcon) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = "旗",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            } else if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ResultFooter(
    phase: MinesweeperPhase,
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
                MinesweeperPhase.Cleared -> "クリアしました．"
                MinesweeperPhase.Exploded -> "地雷を開きました．"
                MinesweeperPhase.TimeUp -> "時間切れです．"
                MinesweeperPhase.Playing -> ""
            }

        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}

private data class BoardSpec(
    val size: Int,
    val mineIndices: Set<Int>,
    val adjacentCounts: IntArray,
    val neighbors: Array<IntArray>,
) {
    val totalCells: Int = size * size
    val mineCount: Int = mineIndices.size

    fun isMine(index: Int): Boolean = mineIndices.contains(index)
}

private data class CellState(
    val revealed: Boolean = false,
    val flagged: Boolean = false,
)

private enum class MinesweeperPhase {
    Playing,
    Cleared,
    Exploded,
    TimeUp,
}

private enum class MinesweeperInputMode {
    Reveal,
    Flag,
}

private fun generateBoard(seed: Long): BoardSpec {
    val rng = Random(seed)

    val total = BOARD_SIZE * BOARD_SIZE
    val candidates = ArrayList<Int>(total)
    for (i in 0 until total) {
        if (i == START_INDEX) continue
        candidates.add(i)
    }

    fisherYatesShuffle(candidates, rng)
    val mines = candidates.take(MINE_COUNT).toSet()

    val neighbors = Array(total) { idx -> computeNeighbors(index = idx, size = BOARD_SIZE) }

    val adjacentCounts = IntArray(total)
    for (i in 0 until total) {
        if (mines.contains(i)) continue
        var count = 0
        for (n in neighbors[i]) {
            if (mines.contains(n)) count += 1
        }
        adjacentCounts[i] = count
    }

    return BoardSpec(
        size = BOARD_SIZE,
        mineIndices = mines,
        adjacentCounts = adjacentCounts,
        neighbors = neighbors,
    )
}

private fun computeNeighbors(
    index: Int,
    size: Int,
): IntArray {
    val r = index / size
    val c = index % size

    val buf = IntArray(8)
    var k = 0

    // dr, dc の走査順を固定することで，フラッドフィルの挙動も安定させる
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr !in 0 until size) continue
            if (nc !in 0 until size) continue
            buf[k] = nr * size + nc
            k += 1
        }
    }

    return buf.copyOf(k)
}

private fun <T> fisherYatesShuffle(
    list: MutableList<T>,
    rng: Random,
) {
    for (i in list.lastIndex downTo 1) {
        val j = rng.nextInt(i + 1)
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

private const val BOARD_SIZE = 4
private const val MINE_COUNT = 4
private const val START_INDEX = 5
private const val TIME_LIMIT_SECONDS = 60

private val CELL_GAP_DP = 8.dp
