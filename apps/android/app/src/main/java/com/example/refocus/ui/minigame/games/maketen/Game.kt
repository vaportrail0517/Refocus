package com.example.refocus.ui.minigame.games.maketen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val numbers =
        remember(seed) {
            val size = Problems.size(context)
            val index = Random(seed).nextInt(size)
            Problems.get(context, index)
        }

    val digitKeys: List<MakeTenDigitKeyState> =
        remember(seed) {
            numbers.mapIndexed { idx, v -> MakeTenDigitKeyState(id = idx, value = v) }
        }

    val expr: SnapshotStateList<MakeTenInputToken> =
        remember(seed) { emptyList<MakeTenInputToken>().toMutableStateList() }

    var cursorIndex by remember(seed) { mutableIntStateOf(0) }

    val undoStack: SnapshotStateList<MakeTenEditorSnapshot> =
        remember(seed) { emptyList<MakeTenEditorSnapshot>().toMutableStateList() }

    var phase by remember(seed) { mutableStateOf(MakeTenPhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(60) }

    LaunchedEffect(seed) {
        while (true) {
            if (phase != MakeTenPhase.Playing) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase != MakeTenPhase.Playing) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0 && phase == MakeTenPhase.Playing) {
                phase = MakeTenPhase.TimeUp
            }
        }
    }

    val allDigitsUsed by remember(digitKeys) {
        derivedStateOf { digitKeys.all { it.used } }
    }

    val tokensForEval by remember(expr) {
        derivedStateOf { expr.map { it.toEvalToken() } }
    }

    val evalState by remember(tokensForEval) {
        derivedStateOf { evaluateExpression(tokensForEval) }
    }

    LaunchedEffect(evalState, allDigitsUsed, phase, remainingSeconds) {
        if (phase != MakeTenPhase.Playing) return@LaunchedEffect
        if (remainingSeconds <= 0) return@LaunchedEffect
        val v = evalState
        if (v is MakeTenEvalState.Ok && v.value.isTen() && allDigitsUsed) {
            phase = MakeTenPhase.Solved
        }
    }

    val editingEnabled = phase == MakeTenPhase.Playing && remainingSeconds > 0

    fun setCursorSafe(index: Int) {
        cursorIndex = index.coerceIn(0, expr.size)
    }

    fun syncDigitUsedFromExpr() {
        digitKeys.forEach { it.used = false }
        expr.forEach { t ->
            if (t is MakeTenInputToken.Number) {
                digitKeys.getOrNull(t.digitId)?.used = true
            }
        }
    }

    fun pushUndoSnapshot() {
        // 直前の状態を保存して Undo を可能にする
        undoStack.add(MakeTenEditorSnapshot(tokens = expr.toList(), cursorIndex = cursorIndex))
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    fun undo() {
        if (!editingEnabled) return
        if (undoStack.isEmpty()) return
        val snap = undoStack.removeAt(undoStack.lastIndex)
        expr.clear()
        expr.addAll(snap.tokens)
        cursorIndex = snap.cursorIndex.coerceIn(0, expr.size)
        syncDigitUsedFromExpr()
    }

    fun parenBalanceBefore(index: Int): Int {
        var balance = 0
        for (i in 0 until index.coerceIn(0, expr.size)) {
            when (expr[i]) {
                MakeTenInputToken.LParen -> balance += 1
                MakeTenInputToken.RParen -> balance -= 1
                else -> {}
            }
        }
        return balance
    }

    fun kindOf(token: MakeTenInputToken): MakeTenTokenKind =
        when (token) {
            is MakeTenInputToken.Number -> MakeTenTokenKind.Operand
            MakeTenInputToken.LParen -> MakeTenTokenKind.LParen
            MakeTenInputToken.RParen -> MakeTenTokenKind.RParen
            MakeTenInputToken.Plus,
            MakeTenInputToken.Minus,
            MakeTenInputToken.Times,
            MakeTenInputToken.Divide,
            -> MakeTenTokenKind.Operator
        }

    fun isAllowedPair(
        prev: MakeTenInputToken?,
        next: MakeTenInputToken?,
    ): Boolean {
        val prevKind = prev?.let { kindOf(it) }
        val nextKind = next?.let { kindOf(it) }

        return when (prevKind) {
            null -> nextKind == null || nextKind == MakeTenTokenKind.Operand || nextKind == MakeTenTokenKind.LParen
            MakeTenTokenKind.Operand,
            MakeTenTokenKind.RParen,
            -> nextKind == null || nextKind == MakeTenTokenKind.Operator || nextKind == MakeTenTokenKind.RParen

            MakeTenTokenKind.Operator,
            MakeTenTokenKind.LParen,
            -> nextKind == null || nextKind == MakeTenTokenKind.Operand || nextKind == MakeTenTokenKind.LParen
        }
    }

    fun canInsertToken(token: MakeTenInputToken): Boolean {
        // 入力制御は行わない（文法制約や括弧バランスでボタンを無効化しない）．
        // ただし数字の重複使用だけは防止する（使用済み数字キーはグレーアウト）．
        if (!editingEnabled) return false

        if (token is MakeTenInputToken.Number) {
            val key = digitKeys.getOrNull(token.digitId) ?: return false
            if (key.used) return false
        }

        return true
    }

    fun removeAt(index: Int) {
        if (!editingEnabled) return
        if (index !in expr.indices) return
        pushUndoSnapshot()
        val removed = expr.removeAt(index)
        if (removed is MakeTenInputToken.Number) {
            digitKeys.getOrNull(removed.digitId)?.used = false
        }
        if (cursorIndex > index) cursorIndex -= 1
        setCursorSafe(cursorIndex)
    }

    fun insertToken(token: MakeTenInputToken) {
        if (!canInsertToken(token)) return
        pushUndoSnapshot()
        if (token is MakeTenInputToken.Number) {
            digitKeys.getOrNull(token.digitId)?.used = true
        }
        val idx = cursorIndex.coerceIn(0, expr.size)
        expr.add(idx, token)
        setCursorSafe(idx + 1)
    }

    fun backspace() {
        if (!editingEnabled) return
        if (cursorIndex <= 0) return
        removeAt(cursorIndex - 1)
    }

    fun clearAll() {
        if (!editingEnabled) return
        if (expr.isEmpty()) return
        pushUndoSnapshot()
        expr.clear()
        digitKeys.forEach { it.used = false }
        setCursorSafe(0)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MakeTenHeader(
            phase = phase,
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
        )

        ExpressionEditor(
            tokens = expr,
            cursorIndex = cursorIndex,
            editingEnabled = editingEnabled,
            usedDigitsCount = digitKeys.count { it.used },
            totalDigitsCount = digitKeys.size,
            onSetCursor = { setCursorSafe(it) },
        )

        EvaluationText(
            phase = phase,
            evalState = evalState,
            allDigitsUsed = allDigitsUsed,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (phase == MakeTenPhase.Playing) {
            MakeTenKeyboard(
                digitKeys = digitKeys,
                enabled = editingEnabled,
                canInsert = { canInsertToken(it) },
                canUndo = undoStack.isNotEmpty(),
                canBackspace = cursorIndex > 0,
                canClear = expr.isNotEmpty(),
                onInsert = { insertToken(it) },
                onUndo = { undo() },
                onBackspace = { backspace() },
                onClear = { clearAll() },
            )
        } else {
            ResultFooter(onFinished = onFinished)
        }
    }
}

@Composable
private fun MakeTenHeader(
    phase: MakeTenPhase,
    remainingSeconds: Int,
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
                text = "make ten",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle =
                when (phase) {
                    MakeTenPhase.Playing -> "数字と記号で 10 を作る"
                    MakeTenPhase.Solved -> "正解"
                    MakeTenPhase.TimeUp -> "時間切れ"
                }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = formatSeconds(remainingSeconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ExpressionEditor(
    tokens: SnapshotStateList<MakeTenInputToken>,
    cursorIndex: Int,
    editingEnabled: Boolean,
    usedDigitsCount: Int,
    totalDigitsCount: Int,
    onSetCursor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exprText by remember(tokens) {
        derivedStateOf { tokens.joinToString(separator = "") { it.label } }
    }

    val highlightParenPair by remember(tokens, cursorIndex) {
        derivedStateOf { findMatchingParenPair(tokens, cursorIndex) }
    }

    val highlightIndices by remember(highlightParenPair) {
        derivedStateOf {
            highlightParenPair?.let { setOf(it.first, it.second) } ?: emptySet()
        }
    }

    // MaterialTheme の参照は @Composable コンテキストで行い，
    // derivedStateOf の中では通常の値として扱う（@Composable 呼び出しエラー回避）
    val parenHighlightBg = MaterialTheme.colorScheme.secondaryContainer
    val parenHighlightFg = MaterialTheme.colorScheme.onSecondaryContainer

    val displayText by remember(tokens, highlightIndices, parenHighlightBg, parenHighlightFg) {
        derivedStateOf {
            buildAnnotatedString {
                tokens.forEachIndexed { idx, t ->
                    if (idx in highlightIndices) {
                        withStyle(
                            SpanStyle(
                                background = parenHighlightBg,
                                color = parenHighlightFg,
                            ),
                        ) {
                            append(t.label)
                        }
                    } else {
                        append(t.label)
                    }
                }
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val contentPadding = 12.dp
    val contentPaddingPx = with(density) { contentPadding.toPx() }

    var caretVisible by remember { mutableStateOf(false) }
    val caretColor = MaterialTheme.colorScheme.primary

    // カーソル移動・入力直後は一旦点灯してから点滅させる
    LaunchedEffect(editingEnabled, cursorIndex, exprText) {
        if (!editingEnabled) {
            caretVisible = false
            return@LaunchedEffect
        }
        caretVisible = true
        while (true) {
            delay(520)
            caretVisible = !caretVisible
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "使用 $usedDigitsCount/$totalDigitsCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.small,
            ) {
                // 1行固定 + 横スクロール + カーソル追従
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clipToBounds(),
                ) {
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val marginPx = with(density) { 18.dp.toPx() }

                    // カーソルが見えるように自動スクロール
                    LaunchedEffect(cursorIndex, exprText, layoutResult, viewportWidthPx) {
                        if (!editingEnabled) return@LaunchedEffect
                        val lr = layoutResult ?: return@LaunchedEffect
                        val safeIndex = cursorIndex.coerceIn(0, exprText.length)
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
                                .pointerInput(editingEnabled, exprText, tokens.size) {
                                    detectTapGestures { pos ->
                                        if (!editingEnabled) return@detectTapGestures
                                        val lr = layoutResult
                                        if (lr == null) {
                                            onSetCursor(tokens.size)
                                            return@detectTapGestures
                                        }
                                        val local =
                                            Offset(
                                                x = pos.x + scrollState.value - contentPaddingPx,
                                                y = pos.y - contentPaddingPx,
                                            )
                                        val off = lr.getOffsetForPosition(local)
                                        onSetCursor(off.coerceIn(0, tokens.size))
                                    }
                                },
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(contentPadding)
                                    .drawWithContent {
                                        drawContent()
                                        if (!editingEnabled) return@drawWithContent
                                        if (!caretVisible) return@drawWithContent

                                        val safeIndex = cursorIndex.coerceIn(0, exprText.length)
                                        val lr = layoutResult
                                        val caret = lr?.getCursorRect(safeIndex)

                                        if (caret != null) {
                                            drawLine(
                                                color = caretColor,
                                                start = Offset(caret.left, caret.top),
                                                end = Offset(caret.left, caret.bottom),
                                                strokeWidth = with(density) { 2.dp.toPx() },
                                            )
                                        } else {
                                            // 空文字などでレイアウト情報が取れない場合のフォールバック
                                            val h = with(density) { 22.sp.toPx() }
                                            drawLine(
                                                color = caretColor,
                                                start = Offset(0f, 0f),
                                                end = Offset(0f, h),
                                                strokeWidth = with(density) { 2.dp.toPx() },
                                            )
                                        }
                                    },
                        ) {
                            if (exprText.isEmpty()) {
                                Text(
                                    text = "ここに式を入力",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }

                            Text(
                                text = displayText,
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

            Text(
                text = "式をタップしてカーソル位置を変更できます．",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvaluationText(
    phase: MakeTenPhase,
    evalState: MakeTenEvalState,
    allDigitsUsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        when (evalState) {
            is MakeTenEvalState.NotParsable -> "式を評価できません"
            is MakeTenEvalState.DivisionByZero -> "0 では割れません"
            is MakeTenEvalState.Ok -> {
                val base = "＝ ${evalState.value.toDisplayString()}"
                if (evalState.value.isTen() && !allDigitsUsed) {
                    "$base（数字をすべて使ってください）"
                } else {
                    base
                }
            }
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
        fontWeight = if (phase == MakeTenPhase.Solved) FontWeight.SemiBold else null,
    )
}

@Composable
private fun MakeTenKeyboard(
    digitKeys: List<MakeTenDigitKeyState>,
    enabled: Boolean,
    canInsert: (MakeTenInputToken) -> Boolean,
    canUndo: Boolean,
    canBackspace: Boolean,
    canClear: Boolean,
    onInsert: (MakeTenInputToken) -> Unit,
    onUndo: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            digitKeys.forEach { key ->
                val token = MakeTenInputToken.Number(digitId = key.id, value = key.value)
                MakeTenKeyButton(
                    text = key.value.toString(),
                    enabled = enabled && canInsert(token),
                    modifier = Modifier.weight(1f),
                    onClick = { onInsert(token) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MakeTenKeyButton(
                text = "(",
                enabled = enabled && canInsert(MakeTenInputToken.LParen),
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.LParen) },
            )
            MakeTenKeyButton(
                text = ")",
                enabled = enabled && canInsert(MakeTenInputToken.RParen),
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.RParen) },
            )
            MakeTenKeyButton(
                text = "+",
                enabled = enabled && canInsert(MakeTenInputToken.Plus),
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.Plus) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MakeTenKeyButton(
                text = "-",
                enabled = enabled && canInsert(MakeTenInputToken.Minus),
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.Minus) },
            )
            MakeTenKeyButton(
                text = "×",
                enabled = enabled && canInsert(MakeTenInputToken.Times),
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.Times) },
            )
            MakeTenKeyButton(
                text = "÷",
                enabled = enabled && canInsert(MakeTenInputToken.Divide),
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.Divide) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                enabled = enabled && canUndo,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
            ) {
                Text(text = "戻す")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = enabled && canClear,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
            ) {
                Text(text = "クリア")
            }
            OutlinedButton(
                onClick = onBackspace,
                enabled = enabled && canBackspace,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
            ) {
                Text(text = "削除")
            }
        }
    }
}

@Composable
private fun MakeTenKeyButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(52.dp)
                .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
            if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun ResultFooter(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onFinished,
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp),
    ) {
        Text(text = "完了")
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

private enum class MakeTenPhase {
    Playing,
    Solved,
    TimeUp,
}

private class MakeTenDigitKeyState(
    val id: Int,
    val value: Int,
) {
    var used by mutableStateOf(false)
}

private sealed interface MakeTenInputToken {
    val label: String

    data class Number(
        val digitId: Int,
        val value: Int,
    ) : MakeTenInputToken {
        override val label: String = value.toString()
    }

    data object LParen : MakeTenInputToken {
        override val label: String = "("
    }

    data object RParen : MakeTenInputToken {
        override val label: String = ")"
    }

    data object Plus : MakeTenInputToken {
        override val label: String = "+"
    }

    data object Minus : MakeTenInputToken {
        override val label: String = "-"
    }

    data object Times : MakeTenInputToken {
        override val label: String = "×"
    }

    data object Divide : MakeTenInputToken {
        override val label: String = "÷"
    }
}

private enum class MakeTenTokenKind {
    Operand,
    Operator,
    LParen,
    RParen,
}

@Stable
private data class MakeTenEditorSnapshot(
    val tokens: List<MakeTenInputToken>,
    val cursorIndex: Int,
)

private fun findMatchingParenPair(
    tokens: List<MakeTenInputToken>,
    cursorIndex: Int,
): Pair<Int, Int>? {
    if (tokens.isEmpty()) return null

    val candidates =
        listOf(cursorIndex - 1, cursorIndex)
            .filter { it in tokens.indices }
            .filter { tokens[it] == MakeTenInputToken.LParen || tokens[it] == MakeTenInputToken.RParen }

    val idx = candidates.firstOrNull() ?: return null
    val token = tokens[idx]

    return if (token == MakeTenInputToken.LParen) {
        var depth = 0
        for (i in idx + 1 until tokens.size) {
            when (tokens[i]) {
                MakeTenInputToken.LParen -> depth += 1
                MakeTenInputToken.RParen -> {
                    if (depth == 0) return idx to i
                    depth -= 1
                }

                else -> {}
            }
        }
        null
    } else {
        var depth = 0
        for (i in idx - 1 downTo 0) {
            when (tokens[i]) {
                MakeTenInputToken.RParen -> depth += 1
                MakeTenInputToken.LParen -> {
                    if (depth == 0) return i to idx
                    depth -= 1
                }

                else -> {}
            }
        }
        null
    }
}

private sealed interface MakeTenExpr {
    data class Num(
        val value: MakeTenRational,
    ) : MakeTenExpr

    data class Bin(
        val op: MakeTenOp,
        val left: MakeTenExpr,
        val right: MakeTenExpr,
    ) : MakeTenExpr
}

private enum class MakeTenOp(
    val precedence: Int,
) {
    Plus(1),
    Minus(1),
    Times(2),
    Divide(2),
}

private sealed interface MakeTenAstToken {
    data class Number(
        val value: Int,
    ) : MakeTenAstToken

    data class Operator(
        val op: MakeTenOp,
    ) : MakeTenAstToken

    data class Paren(
        val isLeft: Boolean,
    ) : MakeTenAstToken
}

private fun MakeTenInputToken.toEvalToken(): MakeTenAstToken =
    when (this) {
        is MakeTenInputToken.Number -> MakeTenAstToken.Number(value)
        MakeTenInputToken.LParen -> MakeTenAstToken.Paren(isLeft = true)
        MakeTenInputToken.RParen -> MakeTenAstToken.Paren(isLeft = false)
        MakeTenInputToken.Plus -> MakeTenAstToken.Operator(MakeTenOp.Plus)
        MakeTenInputToken.Minus -> MakeTenAstToken.Operator(MakeTenOp.Minus)
        MakeTenInputToken.Times -> MakeTenAstToken.Operator(MakeTenOp.Times)
        MakeTenInputToken.Divide -> MakeTenAstToken.Operator(MakeTenOp.Divide)
    }

@Stable
private data class MakeTenRational(
    val numerator: Long,
    val denominator: Long,
) {
    init {
        require(denominator != 0L)
    }

    fun isZero(): Boolean = numerator == 0L

    fun isTen(): Boolean = numerator == 10L && denominator == 1L

    fun toDisplayString(): String =
        if (denominator == 1L) {
            numerator.toString()
        } else {
            "%d/%d".format(numerator, denominator)
        }

    companion object {
        fun ofInt(v: Int): MakeTenRational = MakeTenRational(v.toLong(), 1L).reduce()
    }

    fun reduce(): MakeTenRational {
        if (numerator == 0L) return MakeTenRational(0L, 1L)
        val g = gcd(abs(numerator), abs(denominator))
        val sign = if (denominator < 0L) -1L else 1L
        return MakeTenRational(sign * (numerator / g), sign * (denominator / g))
    }

    operator fun plus(other: MakeTenRational): MakeTenRational =
        MakeTenRational(
            numerator * other.denominator + other.numerator * denominator,
            denominator * other.denominator,
        ).reduce()

    operator fun minus(other: MakeTenRational): MakeTenRational =
        MakeTenRational(
            numerator * other.denominator - other.numerator * denominator,
            denominator * other.denominator,
        ).reduce()

    operator fun times(other: MakeTenRational): MakeTenRational =
        MakeTenRational(
            numerator * other.numerator,
            denominator * other.denominator,
        ).reduce()

    fun div(other: MakeTenRational): MakeTenRational? {
        if (other.isZero()) return null
        return MakeTenRational(
            numerator * other.denominator,
            denominator * other.numerator,
        ).reduce()
    }
}

private fun gcd(
    a: Long,
    b: Long,
): Long {
    var x = a
    var y = b
    while (y != 0L) {
        val t = x % y
        x = y
        y = t
    }
    return if (x == 0L) 1L else abs(x)
}

private sealed interface MakeTenEvalState {
    data object NotParsable : MakeTenEvalState

    data object DivisionByZero : MakeTenEvalState

    data class Ok(
        val value: MakeTenRational,
    ) : MakeTenEvalState
}

private fun evaluateExpression(tokens: List<MakeTenAstToken>): MakeTenEvalState {
    val expr = parseToAst(tokens) ?: return MakeTenEvalState.NotParsable
    return when (val v = eval(expr)) {
        null -> MakeTenEvalState.DivisionByZero
        else -> MakeTenEvalState.Ok(v)
    }
}

/**
 * 単項演算子は許可しない．
 *
 * 実装は shunting-yard で AST を構築する．
 */
private fun parseToAst(tokens: List<MakeTenAstToken>): MakeTenExpr? {
    val exprStack = ArrayDeque<MakeTenExpr>()
    val opStack = ArrayDeque<MakeTenAstToken>()

    fun applyOp(opToken: MakeTenAstToken.Operator): Boolean {
        if (exprStack.size < 2) return false
        val right = exprStack.removeLast()
        val left = exprStack.removeLast()
        exprStack.addLast(MakeTenExpr.Bin(opToken.op, left, right))
        return true
    }

    var prev: MakeTenAstToken? = null

    for (t in tokens) {
        when (t) {
            is MakeTenAstToken.Number -> {
                exprStack.addLast(MakeTenExpr.Num(MakeTenRational.ofInt(t.value)))
            }

            is MakeTenAstToken.Paren -> {
                if (t.isLeft) {
                    opStack.addLast(t)
                } else {
                    var found = false
                    while (opStack.isNotEmpty()) {
                        val top = opStack.removeLast()
                        when (top) {
                            is MakeTenAstToken.Operator -> if (!applyOp(top)) return null
                            is MakeTenAstToken.Number -> return null
                            is MakeTenAstToken.Paren -> {
                                if (top.isLeft) {
                                    found = true
                                    break
                                } else {
                                    return null
                                }
                            }
                        }
                    }
                    if (!found) return null
                }
            }

            is MakeTenAstToken.Operator -> {
                // 単項演算子禁止：先頭，演算子の直後，'(' の直後は不可
                if (prev == null ||
                    prev is MakeTenAstToken.Operator ||
                    (prev is MakeTenAstToken.Paren && prev.isLeft)
                ) {
                    return null
                }

                while (opStack.isNotEmpty()) {
                    val top = opStack.last()
                    if (top is MakeTenAstToken.Operator && top.op.precedence >= t.op.precedence) {
                        opStack.removeLast()
                        if (!applyOp(top)) return null
                    } else {
                        break
                    }
                }
                opStack.addLast(t)
            }
        }
        prev = t
    }

    if (prev is MakeTenAstToken.Operator) return null

    while (opStack.isNotEmpty()) {
        val top = opStack.removeLast()
        when (top) {
            is MakeTenAstToken.Operator -> if (!applyOp(top)) return null
            is MakeTenAstToken.Number -> return null
            is MakeTenAstToken.Paren -> return null
        }
    }

    return exprStack.singleOrNull()
}

private fun eval(expr: MakeTenExpr): MakeTenRational? {
    return when (expr) {
        is MakeTenExpr.Num -> expr.value
        is MakeTenExpr.Bin -> {
            val l = eval(expr.left) ?: return null
            val r = eval(expr.right) ?: return null
            when (expr.op) {
                MakeTenOp.Plus -> l + r
                MakeTenOp.Minus -> l - r
                MakeTenOp.Times -> l * r
                MakeTenOp.Divide -> l.div(r) ?: return null
            }
        }
    }
}
