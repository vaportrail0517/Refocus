package com.example.refocus.system.overlay.ui.minigame

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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


    val slots: List<SnapshotStateList<TenToken>> =
        remember(seed) {
            List(5) { emptyList<TenToken>().toMutableStateList() }
        }

    var phase by remember(seed) { mutableStateOf(TenPuzzlePhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(60) }

    // タイマーはフェーズ遷移のみを行い，自動終了はしない
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

    val expressionTokens by remember(numbers, slots) {
        derivedStateOf { buildExpressionTokens(numbers, slots) }
    }

    val evalState by remember(expressionTokens) {
        derivedStateOf { evaluateExpression(expressionTokens) }
    }

    // 正解（=10）になった瞬間に編集をロック（自動終了はしない）
    LaunchedEffect(evalState, phase, remainingSeconds) {
        if (phase != TenPuzzlePhase.Playing) return@LaunchedEffect
        if (remainingSeconds <= 0) return@LaunchedEffect
        val v = evalState
        if (v is EvalState.Ok && v.value.isTen()) {
            phase = TenPuzzlePhase.Solved
        }
    }

    val editingEnabled = phase == TenPuzzlePhase.Playing && remainingSeconds > 0

    var transientMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transientMessage) {
        if (transientMessage == null) return@LaunchedEffect
        delay(1_500)
        transientMessage = null
    }

    // ドラッグ状態（ルート座標系のローカル Offset を使う）
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val slotRects = remember { Array<Rect?>(5) { null } }
    var draggingToken by remember { mutableStateOf<TenToken?>(null) }
    var dragPosInRoot by remember { mutableStateOf(Offset.Zero) }
    var hoveredSlotIndex by remember { mutableStateOf<Int?>(null) }

    fun updateHoveredSlot() {
        val pos = dragPosInRoot
        val idx =
            slotRects.indexOfFirst { rect ->
                rect?.contains(pos) == true
            }.takeIf { it >= 0 }
        hoveredSlotIndex = idx
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoords = it },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TenPuzzleHeader(
                phase = phase,
                remainingSeconds = remainingSeconds.coerceAtLeast(0),
            )

            ExpressionArea(
                numbers = numbers,
                slots = slots,
                slotRects = slotRects,
                rootCoords = rootCoords,
                editingEnabled = editingEnabled,
                dragging = draggingToken != null,
                hoveredSlotIndex = hoveredSlotIndex,
                transientMessage = transientMessage,
                onDeleteToken = { slotIndex, tokenIndex ->
                    if (!editingEnabled) return@ExpressionArea
                    val slot = slots.getOrNull(slotIndex) ?: return@ExpressionArea
                    if (tokenIndex !in slot.indices) return@ExpressionArea
                    slot.removeAt(tokenIndex)
                },
            )

            EvaluationText(
                phase = phase,
                evalState = evalState,
            )

            Spacer(modifier = Modifier.weight(1f))

            if (phase == TenPuzzlePhase.Playing) {
                ControlRow(
                    editingEnabled = editingEnabled,
                    onClear = { slots.forEach { it.clear() } },
                )
                Keyboard(
                    enabled = editingEnabled,
                    onStartDrag = { token, startPos ->
                        draggingToken = token
                        dragPosInRoot = startPos
                        updateHoveredSlot()
                    },
                    onDragMove = { delta ->
                        dragPosInRoot += delta
                        updateHoveredSlot()
                    },
                    onDrop = {
                        val token = draggingToken ?: return@Keyboard
                        val idx = hoveredSlotIndex
                        if (editingEnabled && idx != null) {
                            val slot = slots[idx]
                            if (slot.size < 3) {
                                slot.add(token)
                            } else {
                                transientMessage = "そのスロットはこれ以上置けません"
                            }
                        }
                        draggingToken = null
                        hoveredSlotIndex = null
                    },
                    onCancelDrag = {
                        draggingToken = null
                        hoveredSlotIndex = null
                    },
                    rootCoordsProvider = { rootCoords },
                )
            } else {
                ResultFooter(
                    onFinished = onFinished,
                )
            }
        }

        // ドラッグ中のゴースト（視覚補助）
        val ghost = draggingToken
        if (ghost != null) {
            TokenGhost(
                token = ghost,
                position = dragPosInRoot,
            )
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
                    TenPuzzlePhase.Playing -> "記号をドラッグして 10 を作る"
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

@Composable
private fun ExpressionArea(
    numbers: IntArray,
    slots: List<SnapshotStateList<TenToken>>,
    slotRects: Array<Rect?>,
    rootCoords: LayoutCoordinates?,
    editingEnabled: Boolean,
    dragging: Boolean,
    hoveredSlotIndex: Int?,
    onDeleteToken: (slotIndex: Int, tokenIndex: Int) -> Unit,
    transientMessage: String?,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            @Composable
            fun Slot(index: Int) {
                SlotView(
                    tokens = slots[index],
                    isHovered = dragging && hoveredSlotIndex == index,
                    editingEnabled = editingEnabled,
                    onDeleteToken = { tokenIndex -> onDeleteToken(index, tokenIndex) },
                    onGloballyPositioned = { coords ->
                        if (rootCoords == null) return@SlotView
                        val topLeft = rootCoords.localPositionOf(coords, Offset.Zero)
                        val size = coords.size
                        slotRects[index] =
                            Rect(
                                topLeft,
                                topLeft + Offset(size.width.toFloat(), size.height.toFloat()),
                            )
                    },
                )
            }

            Slot(0)
            NumberChip(numbers[0])
            Slot(1)
            NumberChip(numbers[1])
            Slot(2)
            NumberChip(numbers[2])
            Slot(3)
            NumberChip(numbers[3])
            Slot(4)
        }

        if (transientMessage != null) {
            Text(
                text = transientMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "スロットに置けるトークンは最大 3 個です．トークンをタップすると削除できます．",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NumberChip(
    value: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .widthIn(min = 48.dp)
                .requiredHeight(52.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SlotView(
    tokens: List<TenToken>,
    isHovered: Boolean,
    editingEnabled: Boolean,
    onDeleteToken: (index: Int) -> Unit,
    onGloballyPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val border =
        if (isHovered) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }

    Surface(
        modifier =
            modifier
                .sizeIn(minWidth = 86.dp, minHeight = 52.dp)
                .onGloballyPositioned(onGloballyPositioned),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tokens.isEmpty()) {
                Text(
                    text = "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                tokens.forEachIndexed { index, token ->
                    TokenChip(
                        token = token,
                        enabled = editingEnabled,
                        onClick = { onDeleteToken(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenChip(
    token: TenToken,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .sizeIn(minWidth = 24.dp, minHeight = 32.dp)
                .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = token.label,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EvaluationText(
    phase: TenPuzzlePhase,
    evalState: EvalState,
    modifier: Modifier = Modifier,
) {
    val text =
        when (evalState) {
            is EvalState.NotParsable -> "式を評価できません"
            is EvalState.DivisionByZero -> "0 では割れません"
            is EvalState.Ok -> "＝ ${evalState.value.toDisplayString()}"
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
        fontWeight = if (phase == TenPuzzlePhase.Solved) FontWeight.SemiBold else null,
    )
}

@Composable
private fun ControlRow(
    editingEnabled: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = onClear,
            enabled = editingEnabled,
            modifier = Modifier.height(52.dp),
        ) {
            Text(text = "クリア")
        }
    }
}

@Composable
private fun Keyboard(
    enabled: Boolean,
    onStartDrag: (token: TenToken, startPosInRoot: Offset) -> Unit,
    onDragMove: (delta: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancelDrag: () -> Unit,
    rootCoordsProvider: () -> LayoutCoordinates?,
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
            DraggableKey(
                token = TenToken.LParen,
                enabled = enabled,
                onStartDrag = onStartDrag,
                onDragMove = onDragMove,
                onDrop = onDrop,
                onCancelDrag = onCancelDrag,
                rootCoordsProvider = rootCoordsProvider,
                modifier = Modifier.weight(1f),
            )
            DraggableKey(
                token = TenToken.RParen,
                enabled = enabled,
                onStartDrag = onStartDrag,
                onDragMove = onDragMove,
                onDrop = onDrop,
                onCancelDrag = onCancelDrag,
                rootCoordsProvider = rootCoordsProvider,
                modifier = Modifier.weight(1f),
            )
            DraggableKey(
                token = TenToken.Plus,
                enabled = enabled,
                onStartDrag = onStartDrag,
                onDragMove = onDragMove,
                onDrop = onDrop,
                onCancelDrag = onCancelDrag,
                rootCoordsProvider = rootCoordsProvider,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DraggableKey(
                token = TenToken.Minus,
                enabled = enabled,
                onStartDrag = onStartDrag,
                onDragMove = onDragMove,
                onDrop = onDrop,
                onCancelDrag = onCancelDrag,
                rootCoordsProvider = rootCoordsProvider,
                modifier = Modifier.weight(1f),
            )
            DraggableKey(
                token = TenToken.Times,
                enabled = enabled,
                onStartDrag = onStartDrag,
                onDragMove = onDragMove,
                onDrop = onDrop,
                onCancelDrag = onCancelDrag,
                rootCoordsProvider = rootCoordsProvider,
                modifier = Modifier.weight(1f),
            )
            DraggableKey(
                token = TenToken.Divide,
                enabled = enabled,
                onStartDrag = onStartDrag,
                onDragMove = onDragMove,
                onDrop = onDrop,
                onCancelDrag = onCancelDrag,
                rootCoordsProvider = rootCoordsProvider,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DraggableKey(
    token: TenToken,
    enabled: Boolean,
    onStartDrag: (token: TenToken, startPosInRoot: Offset) -> Unit,
    onDragMove: (delta: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancelDrag: () -> Unit,
    rootCoordsProvider: () -> LayoutCoordinates?,
    modifier: Modifier = Modifier,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Surface(
        modifier =
            modifier
                .height(52.dp)
                .onGloballyPositioned { coords = it }
                .pointerInput(token, enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offsetInKey ->
                            val root = rootCoordsProvider() ?: return@detectDragGestures
                            val key = coords ?: return@detectDragGestures
                            val start = root.localPositionOf(key, offsetInKey)
                            onStartDrag(token, start)
                        },
                        onDragEnd = { onDrop() },
                        onDragCancel = { onCancelDrag() },
                        onDrag = { change, dragAmount ->

                            onDragMove(dragAmount)
                        },
                    )
                },
        shape = MaterialTheme.shapes.small,
        color =
            if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        contentColor =
            if (enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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

@Composable
private fun TokenGhost(
    token: TenToken,
    position: Offset,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .offset {
                    IntOffset(position.x.roundToInt(), position.y.roundToInt())
                },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = token.label, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
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

internal enum class TenToken(val label: String) {
    LParen("("),
    RParen(")"),
    Plus("+"),
    Minus("-"),
    Times("×"),
    Divide("÷"),
}

private sealed interface Expr {
    data class Num(val value: Rational) : Expr
    data class Bin(val op: Op, val left: Expr, val right: Expr) : Expr
}

private enum class Op(val precedence: Int) {
    Plus(1),
    Minus(1),
    Times(2),
    Divide(2),
}

private sealed interface Token {
    data class Number(val value: Int) : Token
    data class Operator(val op: Op) : Token
    data class Paren(val isLeft: Boolean) : Token
}

@Stable
private data class Rational(
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
        fun ofInt(v: Int): Rational = Rational(v.toLong(), 1L).reduce()
    }

    fun reduce(): Rational {
        if (numerator == 0L) return Rational(0L, 1L)
        val g = gcd(kotlin.math.abs(numerator), kotlin.math.abs(denominator))
        val sign = if (denominator < 0L) -1L else 1L
        return Rational(sign * (numerator / g), sign * (denominator / g))
    }

    operator fun plus(other: Rational): Rational =
        Rational(
            numerator * other.denominator + other.numerator * denominator,
            denominator * other.denominator,
        ).reduce()

    operator fun minus(other: Rational): Rational =
        Rational(
            numerator * other.denominator - other.numerator * denominator,
            denominator * other.denominator,
        ).reduce()

    operator fun times(other: Rational): Rational =
        Rational(
            numerator * other.numerator,
            denominator * other.denominator,
        ).reduce()

    fun div(other: Rational): Rational? {
        if (other.isZero()) return null
        return Rational(
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
    return if (x == 0L) 1L else kotlin.math.abs(x)
}

private fun buildExpressionTokens(
    numbers: IntArray,
    slots: List<List<TenToken>>,
): List<Token> {
    val out = ArrayList<Token>(4 + 15)

    fun addSlot(index: Int) {
        for (t in slots[index]) {
            out.add(t.toToken())
        }
    }

    addSlot(0)
    out.add(Token.Number(numbers[0]))
    addSlot(1)
    out.add(Token.Number(numbers[1]))
    addSlot(2)
    out.add(Token.Number(numbers[2]))
    addSlot(3)
    out.add(Token.Number(numbers[3]))
    addSlot(4)

    return out
}

private fun TenToken.toToken(): Token =
    when (this) {
        TenToken.LParen -> Token.Paren(isLeft = true)
        TenToken.RParen -> Token.Paren(isLeft = false)
        TenToken.Plus -> Token.Operator(Op.Plus)
        TenToken.Minus -> Token.Operator(Op.Minus)
        TenToken.Times -> Token.Operator(Op.Times)
        TenToken.Divide -> Token.Operator(Op.Divide)
    }

private sealed interface EvalState {
    data object NotParsable : EvalState
    data object DivisionByZero : EvalState
    data class Ok(val value: Rational) : EvalState
}

private fun evaluateExpression(tokens: List<Token>): EvalState {
    val expr = parseToAst(tokens) ?: return EvalState.NotParsable
    return when (val v = eval(expr)) {
        null -> EvalState.DivisionByZero
        else -> EvalState.Ok(v)
    }
}

/**
 * 単項演算子は許可しない。
 *
 * 実装は shunting-yard で AST を構築する。
 */
private fun parseToAst(tokens: List<Token>): Expr? {
    val exprStack = ArrayDeque<Expr>()
    val opStack = ArrayDeque<Token>()

    fun applyOp(opToken: Token.Operator): Boolean {
        if (exprStack.size < 2) return false
        val right = exprStack.removeLast()
        val left = exprStack.removeLast()
        exprStack.addLast(Expr.Bin(opToken.op, left, right))
        return true
    }

    var prev: Token? = null

    for (t in tokens) {
        when (t) {
            is Token.Number -> {
                exprStack.addLast(Expr.Num(Rational.ofInt(t.value)))
            }

            is Token.Paren -> {
                if (t.isLeft) {
                    opStack.addLast(t)
                } else {
                    var found = false
                    while (opStack.isNotEmpty()) {
                        val top = opStack.removeLast()
                        when (top) {
                            is Token.Operator -> if (!applyOp(top)) return null
                            is Token.Paren -> {
                                if (top.isLeft) {
                                    found = true
                                    break
                                } else {
                                    return null
                                }
                            }
                            else -> return null
                        }
                    }
                    if (!found) return null
                }
            }

            is Token.Operator -> {
                // 単項演算子禁止：先頭，演算子の直後，'(' の直後は不可
                if (prev == null || prev is Token.Operator || (prev is Token.Paren && prev.isLeft)) {
                    return null
                }

                while (opStack.isNotEmpty()) {
                    val top = opStack.last()
                    if (top is Token.Operator && top.op.precedence >= t.op.precedence) {
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

    // 末尾が演算子は不可
    if (prev is Token.Operator) return null

    while (opStack.isNotEmpty()) {
        val top = opStack.removeLast()
        when (top) {
            is Token.Operator -> if (!applyOp(top)) return null
            is Token.Paren -> return null
            else -> return null
        }
    }

    return exprStack.singleOrNull()
}

private fun eval(expr: Expr): Rational? {
    return when (expr) {
        is Expr.Num -> expr.value
        is Expr.Bin -> {
            val l = eval(expr.left) ?: return null
            val r = eval(expr.right) ?: return null
            when (expr.op) {
                Op.Plus -> l + r
                Op.Minus -> l - r
                Op.Times -> l * r
                Op.Divide -> l.div(r) ?: return null
            }
        }
    }
}

