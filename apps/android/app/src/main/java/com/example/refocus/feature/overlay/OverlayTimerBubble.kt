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
import com.example.refocus.core.model.Settings
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource
import com.example.refocus.core.util.formatDurationForTimerBubble
import kotlinx.coroutines.delay


enum class OverlayColorMode {
    SingleColor,
    Threshold,
    Gradient
}

@Composable
fun OverlayTimerBubble(
    modifier: Modifier = Modifier,
    settings: Settings,
    // SessionManager から経過時間をもらうための provider
    elapsedMillisProvider: (Long) -> Long
) {
    val timeSource: TimeSource = remember { SystemTimeSource() }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    // 1 秒ごとに SessionManager に「今の経過時間」を問い合わせる
    LaunchedEffect(Unit) {
        while (true) {
            val nowElapsed = timeSource.elapsedRealtime()
            elapsedMillis = elapsedMillisProvider(nowElapsed)
            delay(1000L)
        }
    }
    val elapsedMinutes = elapsedMillis / 1000f / 60f
    val progress = if (settings.timeToMaxMinutes > 0) {
        (elapsedMinutes / settings.timeToMaxMinutes).coerceIn(0f, 1f)
    } else {
        1f
    }
    val fontSizeSp = settings.minFontSizeSp +
            (settings.maxFontSizeSp - settings.minFontSizeSp) * progress
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
            text = formatDurationForTimerBubble(elapsedMillis),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSizeSp.sp
        )
    }
}
