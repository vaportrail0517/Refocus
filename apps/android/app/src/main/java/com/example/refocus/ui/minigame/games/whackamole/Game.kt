package com.example.refocus.ui.minigame.games.whackamole

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.refocus.ui.minigame.components.MiniGameHeader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TIME_LIMIT_MS: Long = 15_000L
private const val COUNT_IN_MS: Long = 600L
private const val SHOW_MS: Long = 720L

// 厳密に SHOW_MS ちょうどだと，コルーチンのスケジューリング都合で一瞬だけ 3 体見える可能性があるため，安全側に寄せる
private const val OVERLAP_GUARD_MS: Long = 50L

// 15秒の制限時間内で「出現タイミング」を大きくランダム化する
// - gap=0 を許容して，同時（またはほぼ同時）に複数出る状況を作れる
// - gap を大きく取り，1秒以上何も出ない状況も作れる
//
// 出現数は固定せず，TIME_LIMIT_MS 経過で機械的に終了する

private const val MIN_SPAWN_GAP_MS: Long = 0L
private const val MAX_SPAWN_GAP_MS: Long = 2_500L

private const val SHORT_GAP_MAX_MS: Long = 160L
private const val MID_GAP_MIN_MS: Long = 160L
private const val MID_GAP_MAX_MS: Long = 900L
private const val LONG_GAP_MIN_MS: Long = 1_600L
private const val LONG_GAP_MAX_MS: Long = 2_500L

private const val MAX_CONSECUTIVE_ZERO_GAPS: Int = 3

// Kotlin は符号付き Long リテラルの範囲外を許容しないため，2^64 近傍の salt は負数表現で与える
// 0x9E3779B97F4A7C15 (bit pattern) == -7046029254386353131 (signed Long)
private const val GAP_SEED_SALT: Long = -7046029254386353131L

private const val MAX_RETRY_SAME_CELL: Int = 5
private const val HIT_FLASH_MS: Long = 180L

private val GRID_GAP: Dp = 6.dp
private val MIN_CELL_SIZE: Dp = 48.dp

@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameStartAt = remember(seed) { SystemClock.elapsedRealtime() }

    var phase by remember(seed) { mutableStateOf(Phase.CountIn) }
    var aborted by remember(seed) { mutableStateOf(false) }

    // gap を 0 にできるため，同時に複数のセルがアクティブになることがある．
    val activeMolesShownAt = remember(seed) { mutableStateMapOf<Int, Long>() }
    val hitFlashUntilByCell = remember(seed) { mutableStateMapOf<Int, Long>() }

    var hits by remember(seed) { mutableStateOf(0) }
    var totalSpawns by remember(seed) { mutableStateOf(0) }

    val reactionTimesMs = remember(seed) { mutableStateListOf<Long>() }

    var now by remember(seed) { mutableStateOf(gameStartAt) }

    // hitFlashUntilByCell に置き換え（複数同時ヒットを許容）

    val remainingSeconds by remember(seed) {
        derivedStateOf {
            val elapsed = (now - gameStartAt).coerceAtLeast(0L)
            val remaining = (TIME_LIMIT_MS - elapsed).coerceAtLeast(0L)
            ((remaining + 999L) / 1000L).toInt()
        }
    }

    // UI 表示用の時刻更新と，軽いフェイルセーフ（万一スケジュールが崩れても 15 秒で閉じる）
    LaunchedEffect(seed, phase) {
        if (phase == Phase.Result) return@LaunchedEffect
        while (true) {
            val t = SystemClock.elapsedRealtime()
            now = t

            // 複数セルのヒットフラッシュを管理
            if (hitFlashUntilByCell.isNotEmpty()) {
                val expiredKeys = hitFlashUntilByCell.entries
                    .filter { it.value <= t }
                    .map { it.key }
                for (k in expiredKeys) {
                    hitFlashUntilByCell.remove(k)
                }
            }

            if (t - gameStartAt >= TIME_LIMIT_MS && phase != Phase.Result) {
                phase = Phase.Result
            }
            delay(50)
        }
    }

    // 0.6 秒のカウントイン
    LaunchedEffect(seed, phase) {
        if (phase != Phase.CountIn) return@LaunchedEffect
        delay(COUNT_IN_MS)
        if (phase == Phase.CountIn) {
            phase = Phase.Playing
        }
    }

    // 結果画面へ遷移した時点で，描画用の状態をクリアする
    LaunchedEffect(seed, phase) {
        if (phase == Phase.Result) {
            activeMolesShownAt.clear()
            hitFlashUntilByCell.clear()
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val side = minOf(maxWidth, maxHeight)

        val n =
            remember(seed, side) {
                if (side <= 0.dp) 4 else computeBoardSize(availableSide = side, gap = GRID_GAP, minCell = MIN_CELL_SIZE)
            }

        // 15秒経過で機械的に終了するため，出現数は固定しない
        // seed に基づく決定的な RNG で，出現間隔を大きくランダム化する
        LaunchedEffect(seed, phase, n) {
            if (phase != Phase.Playing) return@LaunchedEffect

            // Playing に入ったタイミングで初期化
            activeMolesShownAt.clear()
            hitFlashUntilByCell.clear()
            hits = 0
            totalSpawns = 0
            reactionTimesMs.clear()

            val cells = n * n
            val gapRng = Random(seed xor GAP_SEED_SALT)
            val posRng = Random(seed)
            var prevDesired = -1
            var prevGapMs = SHOW_MS + OVERLAP_GUARD_MS
            var consecutiveZeroGaps = 0

            val deadline = gameStartAt + TIME_LIMIT_MS

            coroutineScope {
                while (phase == Phase.Playing) {
                    val t0 = SystemClock.elapsedRealtime()
                    val remaining = deadline - t0
                    if (remaining <= SHOW_MS) break

                    val gapMs = nextSpawnGapMs(
                        rng = gapRng,
                        prevGapMs = prevGapMs,
                        consecutiveZeroGaps = consecutiveZeroGaps,
                    )

                    // 次の出現までの待ち時間（上限を残り時間で切る）
                    val maxWait = (remaining - SHOW_MS).coerceAtLeast(0L)
                    val waitMs = minOf(gapMs, maxWait)
                    if (waitMs > 0L) delay(waitMs)

                    if (phase != Phase.Playing) break

                    val t1 = SystemClock.elapsedRealtime()
                    if (deadline - t1 <= SHOW_MS) break

                    if (waitMs == 0L) {
                        consecutiveZeroGaps += 1
                    } else {
                        consecutiveZeroGaps = 0
                    }
                    prevGapMs = waitMs

                    val desired = nextDesiredCell(rng = posRng, cells = cells, prev = prevDesired)
                    prevDesired = desired

                    val cell =
                        pickFreeCell(
                            desiredCellIndex = desired,
                            cells = cells,
                            activeMolesShownAt = activeMolesShownAt,
                        )

                    val shownAt = SystemClock.elapsedRealtime()
                    activeMolesShownAt[cell] = shownAt
                    totalSpawns += 1

                    // SHOW_MS 経過で消える（同じセルに新しいモグラが出た場合は誤消去しない）
                    launch {
                        delay(SHOW_MS)
                        if (activeMolesShownAt[cell] == shownAt) {
                            activeMolesShownAt.remove(cell)
                        }
                    }
                }
            }
        }

        val onCellClick: (Int) -> Unit = cellClick@{ index ->
            if (phase != Phase.Playing) return@cellClick

            val shownAt = activeMolesShownAt[index] ?: return@cellClick
            activeMolesShownAt.remove(index)

            hits += 1
            val rt = (SystemClock.elapsedRealtime() - shownAt).coerceAtLeast(0L)
            reactionTimesMs.add(rt)

            hitFlashUntilByCell[index] = SystemClock.elapsedRealtime() + HIT_FLASH_MS
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniGameHeader(
                title = "モグラたたき",
                subtitle =
                    when (phase) {
                        Phase.CountIn -> "準備中"
                        Phase.Playing -> "出てきた丸をタップ"
                        Phase.Result -> "結果"
                    },
                rightTop = "残り ${remainingSeconds}秒",
                rightBottom = "成功 $hits / $totalSpawns",
            )

            Spacer(Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (phase == Phase.Result) {
                    ResultContent(
                        hits = hits,
                        totalSpawns = totalSpawns,
                        aborted = aborted,
                        reactionTimesMs = reactionTimesMs,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MoleGrid(
                        boardSize = n,
                        activeMolesShownAt = activeMolesShownAt,
                        hitFlashUntilByCell = hitFlashUntilByCell,
                        nowMillis = now,
                        onCellClick = onCellClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
            }

            if (phase == Phase.Result) {
                Button(
                    onClick = onFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(text = "閉じる")
                }
            }
//            else {
//                OutlinedButton(
//                    onClick = {
//                        aborted = true
//                        phase = Phase.Result
//                    },
//                    modifier = Modifier.fillMaxWidth(),
//                ) {
//                    Text(text = "中断")
//                }
//            }
        }
    }
}

@Composable
private fun MoleGrid(
    boardSize: Int,
    activeMolesShownAt: Map<Int, Long>,
    hitFlashUntilByCell: Map<Int, Long>,
    nowMillis: Long,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = boardSize * boardSize

    LazyVerticalGrid(
        columns = GridCells.Fixed(boardSize),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
        verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        userScrollEnabled = false,
    ) {
        items(cells) { index ->
            val flashUntil = hitFlashUntilByCell[index] ?: 0L
            HoleCell(
                isActive = activeMolesShownAt.containsKey(index),
                isHitFlash = nowMillis < flashUntil,
                onClick = { onCellClick(index) },
                modifier = Modifier.aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun HoleCell(
    isActive: Boolean,
    isHitFlash: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val holeColor = MaterialTheme.colorScheme.outlineVariant
    val moleColor = MaterialTheme.colorScheme.primary

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val flashStrokeWidth = 4.dp.toPx()

            // 穴（枠）
            drawCircle(
                color = holeColor,
                style = Stroke(width = strokeWidth),
            )

            // モグラ（塗りつぶし）
            if (isActive) {
                drawCircle(
                    color = moleColor,
                    radius = size.minDimension * 0.34f,
                )
            }

            // 成功フラッシュ（リング）
            if (isHitFlash) {
                drawCircle(
                    color = moleColor,
                    radius = size.minDimension * 0.42f,
                    style = Stroke(width = flashStrokeWidth),
                )
            }
        }
    }
}

@Composable
private fun ResultContent(
    hits: Int,
    totalSpawns: Int,
    aborted: Boolean,
    reactionTimesMs: List<Long>,
    modifier: Modifier = Modifier,
) {
    val avgMs: Int? =
        if (reactionTimesMs.isNotEmpty()) {
            (reactionTimesMs.sum().toDouble() / reactionTimesMs.size).roundToInt()
        } else {
            null
        }

    val bestMs: Int? = reactionTimesMs.minOrNull()?.toInt()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (aborted) {
            Text(
                text = "中断しました",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = "成功 $hits / $totalSpawns",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (avgMs != null && bestMs != null) {
            Text(
                text = "平均 ${avgMs}ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "最速 ${bestMs}ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun computeBoardSize(
    availableSide: Dp,
    gap: Dp,
    minCell: Dp,
): Int {
    val cell5 = (availableSide - gap * 4f) / 5f
    return if (cell5 >= minCell) 5 else 4
}

private fun nextSpawnGapMs(
    rng: Random,
    prevGapMs: Long,
    consecutiveZeroGaps: Int,
): Long {
    // 分布（体感重視）
    // - 0ms（ほぼ同時）は少なめに出す（頻発すると難易度が上がる）
    // - LONG は 1600ms 以上を確保して「1秒以上出ない」時間を作れる
    val roll = rng.nextDouble()
    val raw =
        when {
            roll < 0.10 -> 0L
            roll < 0.32 -> rng.nextLong(0L, SHORT_GAP_MAX_MS + 1L)
            roll < 0.72 -> rng.nextLong(MID_GAP_MIN_MS, MID_GAP_MAX_MS + 1L)
            else -> rng.nextLong(LONG_GAP_MIN_MS, LONG_GAP_MAX_MS + 1L)
        }

    val candidate =
        if (raw == 0L && consecutiveZeroGaps >= MAX_CONSECUTIVE_ZERO_GAPS) {
            rng.nextLong(0L, SHORT_GAP_MAX_MS + 1L)
        } else {
            raw
        }

    // 同時出現を最大2体に抑える制約（3連続が詰まりすぎない）
    val need = (SHOW_MS + OVERLAP_GUARD_MS - prevGapMs).coerceAtLeast(0L)

    val enforced = maxOf(candidate, need)
    return enforced.coerceIn(MIN_SPAWN_GAP_MS, MAX_SPAWN_GAP_MS)
}

private fun nextDesiredCell(
    rng: Random,
    cells: Int,
    prev: Int,
): Int {
    if (cells <= 1) return 0

    var next = rng.nextInt(cells)
    var retry = 0
    while (next == prev && retry < MAX_RETRY_SAME_CELL) {
        next = rng.nextInt(cells)
        retry += 1
    }
    if (next == prev) {
        next = (prev + 1) % cells
    }
    return next
}

private fun pickFreeCell(
    desiredCellIndex: Int,
    cells: Int,
    activeMolesShownAt: Map<Int, Long>,
): Int {
    if (cells <= 0) return 0

    var cell = ((desiredCellIndex % cells) + cells) % cells
    var tries = 0
    while (tries < cells && activeMolesShownAt.containsKey(cell)) {
        cell = (cell + 1) % cells
        tries += 1
    }
    return cell
}

private enum class Phase {
    CountIn,
    Playing,
    Result,
}
