package com.example.refocus.feature.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.refocus.core.model.OverlaySettings
import kotlinx.coroutines.delay
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource

enum class OverlayColorMode {
    SingleColor,
    Threshold,
    Gradient
}

@Composable
fun OverlayTimerBubble(
    modifier: Modifier = Modifier,
    initialElapsedMillis: Long = 0L,
    settings: OverlaySettings,
) {
    val timeSource: TimeSource = remember { SystemTimeSource() }
    var elapsedMillis by remember { mutableLongStateOf(initialElapsedMillis) }
    var lastTickElapsedRealtime by remember {
        mutableLongStateOf(timeSource.elapsedRealtime())
    }
    val elapsedMinutes = elapsedMillis / 1000f / 60f
    val progress = if (settings.timeToMaxMinutes > 0) {
        (elapsedMinutes / settings.timeToMaxMinutes).coerceIn(0f, 1f)
    } else {
        1f
    }
    val fontSizeSp = settings.minFontSizeSp +
            (settings.maxFontSizeSp - settings.minFontSizeSp) * progress
    // タイマー本体：常に 1 秒ごとに state を更新する
    LaunchedEffect(initialElapsedMillis) {
        // 初期値をリセット
        elapsedMillis = initialElapsedMillis
        lastTickElapsedRealtime = timeSource.elapsedRealtime()
        while (true) {
            // 1 秒待つ
            delay(1000L)
            val now = timeSource.elapsedRealtime()
            val delta = now - lastTickElapsedRealtime
            if (delta > 0L) {
                elapsedMillis += delta
                lastTickElapsedRealtime = now
            }
        }
    }
    Box(
        modifier = modifier
            .alpha(0.9f)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = formatDuration(elapsedMillis),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSizeSp.sp
        )
    }
}

// 00:00 / 12:34 / 1:23:45 みたいな表記
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
