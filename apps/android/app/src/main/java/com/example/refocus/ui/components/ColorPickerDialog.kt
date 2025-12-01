package com.example.refocus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * HSV ベースのリッチなカラーピッカー。
 *
 * - 上: 選択済み色のプレビュー
 * - 中央: Hue に応じた SV 正方形（X: Saturation, Y: Value）
 * - 下: Hue スライダー
 *
 * 返り値は 0xFFRRGGBB の ARGB Int。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    description: String,
    initialColorArgb: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // ★ MaterialTheme は remember の外で読む（@Composable プロパティ）
    val themePrimary = MaterialTheme.colorScheme.primary

    // 初期色
    val initialColor = remember(initialColorArgb) {
        if (initialColorArgb == null || initialColorArgb == 0) {
            themePrimary
        } else {
            Color(initialColorArgb)
        }
    }

    var hue by rememberSaveable { mutableFloatStateOf(0f) }         // 0..360
    var saturation by rememberSaveable { mutableFloatStateOf(1f) }  // 0..1
    var value by rememberSaveable { mutableFloatStateOf(1f) }       // 0..1

    // 初期 HSV への変換（remember で1回だけ）
    remember(initialColorArgb) {
        val r = initialColor.red
        val g = initialColor.green
        val b = initialColor.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val v = max
        val s = if (max == 0f) 0f else delta / max

        hue = h
        saturation = s
        value = v
        true
    }

    val currentColor = Color.hsv(
        hue = hue,
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // プレビュー
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(currentColor)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.medium
                        ),
                )

                // SV 正方形
                SVColorSquare(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                    },
                )

                // Hue スライダー
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                )

                Text(
                    text = "現在の色: #${argbStringFromColor(currentColor)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val argb = argbIntFromColor(currentColor)
                    onConfirm(argb)
                }
            ) {
                Text("決定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * Hue と SV から色を選ぶための正方形。
 *
 * - X: Saturation (0..1)
 * - Y: Value      (1..0)  上が明るく、下が暗い
 */
@Composable
private fun SVColorSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        var boxWidth by remember { mutableFloatStateOf(0f) }
        var boxHeight by remember { mutableFloatStateOf(0f) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hue) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onChangeFromOffset(offset, boxWidth, boxHeight, onChange)
                        },
                        onDrag = { change, _ ->
                            onChangeFromOffset(change.position, boxWidth, boxHeight, onChange)
                        }
                    )
                }
        ) {
            // Canvas のサイズを保存
            boxWidth = size.width
            boxHeight = size.height

            val hueColor = Color.hsv(hue, 1f, 1f)

            // 横方向: 白→Hue カラー
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to hueColor,
                )
            )

            // 縦方向: 透明→黒 を Multiply で重ねて Value を表現
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                ),
                blendMode = BlendMode.Multiply
            )

            // 選択位置のハンドル
            val s = saturation.coerceIn(0f, 1f)
            val v = value.coerceIn(0f, 1f)
            val handleX = size.width * s
            val handleY = size.height * (1f - v)

            // 外側黒
            drawCircle(
                color = Color.Black,
                radius = 9.dp.toPx(),
                center = Offset(handleX, handleY),
            )
            // 内側白
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(handleX, handleY),
            )
        }
    }
}

private fun onChangeFromOffset(
    offset: Offset,
    width: Float,
    height: Float,
    onChange: (Float, Float) -> Unit,
) {
    if (width <= 0f || height <= 0f) return

    val x = offset.x.coerceIn(0f, width)
    val y = offset.y.coerceIn(0f, height)

    val s = (x / width).coerceIn(0f, 1f)
    val v = (1f - y / height).coerceIn(0f, 1f)
    onChange(s, v)
}

/**
 * Hue スライダー（0..360）。
 */
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradientColors = remember {
        val stops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
        stops.map { h -> Color.hsv(h, 1f, 1f) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "色相 (Hue): ${hue.roundToInt()}°")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    Brush.horizontalGradient(colors = gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = hue,
                onValueChange = { onHueChange(it.coerceIn(0f, 360f)) },
                valueRange = 0f..360f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
            )
        }
    }
}

/**
 * Color から #RRGGBB 文字列を生成。
 */
private fun argbStringFromColor(color: Color): String {
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return "%02X%02X%02X".format(r, g, b)
}

/**
 * Color から 0xFFRRGGBB の Int に変換。
 */
private fun argbIntFromColor(color: Color): Int {
    val a = 0xFF
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
