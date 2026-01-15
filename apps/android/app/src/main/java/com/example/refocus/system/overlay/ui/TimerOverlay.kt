package com.example.refocus.system.overlay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.util.formatDurationForTimerBubble
import com.example.refocus.ui.util.interpolateColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@Deprecated(
    message = "Use TimerOverlay(customize, visualMillis, effectMillis, text) instead.",
    replaceWith =
        ReplaceWith(
            "TimerOverlay(customize = customize, visualMillis = visualMillis, effectMillis = 0L, text = formatDurationForTimerBubble(displayMillis), modifier = modifier)",
        ),
)
@Composable
fun TimerOverlay(
    customize: Customize,
    displayMillis: Long,
    visualMillis: Long,
    modifier: Modifier = Modifier,
) {
    TimerOverlay(
        customize = customize,
        visualMillis = visualMillis,
        effectMillis = 0L,
        text = formatDurationForTimerBubble(displayMillis),
        modifier = modifier,
    )
}

@Composable
fun TimerOverlay(
    customize: Customize,
    visualMillis: Long,
    effectMillis: Long = 0L,
    text: String,
    modifier: Modifier = Modifier,
) {
    // 既存実装の「フォントサイズを時間経過で補間する」思想は維持しつつ，演出時間は秒単位に統一する
    val minSize: Dp = (customize.minFontSizeSp / 2f).dp
    val maxSize: Dp = (customize.maxFontSizeSp / 2f).dp

    val visualSeconds = visualMillis / 1000f
    val denomSeconds = customize.timeToMaxSeconds

    // 0秒なら「即座に最大サイズ」とみなす
    val raw =
        if (denomSeconds <= 0) {
            1f
        } else {
            (visualSeconds / denomSeconds.toFloat()).coerceIn(0f, 1f)
        }

    val p =
        when (customize.growthMode) {
            TimerGrowthMode.Linear -> raw
            TimerGrowthMode.FastToSlow -> kotlin.math.sqrt(raw)
            TimerGrowthMode.SlowToFast -> raw * raw
            TimerGrowthMode.SlowFastSlow -> {
                // 0..1 を滑らかに（スムーズステップ）
                3f * raw * raw - 2f * raw * raw * raw
            }
        }

    val targetSize =
        if (customize.baseSizeAnimEnabled) {
            minSize + (maxSize - minSize) * p
        } else {
            minSize
        }

    val size by
        animateDpAsState(
            targetValue = targetSize,
            animationSpec = tween(durationMillis = 350),
            label = "timer_size",
        )

    val animatedBaseColor =
        when (customize.colorMode) {
            TimerColorMode.Fixed -> Color(customize.fixedColorArgb)
            TimerColorMode.GradientTwo -> {
                val start = Color(customize.gradientStartColorArgb)
                val end = Color(customize.gradientEndColorArgb)
                interpolateColor(start, end, p)
            }

            TimerColorMode.GradientThree -> {
                val start = Color(customize.gradientStartColorArgb)
                val mid = Color(customize.gradientMiddleColorArgb)
                val end = Color(customize.gradientEndColorArgb)
                if (p < 0.5f) {
                    interpolateColor(start, mid, p / 0.5f)
                } else {
                    interpolateColor(mid, end, (p - 0.5f) / 0.5f)
                }
            }
        }

    val fixedBaseColor =
        when (customize.colorMode) {
            TimerColorMode.Fixed -> Color(customize.fixedColorArgb)
            TimerColorMode.GradientTwo,
            TimerColorMode.GradientThree -> Color(customize.gradientStartColorArgb)
        }

    val targetBaseColor =
        if (customize.baseColorAnimEnabled) {
            animatedBaseColor
        } else {
            fixedBaseColor
        }

    val baseColor by
        animateColorAsState(
            targetValue = targetBaseColor,
            animationSpec = tween(durationMillis = 400),
            label = "timer_base_color",
        )

    val pulseScale = rememberPulseScale(enabled = customize.basePulseEnabled)

    // エフェクト（一定間隔ランダム，提案とは無関係）
    val attention = remember { Animatable(0f) }
    val blinkAlphaMul = remember { Animatable(1f) }
    val rotationDeg = remember { Animatable(0f) }
    val shakeX = remember { Animatable(0f) }
    val shakeY = remember { Animatable(0f) }

    var effectJob by remember { mutableStateOf<Job?>(null) }
    var lastEffectType by remember { mutableStateOf<TimerEffectType?>(null) }

    val density = LocalDensity.current
    val shakeAmpPx = with(density) { SHAKE_AMPLITUDE_DP.dp.toPx() }

    LaunchedEffect(customize.effectsEnabled, customize.effectIntervalSeconds) {
        // 設定変更時は走行中エフェクトを止めて初期化
        effectJob?.cancel()
        effectJob = null
        lastEffectType = null

        attention.snapTo(0f)
        blinkAlphaMul.snapTo(1f)
        rotationDeg.snapTo(0f)
        shakeX.snapTo(0f)
        shakeY.snapTo(0f)

        val intervalSeconds = customize.effectIntervalSeconds
        if (!customize.effectsEnabled || intervalSeconds <= 0) {
            return@LaunchedEffect
        }

        val intervalMs = intervalSeconds.toLong() * 1000L
        val random = Random(System.currentTimeMillis())

        snapshotFlow { effectMillis / intervalMs }
            .distinctUntilChanged()
            .collect { bucket ->
                if (bucket <= 0L) {
                    // セッション開始直後（0バケット）は鳴らさない．またセッション境界で状態を落とす
                    effectJob?.cancel()
                    effectJob = null
                    lastEffectType = null
                    attention.snapTo(0f)
                    blinkAlphaMul.snapTo(1f)
                    rotationDeg.snapTo(0f)
                    shakeX.snapTo(0f)
                    shakeY.snapTo(0f)
                    return@collect
                }

                // 実行中ならこの境界はスキップ（同時実行しない）
                if (effectJob?.isActive == true) {
                    return@collect
                }

                val next = chooseNextEffect(lastEffectType, random)
                lastEffectType = next

                effectJob = launch {
                    runEffect(
                        effect = next,
                        attention = attention,
                        blinkAlphaMul = blinkAlphaMul,
                        rotationDeg = rotationDeg,
                        shakeX = shakeX,
                        shakeY = shakeY,
                        shakeAmplitudePx = shakeAmpPx,
                    )
                }
            }
    }

    val effectBlend = (attention.value * ATTENTION_BLEND_MAX).coerceIn(0f, 1f)
    val effectColor = lerpColor(baseColor, ATTENTION_RED, effectBlend)
    val bgAlpha = (BASE_BACKGROUND_ALPHA * blinkAlphaMul.value).coerceIn(0f, 1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        TimerBubble(
            text = text,
            backgroundColor = effectColor.copy(alpha = bgAlpha),
            size = size,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.graphicsLayer(
                    scaleX = pulseScale,
                    scaleY = pulseScale,
                    rotationZ = rotationDeg.value,
                    translationX = shakeX.value,
                    translationY = shakeY.value,
                ),
        )
    }
}

private enum class TimerEffectType {
    Blink,
    Rotate,
    Shake,
}

private fun chooseNextEffect(last: TimerEffectType?, random: Random): TimerEffectType {
    val all = listOf(TimerEffectType.Blink, TimerEffectType.Rotate, TimerEffectType.Shake)
    if (last == null) return all[random.nextInt(all.size)]

    val candidates = all.filter { it != last }
    return candidates[random.nextInt(candidates.size)]
}

private suspend fun runEffect(
    effect: TimerEffectType,
    attention: Animatable<Float, *>,
    blinkAlphaMul: Animatable<Float, *>,
    rotationDeg: Animatable<Float, *>,
    shakeX: Animatable<Float, *>,
    shakeY: Animatable<Float, *>,
    shakeAmplitudePx: Float,
) {
    // 初期化
    attention.snapTo(0f)
    blinkAlphaMul.snapTo(1f)
    rotationDeg.snapTo(0f)
    shakeX.snapTo(0f)
    shakeY.snapTo(0f)

    coroutineScope {
        val attentionJob = launch {
            val hold = (EFFECT_TOTAL_MS - EFFECT_RAMP_IN_MS - EFFECT_RAMP_OUT_MS).coerceAtLeast(0)
            attention.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = EFFECT_RAMP_IN_MS, easing = FastOutSlowInEasing),
            )
            delay(hold.toLong())
            attention.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = EFFECT_RAMP_OUT_MS, easing = FastOutSlowInEasing),
            )
        }

        val effectJob = launch {
            when (effect) {
                TimerEffectType.Blink -> {
                    repeat(BLINK_CYCLES) {
                        blinkAlphaMul.animateTo(
                            targetValue = BLINK_MIN_MUL,
                            animationSpec = tween(durationMillis = BLINK_HALF_PERIOD_MS, easing = FastOutSlowInEasing),
                        )
                        blinkAlphaMul.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = BLINK_HALF_PERIOD_MS, easing = FastOutSlowInEasing),
                        )
                    }
                    blinkAlphaMul.snapTo(1f)
                }

                TimerEffectType.Rotate -> {
                    rotationDeg.animateTo(
                        targetValue = ROTATE_AMPLITUDE_DEG,
                        animationSpec = tween(durationMillis = ROTATE_STEP_MS, easing = FastOutSlowInEasing),
                    )
                    rotationDeg.animateTo(
                        targetValue = -ROTATE_AMPLITUDE_DEG,
                        animationSpec = tween(durationMillis = ROTATE_STEP_MS * 2, easing = FastOutSlowInEasing),
                    )
                    rotationDeg.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = ROTATE_STEP_MS, easing = FastOutSlowInEasing),
                    )
                }

                TimerEffectType.Shake -> {
                    val a = shakeAmplitudePx
                    shakeX.animateTo(
                        targetValue = a,
                        animationSpec = tween(durationMillis = SHAKE_STEP_MS, easing = FastOutSlowInEasing),
                    )
                    shakeX.animateTo(
                        targetValue = -a,
                        animationSpec = tween(durationMillis = SHAKE_STEP_MS, easing = FastOutSlowInEasing),
                    )
                    shakeX.animateTo(
                        targetValue = a * 0.6f,
                        animationSpec = tween(durationMillis = SHAKE_STEP_MS, easing = FastOutSlowInEasing),
                    )
                    shakeX.animateTo(
                        targetValue = -a * 0.6f,
                        animationSpec = tween(durationMillis = SHAKE_STEP_MS, easing = FastOutSlowInEasing),
                    )
                    shakeX.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = SHAKE_STEP_MS, easing = FastOutSlowInEasing),
                    )
                    shakeY.snapTo(0f)
                }
            }
        }

        attentionJob.join()
        effectJob.join()
    }

    // 念のため終端を整える
    attention.snapTo(0f)
    blinkAlphaMul.snapTo(1f)
    rotationDeg.snapTo(0f)
    shakeX.snapTo(0f)
    shakeY.snapTo(0f)
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt,
    )
}

private const val BASE_BACKGROUND_ALPHA: Float = 0.7f
private const val ATTENTION_BLEND_MAX: Float = 0.65f
private val ATTENTION_RED: Color = Color(0xFFFF3B30.toInt())

private const val EFFECT_TOTAL_MS: Int = 900
private const val EFFECT_RAMP_IN_MS: Int = 120
private const val EFFECT_RAMP_OUT_MS: Int = 180

private const val BLINK_HALF_PERIOD_MS: Int = 170
private const val BLINK_CYCLES: Int = 2
private const val BLINK_MIN_MUL: Float = 0.4f

private const val ROTATE_STEP_MS: Int = 160
private const val ROTATE_AMPLITUDE_DEG: Float = 12f

private const val SHAKE_STEP_MS: Int = 70
private const val SHAKE_AMPLITUDE_DP: Float = 6f

private const val PULSE_PERIOD_MS: Int = 2600
private const val PULSE_AMPLITUDE: Float = 0.04f

@Composable
private fun rememberPulseScale(enabled: Boolean): Float {
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            pulse.snapTo(1f)
            return@LaunchedEffect
        }

        val up = 1f + PULSE_AMPLITUDE
        val down = 1f - PULSE_AMPLITUDE

        while (isActive) {
            pulse.animateTo(
                targetValue = up,
                animationSpec = tween(durationMillis = PULSE_PERIOD_MS / 2, easing = FastOutSlowInEasing),
            )
            pulse.animateTo(
                targetValue = down,
                animationSpec = tween(durationMillis = PULSE_PERIOD_MS / 2, easing = FastOutSlowInEasing),
            )
        }
    }

    return pulse.value
}

@Composable
private fun TimerBubble(
    text: String,
    backgroundColor: Color,
    size: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val textColor = chooseOnColorForBackground(backgroundColor)

    Box(
        modifier =
            modifier
                .shadow(
                    elevation = 4.dp,
                    shape = MaterialTheme.shapes.medium,
                    clip = false,
                ).background(
                    color = backgroundColor,
                    shape = MaterialTheme.shapes.medium,
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = textStyle.copy(fontSize = fontSize),
        )
    }
}

private fun chooseOnColorForBackground(bg: Color): Color {
    val r = (bg.red * 255).toInt()
    val g = (bg.green * 255).toInt()
    val b = (bg.blue * 255).toInt()

    val brightness = (r * 299 + g * 587 + b * 114) / 1000
    return if (brightness >= 128) Color.Black else Color.White
}
