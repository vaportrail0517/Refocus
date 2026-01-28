package com.example.refocus.ui.minigame.games.stroop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

/**
 * ストループ効果を使った四択ミニゲーム。
 *
 * 要件：
 * - ゲーム内で結果を domain に返さない（閉じた事実だけ）
 * - seed から決定的に問題列を生成する
 * - 入力は画面内ボタンのみ
 * - overlay 表示でも安全に動く（Activity 依存 API を使わない）
 */
@Composable
fun Game(
    seed: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec =
        remember(seed) {
            StroopSpec(
                timeLimitSeconds = 25,
                maxQuestions = 20,
                trialPoolSize = 40,
                incongruentPercent = 70,
                wordTaskPercent = 20,
            )
        }

    val trials =
        remember(seed) {
            generateTrials(
                seed = seed,
                poolSize = spec.trialPoolSize,
                incongruentPercent = spec.incongruentPercent,
                wordTaskPercent = spec.wordTaskPercent,
            )
        }

    var phase by remember(seed) { mutableStateOf(StroopPhase.Playing) }
    var remainingSeconds by remember(seed) { mutableIntStateOf(spec.timeLimitSeconds) }

    var trialIndex by remember(seed) { mutableIntStateOf(0) }
    var answeredCount by remember(seed) { mutableIntStateOf(0) }
    var correctCount by remember(seed) { mutableIntStateOf(0) }

    var currentStreak by remember(seed) { mutableIntStateOf(0) }
    var maxStreak by remember(seed) { mutableIntStateOf(0) }

    var feedbackText by remember(seed) { mutableStateOf<String?>(null) }

    val currentTrial: StroopTrial? =
        trials.getOrNull(trialIndex)?.takeIf { phase != StroopPhase.Result }

    val canAnswer = phase == StroopPhase.Playing && remainingSeconds > 0 && currentTrial != null

    // 制限時間カウントダウン
    LaunchedEffect(seed) {
        while (true) {
            if (phase == StroopPhase.Result) break
            if (remainingSeconds <= 0) break
            delay(1_000)
            if (phase == StroopPhase.Result) break
            remainingSeconds -= 1
            if (remainingSeconds <= 0) {
                phase = StroopPhase.Result
            }
        }
    }

    // フィードバック（短時間）→ 次問題 or 結果へ遷移
    LaunchedEffect(phase, trialIndex) {
        if (phase != StroopPhase.Feedback) return@LaunchedEffect
        delay(250)
        if (phase != StroopPhase.Feedback) return@LaunchedEffect

        feedbackText = null

        val nextIndex = trialIndex + 1
        val questionLimitReached = answeredCount >= spec.maxQuestions
        val noNextTrial = nextIndex >= trials.size

        phase =
            if (remainingSeconds <= 0 || questionLimitReached || noNextTrial) {
                StroopPhase.Result
            } else {
                trialIndex = nextIndex
                StroopPhase.Playing
            }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(
            remainingSeconds = remainingSeconds,
            answeredCount = answeredCount,
            correctCount = correctCount,
            task = currentTrial?.task,
        )

        StimulusArea(
            trial = currentTrial,
            phase = phase,
            feedbackText = feedbackText,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
        )

        when (phase) {
            StroopPhase.Playing,
            StroopPhase.Feedback,
            -> {
                AnswerGrid(
                    enabled = canAnswer,
                    onSelect = { selected ->
                        val t = currentTrial ?: return@AnswerGrid
                        val correct =
                            when (t.task) {
                                StroopTask.Ink -> t.ink
                                StroopTask.Word -> t.word
                            }

                        val isCorrect = selected == correct
                        answeredCount += 1
                        if (isCorrect) {
                            correctCount += 1
                            currentStreak += 1
                            maxStreak = max(maxStreak, currentStreak)
                            feedbackText = "正解"
                        } else {
                            currentStreak = 0
                            feedbackText = "不正解"
                        }

                        phase = StroopPhase.Feedback
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            StroopPhase.Result -> {
                ResultFooter(
                    answeredCount = answeredCount,
                    correctCount = correctCount,
                    maxStreak = maxStreak,
                    onFinished = onFinished,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Header(
    remainingSeconds: Int,
    answeredCount: Int,
    correctCount: Int,
    task: StroopTask?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "ストループ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            val instruction =
                when (task) {
                    StroopTask.Ink -> "指示：色で答える"
                    StroopTask.Word -> "指示：意味で答える"
                    null -> ""
                }

            if (instruction.isNotBlank()) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TimeBadge(
                seconds = remainingSeconds.coerceAtLeast(0),
            )

            Text(
                text = "正解 $correctCount/$answeredCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeBadge(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = "残り ${seconds}秒",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StimulusArea(
    trial: StroopTrial?,
    phase: StroopPhase,
    feedbackText: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (trial == null || phase == StroopPhase.Result) {
                Text(
                    text = "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Box
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TaskChip(task = trial.task)

                Text(
                    text = trial.word.label,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = trial.ink.color,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))

                if (!feedbackText.isNullOrBlank()) {
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskChip(
    task: StroopTask,
    modifier: Modifier = Modifier,
) {
    val label =
        when (task) {
            StroopTask.Ink -> "色"
            StroopTask.Word -> "意味"
        }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AnswerGrid(
    enabled: Boolean,
    onSelect: (StroopColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = StroopColor.entries

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AnswerButton(
                label = options[0].label,
                enabled = enabled,
                onClick = { onSelect(options[0]) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
            AnswerButton(
                label = options[1].label,
                enabled = enabled,
                onClick = { onSelect(options[1]) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AnswerButton(
                label = options[2].label,
                enabled = enabled,
                onClick = { onSelect(options[2]) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
            AnswerButton(
                label = options[3].label,
                enabled = enabled,
                onClick = { onSelect(options[3]) },
                modifier = Modifier.weight(1f).height(52.dp),
            )
        }
    }
}

@Composable
private fun AnswerButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ResultFooter(
    answeredCount: Int,
    correctCount: Int,
    maxStreak: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "結果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "正解：$correctCount/$answeredCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "最大連続正解：$maxStreak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Button(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "閉じる")
        }
    }
}

private data class StroopSpec(
    val timeLimitSeconds: Int,
    val maxQuestions: Int,
    val trialPoolSize: Int,
    val incongruentPercent: Int,
    val wordTaskPercent: Int,
)

private enum class StroopPhase {
    Playing,
    Feedback,
    Result,
}

private enum class StroopTask {
    Ink,
    Word,
}

private enum class StroopColor(
    val label: String,
    val color: Color,
) {
    Red(label = "赤", color = Color(0xFFD32F2F)),
    Blue(label = "青", color = Color(0xFF1976D2)),
    Yellow(label = "黄", color = Color(0xFFF9A825)),
    Purple(label = "紫", color = Color(0xFF7B1FA2)),
}

private data class StroopTrial(
    val task: StroopTask,
    val word: StroopColor,
    val ink: StroopColor,
)

private fun generateTrials(
    seed: Long,
    poolSize: Int,
    incongruentPercent: Int,
    wordTaskPercent: Int,
): List<StroopTrial> {
    val rng = Random(seed)
    val colors = StroopColor.entries

    fun pickTask(): StroopTask {
        val v = rng.nextInt(100)
        return if (v < wordTaskPercent) StroopTask.Word else StroopTask.Ink
    }

    fun pickWord(): StroopColor = colors[rng.nextInt(colors.size)]

    fun pickInk(word: StroopColor): StroopColor {
        val congruent = rng.nextInt(100) >= incongruentPercent
        return if (congruent) {
            word
        } else {
            val others = colors.filter { it != word }
            others[rng.nextInt(others.size)]
        }
    }

    return List(poolSize) {
        val word = pickWord()
        StroopTrial(
            task = pickTask(),
            word = word,
            ink = pickInk(word),
        )
    }
}
