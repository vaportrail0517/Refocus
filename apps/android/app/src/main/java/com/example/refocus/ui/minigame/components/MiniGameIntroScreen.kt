package com.example.refocus.ui.minigame.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.refocus.ui.minigame.MiniGameTestTags
import com.example.refocus.ui.minigame.catalog.MiniGameDescriptor

/**
 * ミニゲーム開始前に表示する共通の開始画面．
 *
 * - ゲーム本体を Compose する前に，タイトル・解説・ルールを提示する
 * - 「開始」押下でプレイへ進む
 * - 「スキップ」はホスト側で終了扱いにする想定
 */
@Composable
fun MiniGameIntroScreen(
    descriptor: MiniGameDescriptor,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    skipLabel: String = "今回はスキップ",
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(MiniGameTestTags.INTRO_ROOT),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = descriptor.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (descriptor.description.isNotBlank()) {
                Text(
                    text = descriptor.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val metaLines =
                buildList {
                    descriptor.timeLimitSeconds?.let { add("制限時間: ${it}秒") }
                    descriptor.estimatedSeconds?.let { add("目安: ${it}秒") }
                }
            if (metaLines.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    metaLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (descriptor.rules.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "ルール",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    descriptor.rules.forEach { rule ->
                        Text(
                            text = "・$rule",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            extraContent?.let {
                Spacer(modifier = Modifier.height(4.dp))
                it()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onStart,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag(MiniGameTestTags.INTRO_START_BUTTON),
            ) {
                Text(descriptor.primaryActionLabel)
            }

            if (descriptor.canSkipBeforeStart) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(MiniGameTestTags.INTRO_SKIP_BUTTON),
                ) {
                    Text(skipLabel)
                }
            }
        }
    }
}
