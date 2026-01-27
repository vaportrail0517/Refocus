package com.example.refocus.ui.minigame.games.romanizationquiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.ui.minigame.components.MiniGameHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TIME_LIMIT_SECONDS = 40
private const val FEEDBACK_MILLIS = 420L

private enum class Phase {
    Playing,
    Feedback,
    Result,
}

private enum class OptionEmphasis {
    None,
    Correct,
    WrongSelected,
}

private sealed interface PlanLoadState {
    data object Loading : PlanLoadState
    data class Loaded(val plan: RomanizationQuizSessionPlan) : PlanLoadState
    data class Error(val message: String) : PlanLoadState
}

@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loadState by remember(seed) { mutableStateOf<PlanLoadState>(PlanLoadState.Loading) }

    LaunchedEffect(seed) {
        loadState = PlanLoadState.Loading
        val result =
            runCatching {
                withContext(Dispatchers.IO) {
                    val packs = RomanizationQuizPackLoader.loadAllPacks(context.assets)
                    generateRomanizationQuizSessionPlan(
                        seed = seed,
                        packs = packs,
                        timeLimitSeconds = TIME_LIMIT_SECONDS,
                    )
                }
            }
        loadState =
            result.fold(
                onSuccess = { PlanLoadState.Loaded(it) },
                onFailure = { PlanLoadState.Error("データの読み込みに失敗しました．") },
            )
    }

    when (val s = loadState) {
        is PlanLoadState.Loading -> {
            LoadingScreen(onFinished = onFinished, modifier = modifier.fillMaxSize())
        }

        is PlanLoadState.Error -> {
            ErrorScreen(message = s.message, onFinished = onFinished, modifier = modifier.fillMaxSize())
        }

        is PlanLoadState.Loaded -> {
            GameWithPlan(
                seed = seed,
                plan = s.plan,
                onFinished = onFinished,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun LoadingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniGameHeader(
            title = "英語表記推理クイズ",
            subtitle = "データ読み込み中",
            rightTop = "",
            rightBottom = "",
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "読み込み中です．",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onFinished,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniGameHeader(
            title = "英語表記推理クイズ",
            subtitle = "エラー",
            rightTop = "",
            rightBottom = "",
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onFinished,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}

@Composable
private fun GameWithPlan(
    seed: Long,
    plan: RomanizationQuizSessionPlan,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var phase by remember(seed, plan.pack.packId) { mutableStateOf(Phase.Playing) }
    var currentIndex by remember(seed, plan.pack.packId) { mutableIntStateOf(0) }
    var answeredCount by remember(seed, plan.pack.packId) { mutableIntStateOf(0) }
    var correctCount by remember(seed, plan.pack.packId) { mutableIntStateOf(0) }
    var remainingSeconds by remember(seed, plan.pack.packId) { mutableIntStateOf(plan.timeLimitSeconds) }

    var lastSelectedIndex by remember(seed, plan.pack.packId) { mutableIntStateOf(-1) }
    var lastWasCorrect by remember(seed, plan.pack.packId) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(seed, plan.pack.packId) {
        while (true) {
            if (phase == Phase.Result) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase == Phase.Result) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0) {
                phase = Phase.Result
                break
            }
        }
    }

    LaunchedEffect(phase, currentIndex, seed, plan.pack.packId) {
        if (phase != Phase.Feedback) return@LaunchedEffect
        delay(FEEDBACK_MILLIS)
        if (remainingSeconds <= 0) {
            phase = Phase.Result
            return@LaunchedEffect
        }
        val next = currentIndex + 1
        if (next >= plan.rounds.size) {
            phase = Phase.Result
            return@LaunchedEffect
        }
        currentIndex = next
        lastSelectedIndex = -1
        lastWasCorrect = null
        phase = Phase.Playing
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val progressLabel = "${answeredCount.coerceAtMost(plan.rounds.size)}/${plan.rounds.size}"
        MiniGameHeader(
            title = "英語表記推理クイズ",
            subtitle = "${plan.pack.displayName}・${plan.pack.romanizationStandard}",
            rightTop = "${remainingSeconds}s",
            rightBottom = progressLabel,
        )

        when (phase) {
            Phase.Result -> {
                ResultScreen(
                    correctCount = correctCount,
                    total = plan.rounds.size,
                    onFinished = onFinished,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                val round = plan.rounds.getOrNull(currentIndex)
                if (round == null) {
                    ResultScreen(
                        correctCount = correctCount,
                        total = plan.rounds.size,
                        onFinished = onFinished,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    QuestionScreen(
                        pack = plan.pack,
                        round = round,
                        phase = phase,
                        lastSelectedIndex = lastSelectedIndex,
                        lastWasCorrect = lastWasCorrect,
                        onSelect = { selectedIndex ->
                            if (phase != Phase.Playing) return@QuestionScreen
                            lastSelectedIndex = selectedIndex
                            val correct = selectedIndex == round.correctIndex
                            lastWasCorrect = correct
                            answeredCount += 1
                            if (correct) correctCount += 1
                            phase = Phase.Feedback
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionScreen(
    pack: LanguagePack,
    round: RomanizationQuizRound,
    phase: Phase,
    lastSelectedIndex: Int,
    lastWasCorrect: Boolean?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRtl = pack.direction == TextDirection.RTL
    val scriptAlign = if (isRtl) TextAlign.End else TextAlign.Start
    val scroll = rememberScrollState()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 上部はスクロール領域（小さい画面でも選択肢を見切らせない）
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HintPanel(
                hintPairs = round.hintPairs,
                scriptAlign = scriptAlign,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "この単語の英語表記はどれでしょうか．",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = round.questionScript,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                textAlign = if (isRtl) TextAlign.End else TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (phase == Phase.Feedback && lastWasCorrect != null) {
            val msg = if (lastWasCorrect) "正解" else "不正解"
            Text(
                text = msg,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        OptionsGrid(
            options = round.options,
            correctIndex = round.correctIndex,
            selectedIndex = lastSelectedIndex,
            enabled = phase == Phase.Playing,
            showFeedback = phase == Phase.Feedback,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HintPanel(
    hintPairs: List<RomanizationQuizHintPair>,
    scriptAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "ヒント",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hintPairs.isEmpty()) {
                    Text(
                        text = "ヒントが準備できませんでした．",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // 2 列で詰めて表示し，高さを抑える
                    val rows = hintPairs.chunked(2)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { pair ->
                                HintCard(
                                    script = pair.script,
                                    roman = pair.roman,
                                    scriptAlign = scriptAlign,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintCard(
    script: String,
    roman: String,
    scriptAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = script,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = scriptAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = roman,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OptionsGrid(
    options: List<String>,
    correctIndex: Int,
    selectedIndex: Int,
    enabled: Boolean,
    showFeedback: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(options.take(2), options.drop(2).take(2))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEachIndexed { colIdx, label ->
                    val index = rowIdx * 2 + colIdx
                    val emphasis =
                        if (!showFeedback) {
                            OptionEmphasis.None
                        } else if (index == correctIndex) {
                            OptionEmphasis.Correct
                        } else if (index == selectedIndex) {
                            OptionEmphasis.WrongSelected
                        } else {
                            OptionEmphasis.None
                        }

                    OptionButton(
                        label = label,
                        enabled = enabled,
                        emphasis = emphasis,
                        onClick = { onSelect(index) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    label: String,
    enabled: Boolean,
    emphasis: OptionEmphasis,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

    val textContent: @Composable () -> Unit = {
        Text(
            text = label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (emphasis) {
        OptionEmphasis.Correct -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                contentPadding = padding,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                modifier = modifier,
            ) {
                textContent()
            }
        }

        OptionEmphasis.WrongSelected -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                contentPadding = padding,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                modifier = modifier,
            ) {
                textContent()
            }
        }

        else -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                contentPadding = padding,
                modifier = modifier,
            ) {
                textContent()
            }
        }
    }
}

@Composable
private fun ResultScreen(
    correctCount: Int,
    total: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.heightIn(min = 12.dp))

        Text(
            text = "結果",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "正解 ${correctCount.coerceAtLeast(0)}/${total.coerceAtLeast(1)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onFinished,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}
