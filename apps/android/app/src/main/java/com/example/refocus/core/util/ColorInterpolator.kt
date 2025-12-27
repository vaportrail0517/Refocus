package com.example.refocus.core.util

import androidx.compose.ui.graphics.Color

/**
 * 2色の線形補間．t は 0..1 を想定する．
 */
fun interpolateColor(start: Color, end: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    val r = start.red + (end.red - start.red) * clamped
    val g = start.green + (end.green - start.green) * clamped
    val b = start.blue + (end.blue - start.blue) * clamped
    val a = start.alpha + (end.alpha - start.alpha) * clamped
    return Color(r, g, b, a)
}
