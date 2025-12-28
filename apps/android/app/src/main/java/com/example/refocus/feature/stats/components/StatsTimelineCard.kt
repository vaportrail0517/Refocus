package com.example.refocus.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.TimeBucketStats
import com.example.refocus.ui.components.SectionCard

@Composable
internal fun TimelineCard(
    timeBuckets: List<TimeBucketStats>,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "タイムライン",
        description = "どの時間帯に対象アプリを使っていたかの目安です．",
        onClick = onClick,
    ) {
        TimelineChart(timeBuckets = timeBuckets)
    }
}

@Composable
internal fun TimelineChart(timeBuckets: List<TimeBucketStats>) {
    // 縦軸のベースは「対象アプリの利用時間（分）」で見る
    val maxTargetMinutes =
        (timeBuckets.maxOfOrNull { it.targetUsageMinutes } ?: 0)
            .coerceAtLeast(1)
    // 縦軸の最大値を「10分刻み」で丸める（例: 7分 -> 10, 18分 -> 20, 35分 -> 40）
    val axisMaxMinutes = ((maxTargetMinutes + 9) / 10) * 10
    val yLabels =
        listOf(
            axisMaxMinutes,
            (axisMaxMinutes * 2) / 3,
            axisMaxMinutes / 3,
            0,
        )
    val yAxisWidth = 40.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 上段: Y 軸 + バー
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
        ) {
            // Y 軸ラベル
            Column(
                modifier =
                    Modifier
                        .width(yAxisWidth)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yLabels.forEach { label ->
                    Text(
                        text = "${label}分",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // バー本体
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                timeBuckets.forEach { bucket ->
                    val usageMinutes = bucket.targetUsageMinutes
                    val usageHeightFraction =
                        (usageMinutes.toFloat() / axisMaxMinutes.toFloat()).coerceIn(0f, 1f)

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // 背景: Refocus 監視状態
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                                    .background(
                                        color =
                                            if (bucket.monitoringMinutes > 0) {
                                                // 監視していた時間帯
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            } else {
                                                // Refocus が動いていなかった時間帯（未知）
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        shape = RoundedCornerShape(2.dp),
                                    ),
                        )

                        // 前景: 対象アプリの利用時間バー
                        if (usageMinutes > 0) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(usageHeightFraction)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            shape =
                                                RoundedCornerShape(
                                                    topStart = 2.dp,
                                                    topEnd = 2.dp,
                                                ),
                                        ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 下段: 横軸ラベル（0, 6, 12, 18, 24 時）と縦線
        TimelineXAxis(yAxisWidth = yAxisWidth)
    }
}

@Composable
internal fun TimelineXAxis(yAxisWidth: Dp) {
    val labels = listOf("0時", "6時", "12時", "18時", "24時")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(modifier = Modifier.width(yAxisWidth))
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .height(6.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
