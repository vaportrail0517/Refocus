package com.example.refocus.system.overlay.ui.minigame

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun MakeTenGame(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val numbers =
        remember(seed) {
            val size = MakeTenProblems.size(context)
            val index = Random(seed).nextInt(size)
            MakeTenProblems.get(context, index)
        }

    val digitKeys: List<MakeTenDigitKeyState> =
        remember(seed) {
            numbers.mapIndexed { idx, v -> MakeTenDigitKeyState(id = idx, value = v) }
        }

    val expr: SnapshotStateList<MakeTenInputToken> =
        remember(seed) { emptyList<MakeTenInputToken>().toMutableStateList() }

    var cursorIndex by remember(seed) { mutableIntStateOf(0) }

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

    fun removeAt(index: Int) {
        if (!editingEnabled) return
        if (index !in expr.indices) return
        val removed = expr.removeAt(index)
        if (removed is MakeTenInputToken.Number) {
            digitKeys.getOrNull(removed.digitId)?.used = false
        }
        if (cursorIndex > index) cursorIndex -= 1
        setCursorSafe(cursorIndex)
    }

    fun insertToken(token: MakeTenInputToken) {
        if (!editingEnabled) return
        if (token is MakeTenInputToken.Number) {
            val key = digitKeys.getOrNull(token.digitId) ?: return
            if (key.used) return
            key.used = true
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
            onSetCursor = { setCursorSafe(it) },
            onBackspace = { backspace() },
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
                onInsert = { insertToken(it) },
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
    onSetCursor: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exprText by remember(tokens) {
        derivedStateOf { tokens.joinToString(separator = "") { it.label } }
    }

    val caretColor = MaterialTheme.colorScheme.primary

    // タップ位置 -> 文字オフセット変換に必要
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    val contentPadding = 12.dp
    val contentPaddingPx = with(androidx.compose.ui.platform.LocalDensity.current) { contentPadding.toPx() }
    val caretHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 22.sp.toPx() }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // テキストエリア風の表示 + カーソル
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                // 余白も含めてタップ可能にする
                                .pointerInput(editingEnabled, exprText, tokens.size) {
                                    detectTapGestures { pos ->
                                        if (!editingEnabled) return@detectTapGestures
                                        val lr = layoutResult
                                        if (lr == null) {
                                            onSetCursor(tokens.size)
                                            return@detectTapGestures
                                        }
                                        val local = androidx.compose.ui.geometry.Offset(
                                            x = pos.x - contentPaddingPx,
                                            y = pos.y - contentPaddingPx,
                                        )
                                        val off = lr.getOffsetForPosition(local)
                                        onSetCursor(off.coerceIn(0, tokens.size))
                                    }
                                }
                                .padding(contentPadding)
                                .drawWithContent {
                                    drawContent()

                                    if (!editingEnabled) return@drawWithContent

                                    val safeIndex = cursorIndex.coerceIn(0, exprText.length)
                                    val lr = layoutResult
                                    val caret = lr?.getCursorRect(safeIndex)

                                    if (caret != null) {
                                        val x = caret.left
                                        drawLine(
                                            color = caretColor,
                                            start = androidx.compose.ui.geometry.Offset(x, caret.top),
                                            end = androidx.compose.ui.geometry.Offset(x, caret.bottom),
                                            strokeWidth = 2.dp.toPx(),
                                        )
                                    } else {
                                        // 空文字などでレイアウト情報が取れない場合のフォールバック
                                        drawLine(
                                            color = caretColor,
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(0f, caretHeightPx),
                                            strokeWidth = 2.dp.toPx(),
                                        )
                                    }
                                },
                    ) {
                        if (exprText.isEmpty()) {
                            Text(
                                text = "ここに式を入力",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = exprText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            onTextLayout = { layoutResult = it },
                        )
                    }
                }

                OutlinedButton(
                    onClick = onBackspace,
                    enabled = editingEnabled,
                    modifier = Modifier.height(56.dp).widthIn(min = 76.dp),
                ) {
                    Text(text = "削除")
                }
            }

            Text(
                text = "式をタップしてカーソル位置を変更できます．",
                style = MaterialTheme.typography.bodySmall,
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
    onInsert: (MakeTenInputToken) -> Unit,
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
                MakeTenKeyButton(
                    text = key.value.toString(),
                    enabled = enabled && !key.used,
                    modifier = Modifier.weight(1f),
                    onClick = { onInsert(MakeTenInputToken.Number(digitId = key.id, value = key.value)) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MakeTenKeyButton(
                text = "(",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.LParen) },
            )
            MakeTenKeyButton(
                text = ")",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.RParen) },
            )
            MakeTenKeyButton(
                text = "+",
                enabled = enabled,
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
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.Minus) },
            )
            MakeTenKeyButton(
                text = "×",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.Times) },
            )
            MakeTenKeyButton(
                text = "÷",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(MakeTenInputToken.Divide) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = enabled,
                modifier = Modifier.weight(2f).height(52.dp),
            ) {
                Text(text = "クリア")
            }
            OutlinedButton(
                onClick = onBackspace,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(52.dp),
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
        modifier = modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = modifier.fillMaxWidth().height(52.dp),
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

private sealed interface MakeTenExpr {
    data class Num(val value: MakeTenRational) : MakeTenExpr
    data class Bin(
        val op: MakeTenOp,
        val left: MakeTenExpr,
        val right: MakeTenExpr,
    ) : MakeTenExpr
}

private enum class MakeTenOp(val precedence: Int) {
    Plus(1),
    Minus(1),
    Times(2),
    Divide(2),
}

private sealed interface MakeTenAstToken {
    data class Number(val value: Int) : MakeTenAstToken
    data class Operator(val op: MakeTenOp) : MakeTenAstToken
    data class Paren(val isLeft: Boolean) : MakeTenAstToken
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

private fun gcd(a: Long, b: Long): Long {
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
    data class Ok(val value: MakeTenRational) : MakeTenEvalState
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
                if (prev == null || prev is MakeTenAstToken.Operator || (prev is MakeTenAstToken.Paren && prev.isLeft)) {
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
