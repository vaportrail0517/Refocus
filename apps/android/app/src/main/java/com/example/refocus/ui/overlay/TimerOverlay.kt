package com.example.refocus.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.core.model.OverlayColorMode
import com.example.refocus.core.model.OverlayGrowthMode
import com.example.refocus.core.model.Settings
import com.example.refocus.core.util.formatDurationForTimerBubble

@Composable
fun TimerOverlay(
    modifier: Modifier = Modifier,
    settings: Settings,
    elapsedMillis: Long,
) {
    val elapsedMinutes = elapsedMillis / 1000f / 60f

    // 0〜1 の進行度（timeToMaxMinutes に対する割合）
    val p = if (settings.timeToMaxMinutes > 0) {
        (elapsedMinutes / settings.timeToMaxMinutes).coerceIn(0f, 1f)
    } else {
        1f
    }

    // 見た目用に滑らかに補間された進行度
    val animatedProgress by animateFloatAsState(
        targetValue = p,
        animationSpec = tween(durationMillis = 200),
        label = "timerProgress"
    )

    val growth = growthProgress(animatedProgress, settings.growthMode)

    val backgroundColor = computeTimerBackgroundColor(
        growthProgress = growth,
        settings = settings
    ).copy(alpha = 0.7f)

    val textColor = chooseOnColorForBackground(backgroundColor)
    val fontSizeSp = computeTimerFontSizeSp(
        growthProgress = growth,
        settings = settings
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = MaterialTheme.shapes.medium,
                clip = false
            )
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = formatDurationForTimerBubble(elapsedMillis),
            color = textColor,
            fontSize = fontSizeSp.sp
        )
    }
}


/**
 * 成長モードに応じて進行度 p (0..1) を変形した g (0..1) を返す。
 */
private fun growthProgress(
    p: Float,
    mode: OverlayGrowthMode,
): Float = when (mode) {
    OverlayGrowthMode.Linear -> p
    OverlayGrowthMode.FastToSlow -> 1f - (1f - p) * (1f - p)     // 早く→遅く
    OverlayGrowthMode.SlowToFast -> p * p                         // 遅く→早く
    OverlayGrowthMode.SlowFastSlow -> 3f * p * p - 2f * p * p * p // スムーズステップ
}

/**
 * タイマー表示用のフォントサイズを計算する。
 *
 * growthProgress: 0〜1（growthProgress() の結果）
 */
private fun computeTimerFontSizeSp(
    growthProgress: Float,
    settings: Settings,
): Float {
    val g = growthProgress.coerceIn(0f, 1f)
    return settings.minFontSizeSp +
            (settings.maxFontSizeSp - settings.minFontSizeSp) * g
}

private fun computeTimerBackgroundColor(
    growthProgress: Float,
    settings: Settings,
): Color {
    val t = growthProgress.coerceIn(0f, 1f)

    fun Int.toColor(): Color = Color(this)

    return when (settings.colorMode) {
        OverlayColorMode.Fixed -> {
            settings.fixedColorArgb.toColor()
        }

        OverlayColorMode.GradientTwo -> {
            val start = settings.gradientStartColorArgb.toColor()
            val end = settings.gradientEndColorArgb.toColor()
            lerpColor(start, end, t)
        }

        OverlayColorMode.GradientThree -> {
            val start = settings.gradientStartColorArgb.toColor()
            val middle = settings.gradientMiddleColorArgb.toColor()
            val end = settings.gradientEndColorArgb.toColor()
            if (t <= 0.5f) {
                val localT = t * 2f
                lerpColor(start, middle, localT)
            } else {
                val localT = (t - 0.5f) * 2f
                lerpColor(middle, end, localT)
            }
        }
    }
}

/**
 * 2 色の線形補間。
 */
private fun lerpColor(start: Color, end: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    val r = start.red + (end.red - start.red) * clamped
    val g = start.green + (end.green - start.green) * clamped
    val b = start.blue + (end.blue - start.blue) * clamped
    val a = start.alpha + (end.alpha - start.alpha) * clamped
    return Color(r, g, b, a)
}

/**
 * 背景色に対して、白/黒どちらのテキスト色が見やすいかを選ぶ。
 */
private fun chooseOnColorForBackground(bg: Color): Color {
    val r = (bg.red * 255).toInt()
    val g = (bg.green * 255).toInt()
    val b = (bg.blue * 255).toInt()

    val brightness = (r * 299 + g * 587 + b * 114) / 1000
    return if (brightness >= 128) Color.Companion.Black else Color.Companion.White
}