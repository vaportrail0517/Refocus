package com.example.refocus.ui.minigame.games.mirrortext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Backspace
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
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

private fun generateTargetSentence(rng: Random): String {
    // キー入力（A-Z + SPACE）だけで打てるよう，記号は使わない
    val verbs =
        listOf(
            "FOCUS",
            "BREATHE",
            "WRITE",
            "READ",
            "STUDY",
            "BUILD",
            "PLAN",
            "START",
            "CONTINUE",
            "FINISH",
            "RELAX",
            "RESET",
            "REFOCUS",
            "MOVE",
            "LEARN",
            "CREATE",
        )

    val nouns =
        listOf(
            "ONE THING",
            "THE NEXT STEP",
            "YOUR TASK",
            "YOUR GOAL",
            "THIS MOMENT",
            "YOUR WORK",
            "A SMALL STEP",
            "THE PLAN",
            "THE IDEA",
            "THE PAGE",
            "YOUR NOTE",
            "YOUR TIME",
            "THE TODAY",
            "YOUR FOCUS",
            "THE IMPORTANT",
            "THE PRESENT",
        )

    val adjs =
        listOf(
            "CALM",
            "SMALL",
            "CLEAR",
            "GENTLE",
            "STEADY",
            "BRAVE",
            "SIMPLE",
            "QUIET",
            "SMART",
            "FRESH",
        )

    val times =
        listOf(
            "NOW",
            "TODAY",
            "RIGHT HERE",
            "THIS MINUTE",
        )

    val starters =
        listOf(
            "OK",
            "HEY",
            "ALRIGHT",
            "LISTEN",
            "READY",
        )

    val templates: List<(Random) -> String> =
        listOf(
            { _ -> "REFOCUS YOUR MIND" },
            { _ -> "THE QUICK BROWN FOX" },
            { r -> "${starters.random(r)} ${verbs.random(r)} ${nouns.random(r)}" },
            { r -> "${verbs.random(r)} ${nouns.random(r)} ${times.random(r)}" },
            { r -> "TAKE A ${adjs.random(r)} BREAK" },
            { r -> "ONE STEP AT A TIME" },
            { r -> "KEEP IT ${adjs.random(r)}" },
            { r -> "${verbs.random(r)} AND ${verbs.random(r)}" },
            { r -> "${verbs.random(r)} ${adjs.random(r)}" },
            { r -> "${adjs.random(r)} MIND ${adjs.random(r)} ACTION" },
        )

    val sentence = templates.random(rng)(rng)
    return sentence
        .replace(Regex("\\s+"), " ")
        .trim()
}

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
private fun InputEditor(
    text: String,
    cursorIndex: Int,
    onSetCursor: (Int) -> Unit,
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

    val contentColor =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            isCorrect -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }

    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val caretColor = MaterialTheme.colorScheme.primary
    val caretStrokeWidthPx = with(density) { 2.dp.toPx() }
    val fallbackCaretHeightPx = with(density) { 22.sp.toPx() }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var caretVisible by remember { mutableStateOf(true) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        caretVisible = true
        while (enabled) {
            delay(520L)
            caretVisible = !caretVisible
        }
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(12.dp),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clipToBounds(),
        ) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val contentPadding = 12.dp
            val contentPaddingPx = with(density) { contentPadding.toPx() }
            val marginPx = with(density) { 18.dp.toPx() }

            LaunchedEffect(text, cursorIndex, layoutResult, viewportWidthPx) {
                if (!enabled) return@LaunchedEffect
                val lr = layoutResult ?: return@LaunchedEffect
                val safeIndex = cursorIndex.coerceIn(0, text.length)
                val rect = lr.getCursorRect(safeIndex)
                val caretXInContent = contentPaddingPx + rect.left

                val visibleStart = scrollState.value.toFloat()
                val visibleEnd = visibleStart + viewportWidthPx

                val target =
                    when {
                        caretXInContent < visibleStart + marginPx -> (caretXInContent - marginPx).toInt()
                        caretXInContent > visibleEnd - marginPx ->
                            (caretXInContent - viewportWidthPx + marginPx)
                                .toInt()

                        else -> return@LaunchedEffect
                    }.coerceIn(0, scrollState.maxValue)

                scrollState.animateScrollTo(target)
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .horizontalScroll(scrollState)
                        .pointerInput(enabled, text) {
                            detectTapGestures { pos ->
                                if (!enabled) return@detectTapGestures
                                val lr = layoutResult
                                if (lr == null) {
                                    onSetCursor(text.length)
                                    return@detectTapGestures
                                }

                                val local =
                                    Offset(
                                        x = pos.x + scrollState.value - contentPaddingPx,
                                        y = pos.y - contentPaddingPx,
                                    )
                                val off = lr.getOffsetForPosition(local)
                                onSetCursor(off.coerceIn(0, text.length))
                            }
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(contentPadding)
                            .drawWithContent {
                                drawContent()
                                if (!enabled) return@drawWithContent
                                if (!caretVisible) return@drawWithContent

                                val safeIndex = cursorIndex.coerceIn(0, text.length)
                                val lr = layoutResult
                                val caret = lr?.getCursorRect(safeIndex)

                                if (caret != null) {
                                    drawLine(
                                        color = caretColor,
                                        start = Offset(caret.left, caret.top),
                                        end = Offset(caret.left, caret.bottom),
                                        strokeWidth = caretStrokeWidthPx,
                                    )
                                } else {
                                    val h = fallbackCaretHeightPx
                                    drawLine(
                                        color = caretColor,
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, h),
                                        strokeWidth = caretStrokeWidthPx,
                                    )
                                }
                            },
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "ここに入力",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            onTextLayout = { layoutResult = it },
                        )
                    } else {
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            onTextLayout = { layoutResult = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CursorActionRow(
    onMoveLeft: () -> Unit,
    onInsertSpace: () -> Unit,
    onMoveRight: () -> Unit,
    onBackspace: () -> Unit,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canBackspace: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionKey(
            weight = 0.7f,
            enabled = enabled && canMoveLeft,
            onClick = onMoveLeft,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "カーソルを左に",
            )
        }

        ActionKey(
            weight = 1.6f,
            enabled = enabled,
            onClick = onInsertSpace,
        ) {
            Text(
                text = "SPACE",
                fontWeight = FontWeight.SemiBold,
            )
        }

        ActionKey(
            weight = 0.7f,
            enabled = enabled && canMoveRight,
            onClick = onMoveRight,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowForward,
                contentDescription = "カーソルを右に",
            )
        }

        ActionKey(
            weight = 1.0f,
            enabled = enabled && canBackspace,
            onClick = onBackspace,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Icon(
                imageVector = Icons.Outlined.Backspace,
                contentDescription = "削除",
            )
        }
    }
}

@Composable
private fun RowScope.ActionKey(
    weight: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.weight(weight).height(52.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else containerColor,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else contentColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .let {
                        if (enabled) {
                            it.clickable(onClick = onClick)
                        } else {
                            it
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
internal fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rng = remember(seed) { Random(seed) }
    val targetSentence = remember(seed) { generateTargetSentence(rng) }

    var phase by remember(seed) { mutableStateOf(MirrorTextPhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(TIME_LIMIT_SECONDS) }

    var inputText by remember(seed) { mutableStateOf("") }
    var cursorIndex by remember(seed) { mutableIntStateOf(0) }

    val isCorrect = inputText == targetSentence

    fun setCursorSafe(newIndex: Int) {
        cursorIndex = newIndex.coerceIn(0, inputText.length)
    }

    fun insertText(value: String) {
        if (phase != MirrorTextPhase.Playing) return
        val safeIndex = cursorIndex.coerceIn(0, inputText.length)
        inputText = inputText.substring(0, safeIndex) + value + inputText.substring(safeIndex)
        cursorIndex = (safeIndex + value.length).coerceIn(0, inputText.length)
    }

    fun backspace() {
        if (phase != MirrorTextPhase.Playing) return
        val safeIndex = cursorIndex.coerceIn(0, inputText.length)
        if (safeIndex <= 0) return
        inputText = inputText.removeRange(safeIndex - 1, safeIndex)
        cursorIndex = (safeIndex - 1).coerceIn(0, inputText.length)
    }

    fun moveCursorLeft() {
        if (phase != MirrorTextPhase.Playing) return
        setCursorSafe(cursorIndex - 1)
    }

    fun moveCursorRight() {
        if (phase != MirrorTextPhase.Playing) return
        setCursorSafe(cursorIndex + 1)
    }

    LaunchedEffect(seed) {
        remainingSeconds = TIME_LIMIT_SECONDS
        phase = MirrorTextPhase.Playing
        inputText = ""
        cursorIndex = 0
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

        InputEditor(
            text = inputText,
            cursorIndex = cursorIndex,
            onSetCursor = ::setCursorSafe,
            isCorrect = isCorrect,
            enabled = phase == MirrorTextPhase.Playing,
            modifier = Modifier.fillMaxWidth(),
        )

        when (phase) {
            MirrorTextPhase.Playing -> {
                QwertyKeyboard(
                    onKeyClick = { char -> insertText(char.toString()) },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                CursorActionRow(
                    onMoveLeft = ::moveCursorLeft,
                    onInsertSpace = { insertText(" ") },
                    onMoveRight = ::moveCursorRight,
                    onBackspace = ::backspace,
                    canMoveLeft = cursorIndex > 0,
                    canMoveRight = cursorIndex < inputText.length,
                    canBackspace = cursorIndex > 0,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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

private fun formatSeconds(totalSeconds: Int): String {
    val m = (totalSeconds.coerceAtLeast(0)) / 60
    val s = (totalSeconds.coerceAtLeast(0)) % 60
    return "%d:%02d".format(m, s)
}
