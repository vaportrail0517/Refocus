package com.example.refocus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingPage(
    title: String,
    description: String? = null,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Box(
        modifier =
            Modifier.Companion
                .fillMaxSize()
                .systemBarsPadding(),
    ) {
        Column(
            modifier =
                Modifier.Companion
                    .align(Alignment.Companion.Center)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // 任意の説明コンテンツ（箇条書きとかイラスト置き場）
            content()

            Spacer(modifier = Modifier.Companion.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrimaryClick,
                    modifier =
                        Modifier.Companion
                            .fillMaxWidth()
                            .systemBarsPadding(),
                ) {
                    Text(primaryButtonText)
                }

                if (secondaryButtonText != null && onSecondaryClick != null) {
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        modifier =
                            Modifier.Companion
                                .fillMaxWidth()
                                .systemBarsPadding(),
                    ) {
                        Text(secondaryButtonText)
                    }
                }
            }
        }
    }
}
