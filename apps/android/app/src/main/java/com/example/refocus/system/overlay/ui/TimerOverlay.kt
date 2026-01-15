package com.example.refocus.system.overlay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.isActive
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.util.formatDurationForTimerBubble
import com.example.refocus.ui.util.interpolateColor

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

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        TimerBubble(
            text = text,
            backgroundColor = baseColor.copy(alpha = 0.7f),
            size = size,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.graphicsLayer(scaleX = pulseScale, scaleY = pulseScale),
        )
    }
}


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
