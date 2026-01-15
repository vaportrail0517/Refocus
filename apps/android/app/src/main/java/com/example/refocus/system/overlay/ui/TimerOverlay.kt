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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
    var effectBag by remember { mutableStateOf<List<TimerEffectType>>(emptyList()) }

    // 回転エフェクト中のみ，ウィンドウ内部の見切れ対策として一時的に余白を確保する
    var rotateWindowPaddingActive by remember { mutableStateOf(false) }
    var rotatingBoxSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val shakeAmpPx = with(density) { SHAKE_AMPLITUDE_DP.dp.toPx() }

    var lastBucket by remember { mutableStateOf(0L) }
    val random = remember { Random(System.currentTimeMillis()) }
    val intervalSeconds = customize.effectIntervalSeconds
    val intervalMs = intervalSeconds.toLong() * 1000L
    val bucket =
        if (customize.effectsEnabled && intervalSeconds > 0) {
            effectMillis / intervalMs
        } else {
            0L
        }

    // 設定変更時は走行中エフェクトを止めて初期化
    LaunchedEffect(customize.effectsEnabled, intervalSeconds) {
        lastBucket = 0L
        effectJob?.cancel()
        effectJob = null
        lastEffectType = null
        rotateWindowPaddingActive = false

        attention.snapTo(0f)
        blinkAlphaMul.snapTo(1f)
        rotationDeg.snapTo(0f)
        shakeX.snapTo(0f)
        shakeY.snapTo(0f)
    }

    // effectMillis の「一定間隔バケット」の切り替わりで発火させる
    LaunchedEffect(bucket) {
        if (!customize.effectsEnabled || intervalSeconds <= 0) return@LaunchedEffect
        if (bucket <= 0L) {
            // セッション開始直後（0バケット）は鳴らさない．またセッション境界で状態を落とす
            lastBucket = 0L
            effectJob?.cancel()
            effectJob = null
            lastEffectType = null
            rotateWindowPaddingActive = false
            attention.snapTo(0f)
            blinkAlphaMul.snapTo(1f)
            rotationDeg.snapTo(0f)
            shakeX.snapTo(0f)
            shakeY.snapTo(0f)
            return@LaunchedEffect
        }

        if (bucket == lastBucket) return@LaunchedEffect
        lastBucket = bucket
        // 実行中ならこの境界はスキップ（同時実行しない）
        if (effectJob?.isActive == true) return@LaunchedEffect
        val next =
            if (effectBag.isNotEmpty()) {
                effectBag.first().also { effectBag = effectBag.drop(1) }
            } else {
                val bag = makeEffectBag(random = random, last = lastEffectType)
                // 先頭を next として使い，残りを保持する
                effectBag = bag.drop(1)
                bag.first()
            }
        lastEffectType = next
        effectJob =
            launch {
                runEffect(
                    effect = next,
                    attention = attention,
                    blinkAlphaMul = blinkAlphaMul,
                    rotationDeg = rotationDeg,
                    shakeX = shakeX,
                    shakeY = shakeY,
                    shakeAmplitudePx = shakeAmpPx,
                    onRotateWindowPaddingActive = { rotateWindowPaddingActive = it },
                )
            }
    }

    val effectBlend = (attention.value * ATTENTION_BLEND_MAX).coerceIn(0f, 1f)
    val effectColor = lerpColor(baseColor, ATTENTION_RED, effectBlend)
    val bgAlpha = (BASE_BACKGROUND_ALPHA * blinkAlphaMul.value).coerceIn(0f, 1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // 透明オーバーレイ上で shadow + scale が残像を作ることがあるため，
        // オフスクリーン合成したうえで毎フレーム描画領域をクリアしてから描く．
        //
        // TimerBubble は shadow(clip = false) によりレイアウト外へ描画するので，
        // クリア領域を確保するために少しだけガード用の padding を入れている．
        val guard = 16.dp

        // 回転（360度スピン）時にウィンドウ内部で見切れないよう，
        // 回転前の矩形サイズから対角線分の余白を計算して一時的に確保する．
        // （graphicsLayer の回転は計測サイズに反映されないため）
        val (extraPadX, extraPadY) =
            remember(rotatingBoxSize, density) {
                if (rotatingBoxSize.width == 0 || rotatingBoxSize.height == 0) {
                    0.dp to 0.dp
                } else {
                    val w = rotatingBoxSize.width.toFloat()
                    val h = rotatingBoxSize.height.toFloat()
                    val diag = kotlin.math.sqrt(w * w + h * h)
                    val padXpx = ((diag - w) / 2f).coerceAtLeast(0f)
                    val padYpx = ((diag - h) / 2f).coerceAtLeast(0f)
                    with(density) { padXpx.toDp() to padYpx.toDp() }
                }
            }

        val padX = if (rotateWindowPaddingActive) extraPadX else 0.dp
        val padY = if (rotateWindowPaddingActive) extraPadY else 0.dp

        Box(
            modifier = Modifier.padding(horizontal = padX, vertical = padY),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(guard)
                        .onSizeChanged { rotatingBoxSize = it }
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale,
                            rotationZ = rotationDeg.value,
                            translationX = shakeX.value,
                            translationY = shakeY.value,
                            transformOrigin = TransformOrigin(0.5f, 0.5f),
                            compositingStrategy = CompositingStrategy.Offscreen,
                        )
                        .drawWithContent {
                            // 透明レイヤでフレーム間の残像が残るのを防ぐ
                            drawRect(color = Color.Transparent, blendMode = BlendMode.Clear)
                            drawContent()
                        },
            ) {
                TimerBubble(
                    text = text,
                    backgroundColor = effectColor.copy(alpha = bgAlpha),
                    size = size,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private enum class TimerEffectType {
    Blink,
    Rotate,
    Shake,
}

private fun makeEffectBag(
    random: Random,
    last: TimerEffectType?,
): List<TimerEffectType> {
    val bag = TimerEffectType.entries.toMutableList()
    bag.shuffle(random)

    // bag の先頭が直前と同じだと，バケット境界で「たまたま連続」に見えやすいので避ける
    if (last != null && bag.size > 1 && bag.first() == last) {
        val swapIndex = bag.indexOfFirst { it != last }
        if (swapIndex >= 1) {
            val tmp = bag[0]
            bag[0] = bag[swapIndex]
            bag[swapIndex] = tmp
        }
    }
    return bag
}

private suspend fun runEffect(
    effect: TimerEffectType,
    attention: Animatable<Float, *>,
    blinkAlphaMul: Animatable<Float, *>,
    rotationDeg: Animatable<Float, *>,
    shakeX: Animatable<Float, *>,
    shakeY: Animatable<Float, *>,
    shakeAmplitudePx: Float,
    onRotateWindowPaddingActive: (Boolean) -> Unit,
) {
    // 初期化
    onRotateWindowPaddingActive(false)
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
            onRotateWindowPaddingActive(false)
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
                    onRotateWindowPaddingActive(true)
                    try {
                        // その場で 360 度回転するスピン．画面外にはみ出してもよい前提．
                        //
                        // 端末側の「Animator duration scale」が 0（アニメーション無効）の場合，
                        // tween/Animatable の animateTo が即時完了してしまい，見た目として回転が発生しない．
                        // そこで withFrameNanos によるフレーム駆動で回転角を更新し，端末設定に依存せず回す．
                        val spins = 1
                        val totalMs = (ROTATE_SPIN_DURATION_MS * spins).coerceAtLeast(1)
                        rotationDeg.snapTo(0f)

                        val start = withFrameNanos { it }
                        while (isActive) {
                            val now = withFrameNanos { it }
                            val elapsedMs = (now - start) / 1_000_000f
                            val p = (elapsedMs / totalMs.toFloat()).coerceIn(0f, 1f)
                            rotationDeg.snapTo(360f * spins * p)
                            if (p >= 1f) break
                        }

                        // 360度は見た目上 0度と同じなので，状態を正規化して次回を安定させる
                        rotationDeg.snapTo(0f)
                    } finally {
                        onRotateWindowPaddingActive(false)
                    }
                }

                TimerEffectType.Shake -> {
                    val a = shakeAmplitudePx
                    val sequence = listOf(
                        1.0f,
                        -1.0f,
                        0.85f,
                        -0.85f,
                        0.7f,
                        -0.7f,
                        0.55f,
                        -0.55f,
                        0.4f,
                        -0.4f,
                        0.25f,
                        -0.25f,
                        0f,
                    )
                    for (m in sequence) {
                        shakeX.animateTo(
                            targetValue = a * m,
                            animationSpec = tween(durationMillis = SHAKE_STEP_MS, easing = FastOutSlowInEasing),
                        )
                    }
                    shakeY.snapTo(0f)
                }
            }
        }

        attentionJob.join()
        effectJob.join()
    }

    // 念のため終端を整える
    onRotateWindowPaddingActive(false)
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

private const val EFFECT_TOTAL_MS: Int = 2800
private const val EFFECT_RAMP_IN_MS: Int = 300
private const val EFFECT_RAMP_OUT_MS: Int = 420

private const val BLINK_HALF_PERIOD_MS: Int = 280
private const val BLINK_CYCLES: Int = 5
private const val BLINK_MIN_MUL: Float = 0.4f

private const val ROTATE_SPIN_DURATION_MS: Int = 900

private const val ROTATE_HALF_PERIOD_MS: Int = 250
private const val ROTATE_SWINGS: Int = 4
private const val ROTATE_SETTLE_MS: Int = 400
private const val ROTATE_AMPLITUDE_DEG: Float = 10f

private const val SHAKE_STEP_MS: Int = 215
private const val SHAKE_AMPLITUDE_DP: Float = 6f

private const val PULSE_PERIOD_MS: Int = 3200
private const val PULSE_AMPLITUDE: Float = 0.04f

@Composable
private fun rememberPulseScale(enabled: Boolean): Float {
    // システムの Animator duration scale（開発者オプション）に依存せず，
    // enabled のときだけ一定周期で呼吸させる．
    // withFrameNanos を使うことで，duration が 0 扱いになってもスピンループにならない．
    val scaleState = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            scaleState.floatValue = 1f
            return@LaunchedEffect
        }
        val periodNs = PULSE_PERIOD_MS.toLong() * 1_000_000L
        val startNs = withFrameNanos { it }
        while (isActive) {
            val nowNs = withFrameNanos { it }
            val elapsedNs = nowNs - startNs
            // 0..1 の位相に正規化して cos 波で呼吸（開始時は縮小側）
            val normalized = (elapsedNs % periodNs).toDouble() / periodNs.toDouble()
            val phase = normalized * 2.0 * Math.PI
            val scale = 1.0 - (PULSE_AMPLITUDE.toDouble() * kotlin.math.cos(phase))

            scaleState.floatValue = scale.toFloat()
        }
    }
    return scaleState.floatValue
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
                )
                .background(
                    color = backgroundColor,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
