package com.example.refocus.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.util.formatDurationForTimerBubble
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TimerOverlay(
    modifier: Modifier = Modifier,
    customize: Customize,
    elapsedMillis: Long,
) {
    val elapsedMinutes = elapsedMillis / 1000f / 60f

    // 0〜1 の進行度（timeToMaxMinutes に対する割合）
    val p = if (customize.timeToMaxMinutes > 0) {
        (elapsedMinutes / customize.timeToMaxMinutes).coerceIn(0f, 1f)
    } else {
        1f
    }

    // 見た目用に滑らかに補間された進行度
    val animatedProgress by animateFloatAsState(
        targetValue = p,
        animationSpec = tween(durationMillis = 200),
        label = "timerProgress"
    )

    val growth = growthProgress(animatedProgress, customize.growthMode)

    val backgroundColor = computeTimerBackgroundColor(
        growthProgress = growth,
        customize = customize
    ).copy(alpha = 0.7f)

    val textColor = chooseOnColorForBackground(backgroundColor)
    val fontSizeSp = computeTimerFontSizeSp(
        growthProgress = growth,
        customize = customize
    )

    // アニメーション用ステート
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val animRotation = remember { Animatable(0f) }
    val animAlpha = remember { Animatable(1f) }

    // トリガー: デバッグ用に20秒経過で発火 (本来は animatedProgress >= 0.99f)
    val timeUp = elapsedMillis >= 20_000L

    LaunchedEffect(timeUp) {
        if (timeUp) {
            // 1. 移動 (画面中央へ移動のシミュレーション: とりあえず右下に大きく動かす)
            // ※ 画面中央の座標を知るにはBoxWithConstraintsやConfigurationが必要ですが、
            //    まずは簡易実装として固定オフセットで移動させます。
            val moveDuration = 500
            launch { animOffsetX.animateTo(100f, tween(moveDuration)) }
            launch { animOffsetY.animateTo(300f, tween(moveDuration)) }
            delay(moveDuration.toLong())

            // 2. 高速回転 (10回転 = 3600度)
            animRotation.animateTo(
                targetValue = 3600f,
                animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
            )

            // 3. 点滅 (3回)
            repeat(3) {
                animAlpha.animateTo(0.2f, tween(150))
                animAlpha.animateTo(1f, tween(150))
            }

            // 4. 元の位置に戻る
            launch { animOffsetX.animateTo(0f, tween(moveDuration)) }
            launch { animOffsetY.animateTo(0f, tween(moveDuration)) }
            
            // 回転のリセット
            animRotation.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(animOffsetX.value.roundToInt(), animOffsetY.value.roundToInt()) }
            .rotate(animRotation.value)
            .alpha(animAlpha.value)
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
    mode: TimerGrowthMode,
): Float = when (mode) {
    TimerGrowthMode.Linear -> p
    TimerGrowthMode.FastToSlow -> 1f - (1f - p) * (1f - p)     // 早く→遅く
    TimerGrowthMode.SlowToFast -> p * p                         // 遅く→早く
    TimerGrowthMode.SlowFastSlow -> 3f * p * p - 2f * p * p * p // スムーズステップ
}

/**
 * タイマー表示用のフォントサイズを計算する。
 *
 * growthProgress: 0〜1（growthProgress() の結果）
 */
private fun computeTimerFontSizeSp(
    growthProgress: Float,
    customize: Customize,
): Float {
    val g = growthProgress.coerceIn(0f, 1f)
    return customize.minFontSizeSp +
            (customize.maxFontSizeSp - customize.minFontSizeSp) * g
}

private fun computeTimerBackgroundColor(
    growthProgress: Float,
    customize: Customize,
): Color {
    val t = growthProgress.coerceIn(0f, 1f)

    fun Int.toColor(): Color = Color(this)

    return when (customize.colorMode) {
        TimerColorMode.Fixed -> {
            customize.fixedColorArgb.toColor()
        }

        TimerColorMode.GradientTwo -> {
            val start = customize.gradientStartColorArgb.toColor()
            val end = customize.gradientEndColorArgb.toColor()
            lerpColor(start, end, t)
        }

        TimerColorMode.GradientThree -> {
            val start = customize.gradientStartColorArgb.toColor()
            val middle = customize.gradientMiddleColorArgb.toColor()
            val end = customize.gradientEndColorArgb.toColor()
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