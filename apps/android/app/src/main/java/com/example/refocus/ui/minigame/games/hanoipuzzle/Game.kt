package com.example.refocus.ui.minigame.games.hanoipuzzle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.refocus.ui.minigame.components.MiniGameHeader
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

private const val TIME_LIMIT_SECONDS = 60
private const val BOARD_ASPECT_RATIO = 2.9f

private const val MIN_REQUIRED_MOVES_4 = 6
private const val MIN_REQUIRED_MOVES_5 = 8
private const val TARGET_DISTANCE_4 = 12
private const val TARGET_DISTANCE_5 = 14
private const val DISTANCE_WINDOW = 3

private enum class HanoiPuzzlePhase {
    Playing,
    Solved,
    TimeUp,
}

@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val puzzle: HanoiPuzzleProblem =
        remember(seed) {
            generatePuzzle(seed = seed)
        }

    val diskCount = puzzle.diskCount
    val goalPegOfDisk = puzzle.goalPegOfDisk
    val startPegOfDisk = puzzle.startPegOfDisk

    val currentPegOfDisk: SnapshotStateList<Int> =
        remember(seed) {
            startPegOfDisk.toList().toMutableStateList()
        }

    var selectedPeg by remember(seed) { mutableStateOf<Int?>(null) }
    var moveCount by remember(seed) { mutableIntStateOf(0) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(TIME_LIMIT_SECONDS) }
    var phase by remember(seed) { mutableStateOf(HanoiPuzzlePhase.Playing) }
    var transientMessage by remember(seed) { mutableStateOf<String?>(null) }

    val matchedCount by remember(currentPegOfDisk) {
        derivedStateOf {
            var c = 0
            for (d in 0 until diskCount) {
                if (currentPegOfDisk[d] == goalPegOfDisk[d]) c += 1
            }
            c
        }
    }

    val isSolved by remember(currentPegOfDisk) {
        derivedStateOf {
            matchedCount == diskCount
        }
    }

    LaunchedEffect(seed) {
        while (true) {
            if (phase != HanoiPuzzlePhase.Playing) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase != HanoiPuzzlePhase.Playing) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0 && phase == HanoiPuzzlePhase.Playing) {
                phase = HanoiPuzzlePhase.TimeUp
                selectedPeg = null
            }
        }
    }

    LaunchedEffect(isSolved, phase, remainingSeconds) {
        if (phase != HanoiPuzzlePhase.Playing) return@LaunchedEffect
        if (remainingSeconds <= 0) return@LaunchedEffect
        if (isSolved) {
            phase = HanoiPuzzlePhase.Solved
            selectedPeg = null
        }
    }

    LaunchedEffect(transientMessage) {
        if (transientMessage == null) return@LaunchedEffect
        delay(1_000)
        transientMessage = null
    }

    fun resetBoardKeepTime() {
        for (d in 0 until diskCount) {
            currentPegOfDisk[d] = startPegOfDisk[d]
        }
        selectedPeg = null
        moveCount = 0
        transientMessage = null
    }

    fun topDiskOnPeg(pegIndex: Int): Int? =
        topDiskOnPeg(
            pegOfDisk = currentPegOfDisk,
            diskCount = diskCount,
            pegIndex = pegIndex,
        )

    fun tryMove(fromPeg: Int, toPeg: Int) {
        if (phase != HanoiPuzzlePhase.Playing) return
        if (remainingSeconds <= 0) return
        if (fromPeg == toPeg) return

        val disk = topDiskOnPeg(fromPeg) ?: return
        val toTop = topDiskOnPeg(toPeg)
        val legal = toTop == null || disk < toTop
        if (!legal) {
            transientMessage = "小さい円盤の上には置けません"
            return
        }

        currentPegOfDisk[disk] = toPeg
        moveCount += 1
    }

    val legalDestinations: Set<Int> by remember(currentPegOfDisk, selectedPeg, phase, remainingSeconds) {
        derivedStateOf {
            if (phase != HanoiPuzzlePhase.Playing) return@derivedStateOf emptySet()
            if (remainingSeconds <= 0) return@derivedStateOf emptySet()
            val fromPeg = selectedPeg ?: return@derivedStateOf emptySet()
            val disk = topDiskOnPeg(fromPeg) ?: return@derivedStateOf emptySet()

            buildSet {
                for (toPeg in 0..2) {
                    if (toPeg == fromPeg) continue
                    val toTop = topDiskOnPeg(toPeg)
                    if (toTop == null || disk < toTop) {
                        add(toPeg)
                    }
                }
            }
        }
    }

    fun onPegTap(pegIndex: Int) {
        if (phase != HanoiPuzzlePhase.Playing) return
        if (remainingSeconds <= 0) return

        val currentSelection = selectedPeg
        if (currentSelection == null) {
            // 空柱は選択できない
            if (topDiskOnPeg(pegIndex) == null) return
            selectedPeg = pegIndex
            return
        }

        if (currentSelection == pegIndex) {
            selectedPeg = null
            return
        }

        if (!legalDestinations.contains(pegIndex)) {
            transientMessage = "その柱には移動できません"
            return
        }

        tryMove(fromPeg = currentSelection, toPeg = pegIndex)
        selectedPeg = null
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        MiniGameHeader(
            title = "ハノイの塔",
            subtitle =
                when (phase) {
                    HanoiPuzzlePhase.Playing -> "上の配置と同じにする"
                    HanoiPuzzlePhase.Solved -> "クリア"
                    HanoiPuzzlePhase.TimeUp -> "時間切れ"
                },
            rightTop = formatMmSs(max(0, remainingSeconds)),
            rightBottom = "手数 $moveCount · 一致 $matchedCount/$diskCount",
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TargetSection(
                diskCount = diskCount,
                pegOfDisk = goalPegOfDisk,
                modifier = Modifier.fillMaxWidth(),
            )

            CurrentSection(
                diskCount = diskCount,
                pegOfDisk = currentPegOfDisk,
                selectedPeg = selectedPeg,
                legalDestinations = legalDestinations,
                enabled = phase == HanoiPuzzlePhase.Playing && remainingSeconds > 0,
                onPegTap = ::onPegTap,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (phase == HanoiPuzzlePhase.Playing) {
            PlayingFooter(
                transientMessage = transientMessage,
                onReset = ::resetBoardKeepTime,
            )
        } else {
            ResultFooter(
                phase = phase,
                onFinished = onFinished,
            )
        }
    }

}


@Composable
private fun PlayingFooter(
    transientMessage: String?,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "柱をタップして円盤を選び，別の柱をタップして移動します．",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (transientMessage != null) {
            Text(
                text = transientMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = "リセット")
        }
    }
}

@Composable
private fun TargetSection(
    diskCount: Int,
    pegOfDisk: IntArray,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "目標配置",
        modifier = modifier,
    ) {
        HanoiBoard(
            diskCount = diskCount,
            pegOfDisk = pegOfDisk.asList(),
            enabled = false,
            selectedPeg = null,
            legalDestinations = emptySet(),
            onPegTap = {},
            // 目標と現在は同じ高さに揃え，盤面が画面を占有しないようにする．
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BOARD_ASPECT_RATIO),
        )
    }
}

@Composable
private fun CurrentSection(
    diskCount: Int,
    pegOfDisk: List<Int>,
    selectedPeg: Int?,
    legalDestinations: Set<Int>,
    enabled: Boolean,
    onPegTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "操作盤面",
        modifier = modifier,
    ) {
        HanoiBoard(
            diskCount = diskCount,
            pegOfDisk = pegOfDisk,
            enabled = enabled,
            selectedPeg = selectedPeg,
            legalDestinations = legalDestinations,
            onPegTap = onPegTap,
            // 目標と同じ高さに揃えたうえで，操作は下側で行う．
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BOARD_ASPECT_RATIO),
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        val shape = RoundedCornerShape(14.dp)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier.padding(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun HanoiBoard(
    diskCount: Int,
    pegOfDisk: List<Int>,
    enabled: Boolean,
    selectedPeg: Int?,
    legalDestinations: Set<Int>,
    onPegTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { pegIndex ->
            Peg(
                pegIndex = pegIndex,
                diskCount = diskCount,
                pegOfDisk = pegOfDisk,
                enabled = enabled,
                selected = selectedPeg == pegIndex,
                highlighted = legalDestinations.contains(pegIndex),
                onTap = { onPegTap(pegIndex) },
                // 親 Row の高さ制約（aspectRatio 等）に追従させ，Peg が無制限に伸びないようにする．
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Peg(
    pegIndex: Int,
    diskCount: Int,
    pegOfDisk: List<Int>,
    enabled: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            highlighted -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    val backgroundColor =
        when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
            else -> MaterialTheme.colorScheme.surface
        }

    Column(
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .background(backgroundColor)
                .clickable(enabled = enabled) { onTap() }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val disksOnPeg =
            pegOfDisk
                .withIndex()
                .filter { it.value == pegIndex }
                .map { it.index }
                .sorted() // 小さい順（上→下の順で積む）

        Spacer(modifier = Modifier.weight(1f))

        disksOnPeg.forEach { disk ->
            Disk(
                disk = disk,
                diskCount = diskCount,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun Disk(
    disk: Int,
    diskCount: Int,
    modifier: Modifier = Modifier,
) {
    val minFraction = 0.44f
    val maxFraction = 0.96f
    val t = (disk + 1).toFloat() / diskCount.toFloat()
    val fraction = minFraction + (maxFraction - minFraction) * t

    Surface(
        modifier =
            modifier
                .fillMaxWidth(fraction)
                .height(18.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (disk + 1).toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ResultFooter(
    phase: HanoiPuzzlePhase,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val message =
            when (phase) {
                HanoiPuzzlePhase.Solved -> "目標配置を再現できました．"
                HanoiPuzzlePhase.TimeUp -> "時間切れです．"
                HanoiPuzzlePhase.Playing -> ""
            }

        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}

private data class HanoiPuzzleProblem(
    val diskCount: Int,
    val goalPegOfDisk: IntArray,
    val startPegOfDisk: IntArray,
)

private data class HanoiMove(
    val disk: Int,
    val fromPeg: Int,
    val toPeg: Int,
)

private fun generatePuzzle(seed: Long): HanoiPuzzleProblem {
    val rng = Random(seed)
    val diskCount = if (rng.nextBoolean()) 4 else 5

    val goalSteps = if (diskCount == 4) 8 else 10
    val minRequiredMoves = if (diskCount == 4) MIN_REQUIRED_MOVES_4 else MIN_REQUIRED_MOVES_5
    val preferredDistance = if (diskCount == 4) TARGET_DISTANCE_4 else TARGET_DISTANCE_5

    val allLeft = IntArray(diskCount) { 0 }

    // 目標配置：初期状態からランダム合法手を積んで作る（seed 決定性）
    var goal = allLeft
    var lastMoveForGoal: HanoiMove? = null
    repeat(goalSteps) {
        val next = pickRandomLegalMove(rng, goal, lastMove = lastMoveForGoal)
        goal = applyMove(goal, next)
        lastMoveForGoal = next
    }

    // 目標が単調（全円盤が同一柱）にならないようにする
    // seed 決定性はループ内で同じ rng を進めるため維持される
    var guard = 0
    while (goal.toSet().size < 2 && guard < 20) {
        val next = pickRandomLegalMove(rng, goal, lastMove = lastMoveForGoal)
        goal = applyMove(goal, next)
        lastMoveForGoal = next
        guard += 1
    }

    // 開始配置：目標からの最短距離（最短手数）が一定以上になるように選ぶ
    // BFS で goal から全状態の距離を計算し，距離が [minRequiredMoves, preferredDistance±window] の範囲に入るものを優先する
    val distances = computeDistancesFromGoal(goal)
    val startCode =
        pickStartStateCode(
            rng = rng,
            diskCount = diskCount,
            distances = distances,
            minRequiredMoves = minRequiredMoves,
            preferredDistance = preferredDistance,
        )
    val start = decodeState(startCode, diskCount)

    return HanoiPuzzleProblem(
        diskCount = diskCount,
        goalPegOfDisk = goal,
        startPegOfDisk = start,
    )
}

private fun applyMove(
    pegOfDisk: IntArray,
    move: HanoiMove,
): IntArray {
    val next = pegOfDisk.copyOf()
    next[move.disk] = move.toPeg
    return next
}

private fun pickRandomLegalMove(
    rng: Random,
    pegOfDisk: IntArray,
    lastMove: HanoiMove?,
): HanoiMove {
    val diskCount = pegOfDisk.size
    val tops = IntArray(3) { topDiskOnPeg(pegOfDisk, diskCount, it) ?: -1 }

    val candidates = ArrayList<HanoiMove>(12)
    for (fromPeg in 0..2) {
        val disk = tops[fromPeg]
        if (disk < 0) continue
        for (toPeg in 0..2) {
            if (toPeg == fromPeg) continue
            val toTop = tops[toPeg]
            val legal = toTop < 0 || disk < toTop
            if (!legal) continue

            val move = HanoiMove(disk = disk, fromPeg = fromPeg, toPeg = toPeg)
            if (isImmediateUndo(lastMove, move)) continue
            candidates.add(move)
        }
    }

    // immediate undo の制約で候補が消える場合があるためフォールバック
    if (candidates.isEmpty()) {
        val allCandidates = ArrayList<HanoiMove>(12)
        for (fromPeg in 0..2) {
            val disk = tops[fromPeg]
            if (disk < 0) continue
            for (toPeg in 0..2) {
                if (toPeg == fromPeg) continue
                val toTop = tops[toPeg]
                val legal = toTop < 0 || disk < toTop
                if (!legal) continue
                allCandidates.add(HanoiMove(disk = disk, fromPeg = fromPeg, toPeg = toPeg))
            }
        }
        return allCandidates[rng.nextInt(allCandidates.size)]
    }

    return candidates[rng.nextInt(candidates.size)]
}

private fun isImmediateUndo(
    last: HanoiMove?,
    next: HanoiMove,
): Boolean {
    if (last == null) return false
    return last.disk == next.disk && last.fromPeg == next.toPeg && last.toPeg == next.fromPeg
}

/**
 * pegOfDisk は「円盤 d がどの柱（0..2）にあるか」を表す．
 * 円盤は 0 が最小で，任意の状態は必ず合法（同一柱上ではサイズ順に積まれる）．
 */
private fun topDiskOnPeg(
    pegOfDisk: List<Int>,
    diskCount: Int,
    pegIndex: Int,
): Int? {
    for (d in 0 until diskCount) {
        if (pegOfDisk[d] == pegIndex) return d
    }
    return null
}

private fun topDiskOnPeg(
    pegOfDisk: IntArray,
    diskCount: Int,
    pegIndex: Int,
): Int? {
    for (d in 0 until diskCount) {
        if (pegOfDisk[d] == pegIndex) return d
    }
    return null
}

private fun computeDistancesFromGoal(goalPegOfDisk: IntArray): IntArray {
    val diskCount = goalPegOfDisk.size
    val total = pow3(diskCount)
    val dist = IntArray(total) { -1 }
    val queue = IntArray(total)

    val goalCode = encodeState(goalPegOfDisk)
    dist[goalCode] = 0
    var head = 0
    var tail = 0
    queue[tail++] = goalCode

    while (head < tail) {
        val code = queue[head++]
        val state = decodeState(code, diskCount)
        val baseDist = dist[code]

        val tops = IntArray(3) { topDiskOnPeg(state, diskCount, it) ?: -1 }

        for (fromPeg in 0..2) {
            val disk = tops[fromPeg]
            if (disk < 0) continue
            for (toPeg in 0..2) {
                if (toPeg == fromPeg) continue
                val toTop = tops[toPeg]
                val legal = toTop < 0 || disk < toTop
                if (!legal) continue

                val next = state.copyOf()
                next[disk] = toPeg
                val nextCode = encodeState(next)
                if (dist[nextCode] >= 0) continue
                dist[nextCode] = baseDist + 1
                queue[tail++] = nextCode
            }
        }
    }

    return dist
}

private fun pickStartStateCode(
    rng: Random,
    diskCount: Int,
    distances: IntArray,
    minRequiredMoves: Int,
    preferredDistance: Int,
): Int {
    fun collectCandidates(minD: Int, maxD: Int): IntArray {
        val tmp = IntArray(distances.size)
        var k = 0
        for (code in distances.indices) {
            val d = distances[code]
            if (d < 0) continue
            if (d in minD..maxD) {
                tmp[k++] = code
            }
        }
        return tmp.copyOf(k)
    }

    // まずは「ちょうど良い難度帯」を優先する（preferredDistance±window）
    var minD = max(minRequiredMoves, preferredDistance - DISTANCE_WINDOW)
    var maxD = preferredDistance + DISTANCE_WINDOW

    repeat(3) { _ ->
        val candidates = collectCandidates(minD, maxD)
        if (candidates.isNotEmpty()) {
            return candidates[rng.nextInt(candidates.size)]
        }
        // 見つからない場合は上限を広げていく（下限は minRequiredMoves まで下げる）
        minD = minRequiredMoves
        maxD += 6
    }

    // 最低手数のみ満たすものから選ぶ（距離が大きすぎる可能性があるので，遠すぎる場合は調整推奨）
    run {
        val candidates = ArrayList<Int>(distances.size)
        for (code in distances.indices) {
            val d = distances[code]
            if (d >= minRequiredMoves) candidates.add(code)
        }
        if (candidates.isNotEmpty()) return candidates[rng.nextInt(candidates.size)]
    }

    // フォールバック：最も遠い状態を選ぶ（理論上ここには来ないはず）
    var bestCode = 0
    var bestDist = -1
    for (code in distances.indices) {
        val d = distances[code]
        if (d > bestDist) {
            bestDist = d
            bestCode = code
        }
    }
    return bestCode
}

private fun pow3(n: Int): Int {
    var x = 1
    repeat(n) { x *= 3 }
    return x
}

private fun encodeState(pegOfDisk: IntArray): Int {
    var code = 0
    var base = 1
    for (d in pegOfDisk.indices) {
        code += pegOfDisk[d] * base
        base *= 3
    }
    return code
}

private fun decodeState(code: Int, diskCount: Int): IntArray {
    var x = code
    val pegOfDisk = IntArray(diskCount)
    for (d in 0 until diskCount) {
        pegOfDisk[d] = x % 3
        x /= 3
    }
    return pegOfDisk
}

private fun formatMmSs(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    val mm = if (m < 10) "0$m" else "$m"
    val ss = if (s < 10) "0$s" else "$s"
    return "$mm:$ss"
}
