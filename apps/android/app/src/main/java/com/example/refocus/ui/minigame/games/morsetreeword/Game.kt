package com.example.refocus.ui.minigame.games.morsetreeword

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.ui.minigame.components.MiniGameHeader
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TIME_LIMIT_SECONDS = 60
private const val MAX_TREE_DEPTH = 4

private const val MORSE_GROUP_SEPARATOR = "  "

private enum class Phase {
    Playing,
    Cleared,
    TimeUp,
}

private enum class MorseSignal(val codeChar: Char) {
    Dot('.'),
    Dash('-'),
}

private val WORDS: List<String> =
    listOf(
        "REST",
        "READ",
        "WALK",
        "MOVE",
        "PLAN",
        "STOP",
        "TASK",
        "NEXT",
        "NOTE",
        "DEEP",
        "CALM",
        "EASY",
        "DONE",
        "MIND",
        "SAFE",
        "GOAL",
        "STEP",
        "TURN",
        "WORK",
        "CODE",
        "LIST",
        "LOOK",
        "SEEK",
        "PLAY",
        "WAIT",
        "SLOW",
        "TIME",
        "SELF",
        "PACE",
        "PAST",
        "TODO",
        "MAKE",
        "KEEP",
        "HOLD",
        "PUSH",
        "PULL",
        "OPEN",
        "SYNC",
        "BOOK",
        "WORD",
        "DATA",
        "MATH",
        "STUD",
        "TEST",
        "QUIZ",
        "IDEA",
        "SING",
        "TUNE",
        "COOL",
        "WARM",
        "LIFE",
        "KIND",
        "HELP",
        "CARE",
    )

private val CODE_BY_LETTER: Map<Char, String> =
    mapOf(
        'A' to ".-",
        'B' to "-...",
        'C' to "-.-.",
        'D' to "-..",
        'E' to ".",
        'F' to "..-.",
        'G' to "--.",
        'H' to "....",
        'I' to "..",
        'J' to ".---",
        'K' to "-.-",
        'L' to ".-..",
        'M' to "--",
        'N' to "-.",
        'O' to "---",
        'P' to ".--.",
        'Q' to "--.-",
        'R' to ".-.",
        'S' to "...",
        'T' to "-",
        'U' to "..-",
        'V' to "...-",
        'W' to ".--",
        'X' to "-..-",
        'Y' to "-.--",
        'Z' to "--..",
    )

private val LETTER_BY_CODE: Map<String, Char> =
    CODE_BY_LETTER.entries.associate { (k, v) -> v to k }

private val ALL_CODES: List<String> = CODE_BY_LETTER.values.toList()


private fun toCodeString(path: List<MorseSignal>): String =
    path.joinToString(separator = "") { it.codeChar.toString() }

private fun hasAnyCodeWithPrefix(prefix: String): Boolean =
    ALL_CODES.any { it.startsWith(prefix) }


@Composable
private fun MorseSignalGlyph(
    signal: MorseSignal,
    glyphSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(glyphSize),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (signal) {
            MorseSignal.Dot -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.12f,
                    center = Offset(cx, cy),
                )
            }

            MorseSignal.Dash -> {
                val x0 = size.width * 0.22f
                val x1 = size.width * 0.78f
                drawLine(
                    color = color,
                    start = Offset(x0, cy),
                    end = Offset(x1, cy),
                    strokeWidth = size.minDimension * 0.18f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}


@Composable
private fun MorseInputLine(
    committedLetters: String,
    currentPath: List<MorseSignal>,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val currentColor = MaterialTheme.colorScheme.primary

    val committedCodes = committedLetters.mapNotNull { CODE_BY_LETTER[it] }
    val currentCode = toCodeString(currentPath)

    val display = buildAnnotatedString {
        if (committedCodes.isEmpty() && currentCode.isEmpty()) {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append("-")
            }
            return@buildAnnotatedString
        }

        committedCodes.forEachIndexed { idx, code ->
            if (idx > 0) append(MORSE_GROUP_SEPARATOR)
            withStyle(SpanStyle(color = baseColor)) {
                append(code)
            }
        }

        if (currentCode.isNotEmpty()) {
            if (committedCodes.isNotEmpty()) append(MORSE_GROUP_SEPARATOR)
            withStyle(SpanStyle(color = currentColor)) {
                append(currentCode)
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "入力:",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = display,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            softWrap = true,
        )
    }
}


@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rng = remember(seed) { Random(seed) }
    val targetWord = remember(seed) { WORDS.random(rng) }

    var phase by remember(seed) { mutableStateOf(Phase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(TIME_LIMIT_SECONDS) }

    var charIndex by remember(seed) { mutableIntStateOf(0) }
    var path by remember(seed, charIndex) { mutableStateOf(emptyList<MorseSignal>()) }

    LaunchedEffect(seed, phase) {
        if (phase != Phase.Playing) return@LaunchedEffect

        remainingSeconds = TIME_LIMIT_SECONDS
        while (phase == Phase.Playing && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
        if (phase == Phase.Playing && remainingSeconds <= 0) {
            phase = Phase.TimeUp
        }
    }

    fun resetToRoot() {
        path = emptyList()
    }

    fun advanceIfMatched(letter: Char?) {
        val target = targetWord.getOrNull(charIndex) ?: return
        if (letter == target) {
            if (charIndex >= targetWord.lastIndex) {
                phase = Phase.Cleared
            } else {
                charIndex += 1
                resetToRoot()
            }
        }
    }

    fun tryStep(signal: MorseSignal) {
        if (phase != Phase.Playing) return

        val nextPath = path + signal
        val nextCode = toCodeString(nextPath)
        if (!hasAnyCodeWithPrefix(nextCode)) return

        path = nextPath
        advanceIfMatched(LETTER_BY_CODE[nextCode])
    }

    val pathCode = toCodeString(path)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniGameHeader(
            title = "モールスツリー",
            subtitle = "樹形図を辿って単語を完成",
            rightTop = "${remainingSeconds}s",
            rightBottom = "${charIndex + 1}/${targetWord.length}",
        )

        when (phase) {
            Phase.Playing -> {
                WordPrompt(
                    word = targetWord,
                    currentIndex = charIndex,
                    modifier = Modifier.fillMaxWidth(),
                )

                // MiniGameFrame の高さ制約（最大 560dp, 最小 380dp）内に収めるため，
                // 樹形図部分を「残り領域にフィット」させ，必要なら縦スクロール可能にする．
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                ) {
                    val scroll = rememberScrollState()

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scroll)
                                    .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MorseInputLine(
                                committedLetters = targetWord.take(charIndex),
                                currentPath = path,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "左が点，右が線",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MorseTreeDiagram(
                                highlightCode = pathCode,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                ControlRow(
                    canDot = hasAnyCodeWithPrefix(pathCode + "."),
                    canDash = hasAnyCodeWithPrefix(pathCode + "-"),
                    canBack = path.isNotEmpty(),
                    onDot = { tryStep(MorseSignal.Dot) },
                    onDash = { tryStep(MorseSignal.Dash) },
                    onBack = {
                        if (path.isNotEmpty()) {
                            path = path.dropLast(1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Phase.Cleared -> {
                ResultScreen(
                    title = "クリア",
                    detail = "${targetWord} を完成しました",
                    onFinished = onFinished,
                    headerContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            WordPrompt(
                                word = targetWord,
                                currentIndex = -1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MorseInputLine(
                                committedLetters = targetWord,
                                currentPath = emptyList(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Phase.TimeUp -> {
                ResultScreen(
                    title = "時間切れ",
                    detail = "もう一度やる場合はミニゲームを再起動してください",
                    onFinished = onFinished,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun WordPrompt(
    word: String,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        word.forEachIndexed { index, c ->
            val isCurrent = index == currentIndex
            val bg =
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            val border =
                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(bg, RoundedCornerShape(10.dp))
                        .border(1.dp, border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = c.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ControlRow(
    canDot: Boolean,
    canDash: Boolean,
    canBack: Boolean,
    onDot: () -> Unit,
    onDash: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onDot,
            enabled = canDot,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            MorseSignalGlyph(
                signal = MorseSignal.Dot,
                glyphSize = 20.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Button(
            onClick = onDash,
            enabled = canDash,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            MorseSignalGlyph(
                signal = MorseSignal.Dash,
                glyphSize = 20.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        OutlinedButton(
            onClick = onBack,
            enabled = canBack,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "戻る",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MorseTreeDiagram(
    highlightCode: String,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current

    // highlightCode の prefix（ルート含む）をパスとしてハイライトする．例: ".-" -> ["", ".", ".-"]
    val highlightPrefixes =
        remember(highlightCode) {
            buildList {
                add("")
                var acc = ""
                for (c in highlightCode) {
                    acc += c
                    add(acc)
                }
            }
        }

    // (parentCode, childCode) の集合としてハイライト対象エッジを作る．
    val highlightEdges =
        remember(highlightCode) {
            buildSet {
                var acc = ""
                for (c in highlightCode) {
                    val next = acc + c
                    add(acc to next)
                    acc = next
                }
            }
        }

    val maxDepth = MAX_TREE_DEPTH
    val maxLeafCount = 1 shl maxDepth

    // 視認性優先でノードサイズを固定し，横スクロールで表示する．
    val nodeSize = 32.dp
    val nodeRadius = nodeSize / 2f

    val leafSlot = 36.dp
    val sidePad = 10.dp
    val topPad = 10.dp
    val bottomPad = 10.dp
    val levelGap = 40.dp

    val treeWidth = sidePad * 2 + leafSlot * maxLeafCount
    val treeHeight = topPad + nodeSize + levelGap * maxDepth + bottomPad

    val labelFontSize = 15.sp

    BoxWithConstraints(modifier = modifier) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val contentWidthPx = with(density) { treeWidth.toPx() }

        val nodeRadiusPx = with(density) { nodeRadius.toPx() }
        val topPadPx = with(density) { topPad.toPx() }
        val levelGapPx = with(density) { levelGap.toPx() }
        val sidePadPx = with(density) { sidePad.toPx() }
        val leafSlotPx = with(density) { leafSlot.toPx() }

        fun centerX(depth: Int, index: Int): Float {
            // 深さ depth のノードは 2^depth 個．末端（深さ maxDepth）は leafSlot * 16 の幅を持ち，
            // それを depth のノード数に応じて均等に割り当てる．
            val count = 1 shl depth
            val step = (leafSlotPx * maxLeafCount) / count.toFloat()
            return sidePadPx + step * (index + 0.5f)
        }

        fun centerY(depth: Int): Float = topPadPx + nodeRadiusPx + depth * levelGapPx

        val currentDepth = highlightCode.length.coerceIn(0, maxDepth)
        val currentIndex = indexForCode(highlightCode)

        // Canvas の draw ブロックは @Composable ではないため，テーマ色は外側で確定させる．
        val baseColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
        val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)

        val scrollState = rememberScrollState()

        // 現在位置が見えるように，必要なら横スクロール位置を自動調整する．
        LaunchedEffect(highlightCode, maxWidth) {
            if (contentWidthPx <= viewportWidthPx) {
                scrollState.scrollTo(0)
            } else {
                val cx = centerX(currentDepth, currentIndex)
                val desired =
                    (cx - viewportWidthPx / 2f).coerceIn(
                        0f,
                        (contentWidthPx - viewportWidthPx).coerceAtLeast(0f),
                    )
                scrollState.animateScrollTo(desired.toInt())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Box(
                modifier = Modifier
                    .width(treeWidth)
                    .height(treeHeight),
            ) {
                Canvas(
                    modifier = Modifier.matchParentSize(),
                ) {
                    val baseStroke = with(density) { 1.6.dp.toPx() }
                    val highlightStroke = with(density) { 3.2.dp.toPx() }

                    fun drawEdge(
                        parentDepth: Int,
                        parentIndex: Int,
                        childDepth: Int,
                        childIndex: Int,
                        parentCode: String,
                        childCode: String,
                    ) {
                        val px0 = centerX(parentDepth, parentIndex)
                        val py0 = centerY(parentDepth)
                        val px1 = centerX(childDepth, childIndex)
                        val py1 = centerY(childDepth)

                        val dx = px1 - px0
                        val dy = py1 - py0
                        val dist =
                            kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
                        val ux = dx / dist
                        val uy = dy / dist

                        // ノード中心同士ではなく「円の外周」同士を結ぶように補正する（線が文字を突き抜けにくい）．
                        val start =
                            Offset(
                                px0 + ux * nodeRadiusPx,
                                py0 + uy * nodeRadiusPx,
                            )
                        val end =
                            Offset(
                                px1 - ux * nodeRadiusPx,
                                py1 - uy * nodeRadiusPx,
                            )

                        drawLine(
                            color = baseColor,
                            start = start,
                            end = end,
                            strokeWidth = baseStroke,
                        )
                        if ((parentCode to childCode) in highlightEdges) {
                            drawLine(
                                color = highlightColor,
                                start = start,
                                end = end,
                                strokeWidth = highlightStroke,
                            )
                        }
                    }

                    for (depth in 0 until maxDepth) {
                        val parentCount = 1 shl depth
                        for (p in 0 until parentCount) {
                            val parentCode = codeFor(depth, p)
                            drawEdge(
                                parentDepth = depth,
                                parentIndex = p,
                                childDepth = depth + 1,
                                childIndex = p * 2,
                                parentCode = parentCode,
                                childCode = parentCode + ".",
                            )
                            drawEdge(
                                parentDepth = depth,
                                parentIndex = p,
                                childDepth = depth + 1,
                                childIndex = p * 2 + 1,
                                parentCode = parentCode,
                                childCode = parentCode + "-",
                            )
                        }
                    }
                }


                // ノードは Canvas の上に Compose コンポーネントとして重ねる（文字描画の簡便さ優先）．
                for (depth in 0..maxDepth) {
                    val count = 1 shl depth
                    for (i in 0 until count) {
                        val code = codeFor(depth, i)
                        val letter = LETTER_BY_CODE[code]

                        val isCurrent = code == highlightCode
                        val isOnPath = code in highlightPrefixes

                        val cx = centerX(depth, i)
                        val cy = centerY(depth)

                        val xDp = with(density) { (cx - nodeRadiusPx).toDp() }
                        val yDp = with(density) { (cy - nodeRadiusPx).toDp() }

                        TreeNodeBubble(
                            letter = letter,
                            isCurrent = isCurrent,
                            isOnPath = isOnPath,
                            nodeSize = nodeSize,
                            labelFontSize = labelFontSize,
                            modifier = Modifier.offset(xDp, yDp),
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TreeNodeBubble(
    letter: Char?,
    isCurrent: Boolean,
    isOnPath: Boolean,
    nodeSize: androidx.compose.ui.unit.Dp,
    labelFontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    val container =
        when {
            isCurrent -> MaterialTheme.colorScheme.secondaryContainer
            isOnPath -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        }
    val border =
        when {
            isCurrent -> MaterialTheme.colorScheme.secondary
            isOnPath -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.outlineVariant
        }

    Surface(
        modifier =
            modifier
                .size(nodeSize)
                .border(1.dp, border, CircleShape),
        shape = CircleShape,
        color = container,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            val text = letter?.toString() ?: "·"
            val alpha = if (letter == null) 0.55f else 1.0f
            val style =
                MaterialTheme.typography.labelLarge.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                    lineHeight = labelFontSize,
                )

            Text(
                text = text,
                style = style,
                fontSize = labelFontSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
    }
}


private fun codeFor(depth: Int, index: Int): String {
    if (depth <= 0) return ""
    val sb = StringBuilder(depth)
    for (bit in depth - 1 downTo 0) {
        val v = (index shr bit) and 1
        sb.append(if (v == 0) '.' else '-')
    }
    return sb.toString()
}

private fun indexForCode(code: String): Int {
    var idx = 0
    for (c in code) {
        idx = idx * 2 + if (c == '-') 1 else 0
    }
    return idx
}

@Composable
private fun ResultScreen(
    title: String,
    detail: String,
    onFinished: () -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        headerContent?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
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
