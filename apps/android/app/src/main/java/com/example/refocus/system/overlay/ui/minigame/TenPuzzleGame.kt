package com.example.refocus.system.overlay.ui.minigame

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun TenPuzzleGame(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val numbers =
        remember(seed) {
            val size = TenPuzzleProblems.size(context)
            val index = Random(seed).nextInt(size)
            TenPuzzleProblems.get(context, index)
        }

    val digitKeys: List<TenPuzzleDigitKeyState> =
        remember(seed) {
            numbers.mapIndexed { idx, v -> TenPuzzleDigitKeyState(id = idx, value = v) }
        }

    val expr: SnapshotStateList<TenPuzzleInputToken> =
        remember(seed) { emptyList<TenPuzzleInputToken>().toMutableStateList() }

    var cursorIndex by remember(seed) { mutableIntStateOf(0) }

    var phase by remember(seed) { mutableStateOf(TenPuzzlePhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(60) }

    LaunchedEffect(seed) {
        while (true) {
            if (phase != TenPuzzlePhase.Playing) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase != TenPuzzlePhase.Playing) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0 && phase == TenPuzzlePhase.Playing) {
                phase = TenPuzzlePhase.TimeUp
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
        if (phase != TenPuzzlePhase.Playing) return@LaunchedEffect
        if (remainingSeconds <= 0) return@LaunchedEffect
        val v = evalState
        if (v is TenPuzzleEvalState.Ok && v.value.isTen() && allDigitsUsed) {
            phase = TenPuzzlePhase.Solved
        }
    }

    val editingEnabled = phase == TenPuzzlePhase.Playing && remainingSeconds > 0

    fun setCursorSafe(index: Int) {
        cursorIndex = index.coerceIn(0, expr.size)
    }

    fun removeAt(index: Int) {
        if (!editingEnabled) return
        if (index !in expr.indices) return
        val removed = expr.removeAt(index)
        if (removed is TenPuzzleInputToken.Number) {
            digitKeys.getOrNull(removed.digitId)?.used = false
        }
        if (cursorIndex > index) cursorIndex -= 1
        setCursorSafe(cursorIndex)
    }

    fun insertToken(token: TenPuzzleInputToken) {
        if (!editingEnabled) return
        if (token is TenPuzzleInputToken.Number) {
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
        TenPuzzleHeader(
            phase = phase,
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
        )

        ExpressionEditor(
            tokens = expr,
            cursorIndex = cursorIndex,
            editingEnabled = editingEnabled,
            onSetCursor = { setCursorSafe(it) },
            onRemoveAt = { removeAt(it) },
        )

        EvaluationText(
            phase = phase,
            evalState = evalState,
            allDigitsUsed = allDigitsUsed,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (phase == TenPuzzlePhase.Playing) {
            TenPuzzleKeyboard(
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
private fun TenPuzzleHeader(
    phase: TenPuzzlePhase,
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
                text = "テンパズル",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle =
                when (phase) {
                    TenPuzzlePhase.Playing -> "数字と記号で 10 を作る"
                    TenPuzzlePhase.Solved -> "正解"
                    TenPuzzlePhase.TimeUp -> "時間切れ"
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpressionEditor(
    tokens: SnapshotStateList<TenPuzzleInputToken>,
    cursorIndex: Int,
    editingEnabled: Boolean,
    onSetCursor: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0..tokens.size) {
                    InsertCursor(
                        selected = i == cursorIndex,
                        enabled = editingEnabled,
                        onClick = { onSetCursor(i) },
                    )
                    if (i < tokens.size) {
                        TokenChip(
                            token = tokens[i],
                            enabled = editingEnabled,
                            onClick = { onRemoveAt(i) },
                        )
                    }
                }
            }

            Text(
                text = "トークンをタップすると削除できます．カーソル位置にキーを挿入します．",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InsertCursor(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border =
        if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }

    Surface(
        modifier =
            modifier
                .size(width = 14.dp, height = 36.dp)
                .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .width(if (selected) 3.dp else 2.dp)
                        .height(20.dp),
            )
        }
    }
}

@Composable
private fun TokenChip(
    token: TenPuzzleInputToken,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .sizeIn(minWidth = 32.dp, minHeight = 36.dp)
                .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = token.label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun EvaluationText(
    phase: TenPuzzlePhase,
    evalState: TenPuzzleEvalState,
    allDigitsUsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        when (evalState) {
            is TenPuzzleEvalState.NotParsable -> "式を評価できません"
            is TenPuzzleEvalState.DivisionByZero -> "0 では割れません"
            is TenPuzzleEvalState.Ok -> {
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
        fontWeight = if (phase == TenPuzzlePhase.Solved) FontWeight.SemiBold else null,
    )
}

@Composable
private fun TenPuzzleKeyboard(
    digitKeys: List<TenPuzzleDigitKeyState>,
    enabled: Boolean,
    onInsert: (TenPuzzleInputToken) -> Unit,
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
                TenPuzzleKeyButton(
                    text = key.value.toString(),
                    enabled = enabled && !key.used,
                    modifier = Modifier.weight(1f),
                    onClick = { onInsert(TenPuzzleInputToken.Number(digitId = key.id, value = key.value)) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TenPuzzleKeyButton(
                text = "(",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(TenPuzzleInputToken.LParen) },
            )
            TenPuzzleKeyButton(
                text = ")",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(TenPuzzleInputToken.RParen) },
            )
            TenPuzzleKeyButton(
                text = "+",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(TenPuzzleInputToken.Plus) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TenPuzzleKeyButton(
                text = "-",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(TenPuzzleInputToken.Minus) },
            )
            TenPuzzleKeyButton(
                text = "×",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(TenPuzzleInputToken.Times) },
            )
            TenPuzzleKeyButton(
                text = "÷",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(TenPuzzleInputToken.Divide) },
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
                Text(text = "⌫")
            }
        }
    }
}

@Composable
private fun TenPuzzleKeyButton(
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

private enum class TenPuzzlePhase {
    Playing,
    Solved,
    TimeUp,
}

private class TenPuzzleDigitKeyState(
    val id: Int,
    val value: Int,
) {
    var used by mutableStateOf(false)
}

private sealed interface TenPuzzleInputToken {
    val label: String

    data class Number(
        val digitId: Int,
        val value: Int,
    ) : TenPuzzleInputToken {
        override val label: String = value.toString()
    }

    data object LParen : TenPuzzleInputToken {
        override val label: String = "("
    }

    data object RParen : TenPuzzleInputToken {
        override val label: String = ")"
    }

    data object Plus : TenPuzzleInputToken {
        override val label: String = "+"
    }

    data object Minus : TenPuzzleInputToken {
        override val label: String = "-"
    }

    data object Times : TenPuzzleInputToken {
        override val label: String = "×"
    }

    data object Divide : TenPuzzleInputToken {
        override val label: String = "÷"
    }
}

private sealed interface TenPuzzleExpr {
    data class Num(val value: TenPuzzleRational) : TenPuzzleExpr
    data class Bin(
        val op: TenPuzzleOp,
        val left: TenPuzzleExpr,
        val right: TenPuzzleExpr,
    ) : TenPuzzleExpr
}

private enum class TenPuzzleOp(val precedence: Int) {
    Plus(1),
    Minus(1),
    Times(2),
    Divide(2),
}

private sealed interface TenPuzzleAstToken {
    data class Number(val value: Int) : TenPuzzleAstToken
    data class Operator(val op: TenPuzzleOp) : TenPuzzleAstToken
    data class Paren(val isLeft: Boolean) : TenPuzzleAstToken
}

private fun TenPuzzleInputToken.toEvalToken(): TenPuzzleAstToken =
    when (this) {
        is TenPuzzleInputToken.Number -> TenPuzzleAstToken.Number(value)
        TenPuzzleInputToken.LParen -> TenPuzzleAstToken.Paren(isLeft = true)
        TenPuzzleInputToken.RParen -> TenPuzzleAstToken.Paren(isLeft = false)
        TenPuzzleInputToken.Plus -> TenPuzzleAstToken.Operator(TenPuzzleOp.Plus)
        TenPuzzleInputToken.Minus -> TenPuzzleAstToken.Operator(TenPuzzleOp.Minus)
        TenPuzzleInputToken.Times -> TenPuzzleAstToken.Operator(TenPuzzleOp.Times)
        TenPuzzleInputToken.Divide -> TenPuzzleAstToken.Operator(TenPuzzleOp.Divide)
    }

@Stable
private data class TenPuzzleRational(
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
        fun ofInt(v: Int): TenPuzzleRational = TenPuzzleRational(v.toLong(), 1L).reduce()
    }

    fun reduce(): TenPuzzleRational {
        if (numerator == 0L) return TenPuzzleRational(0L, 1L)
        val g = gcd(abs(numerator), abs(denominator))
        val sign = if (denominator < 0L) -1L else 1L
        return TenPuzzleRational(sign * (numerator / g), sign * (denominator / g))
    }

    operator fun plus(other: TenPuzzleRational): TenPuzzleRational =
        TenPuzzleRational(
            numerator * other.denominator + other.numerator * denominator,
            denominator * other.denominator,
        ).reduce()

    operator fun minus(other: TenPuzzleRational): TenPuzzleRational =
        TenPuzzleRational(
            numerator * other.denominator - other.numerator * denominator,
            denominator * other.denominator,
        ).reduce()

    operator fun times(other: TenPuzzleRational): TenPuzzleRational =
        TenPuzzleRational(
            numerator * other.numerator,
            denominator * other.denominator,
        ).reduce()

    fun div(other: TenPuzzleRational): TenPuzzleRational? {
        if (other.isZero()) return null
        return TenPuzzleRational(
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

private sealed interface TenPuzzleEvalState {
    data object NotParsable : TenPuzzleEvalState
    data object DivisionByZero : TenPuzzleEvalState
    data class Ok(val value: TenPuzzleRational) : TenPuzzleEvalState
}

private fun evaluateExpression(tokens: List<TenPuzzleAstToken>): TenPuzzleEvalState {
    val expr = parseToAst(tokens) ?: return TenPuzzleEvalState.NotParsable
    return when (val v = eval(expr)) {
        null -> TenPuzzleEvalState.DivisionByZero
        else -> TenPuzzleEvalState.Ok(v)
    }
}

/**
 * 単項演算子は許可しない．
 *
 * 実装は shunting-yard で AST を構築する．
 */
private fun parseToAst(tokens: List<TenPuzzleAstToken>): TenPuzzleExpr? {
    val exprStack = ArrayDeque<TenPuzzleExpr>()
    val opStack = ArrayDeque<TenPuzzleAstToken>()

    fun applyOp(opToken: TenPuzzleAstToken.Operator): Boolean {
        if (exprStack.size < 2) return false
        val right = exprStack.removeLast()
        val left = exprStack.removeLast()
        exprStack.addLast(TenPuzzleExpr.Bin(opToken.op, left, right))
        return true
    }

    var prev: TenPuzzleAstToken? = null

    for (t in tokens) {
        when (t) {
            is TenPuzzleAstToken.Number -> {
                exprStack.addLast(TenPuzzleExpr.Num(TenPuzzleRational.ofInt(t.value)))
            }

            is TenPuzzleAstToken.Paren -> {
                if (t.isLeft) {
                    opStack.addLast(t)
                } else {
                    var found = false
                    while (opStack.isNotEmpty()) {
                        val top = opStack.removeLast()
                        when (top) {
                            is TenPuzzleAstToken.Operator -> if (!applyOp(top)) return null
                            is TenPuzzleAstToken.Number -> return null
                            is TenPuzzleAstToken.Paren -> {
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

            is TenPuzzleAstToken.Operator -> {
                // 単項演算子禁止：先頭，演算子の直後，'(' の直後は不可
                if (prev == null || prev is TenPuzzleAstToken.Operator || (prev is TenPuzzleAstToken.Paren && prev.isLeft)) {
                    return null
                }

                while (opStack.isNotEmpty()) {
                    val top = opStack.last()
                    if (top is TenPuzzleAstToken.Operator && top.op.precedence >= t.op.precedence) {
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

    if (prev is TenPuzzleAstToken.Operator) return null

    while (opStack.isNotEmpty()) {
        val top = opStack.removeLast()
        when (top) {
            is TenPuzzleAstToken.Operator -> if (!applyOp(top)) return null
            is TenPuzzleAstToken.Number -> return null
            is TenPuzzleAstToken.Paren -> return null
        }
    }

    return exprStack.singleOrNull()
}

private fun eval(expr: TenPuzzleExpr): TenPuzzleRational? {
    return when (expr) {
        is TenPuzzleExpr.Num -> expr.value
        is TenPuzzleExpr.Bin -> {
            val l = eval(expr.left) ?: return null
            val r = eval(expr.right) ?: return null
            when (expr.op) {
                TenPuzzleOp.Plus -> l + r
                TenPuzzleOp.Minus -> l - r
                TenPuzzleOp.Times -> l * r
                TenPuzzleOp.Divide -> l.div(r) ?: return null
            }
        }
    }
}
