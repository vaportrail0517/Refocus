package com.example.refocus.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun OnboardingIntroScreen(
    onStartSetup: () -> Unit
) {
    OnboardingPage(
        title = "Refocus へようこそ",
        description = "アプリの連続使用時間をリアルタイムに可視化するために、最初にいくつか設定を行います。",
        primaryButtonText = "権限を設定する",
        onPrimaryClick = onStartSetup
    )
}

@Composable
fun OnboardingReadyScreen(
    onSelectApps: () -> Unit
) {
    OnboardingPage(
        title = "準備ができました",
        description = "次に、Refocusで可視化するアプリを選びましょう。",
        primaryButtonText = "対象アプリを選択",
        onPrimaryClick = onSelectApps
    )
}

@Composable
fun OnboardingStartModeScreen(
    onDecide: () -> Unit
) {
    val viewModel: OnboardingStartModeViewModel = hiltViewModel()
    var selected by remember { mutableStateOf<StartMode?>(StartMode.AutoAndNow) }
    OnboardingPage(
        title = "Refocus の起動方法を選ぶ",
        description = "Refocus はバックグラウンドで起動している間だけ、対象アプリの連続使用時間を記録します。\nあとから設定画面でいつでも変更できます。",
        primaryButtonText = "この設定で始める",
        onPrimaryClick = {
            val mode = selected ?: return@OnboardingPage
            viewModel.applyStartMode(mode)
            onDecide()
        },
        secondaryButtonText = null,
        onSecondaryClick = null,
    ) {
        Spacer(Modifier.height(128.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StartModeOptionCard(
                title = "自動で起動 + 今すぐ始める",
                badge = "デフォルト",
                description = "今すぐ計測を開始し、端末を再起動しても自動で Refocus が立ち上がるようになります。",
                selected = selected == StartMode.AutoAndNow,
                onClick = { selected = StartMode.AutoAndNow }
            )
            StartModeOptionCard(
                title = "今だけ起動する",
                badge = null,
                description = "今すぐ計測を始めますが、端末を再起動しても自動では起動しません。",
                selected = selected == StartMode.NowOnly,
                onClick = { selected = StartMode.NowOnly }
            )
            StartModeOptionCard(
                title = "今は起動しない",
                badge = null,
                description = "今は何もしません。あとから設定画面の「起動」セクションからいつでも起動できます。",
                selected = selected == StartMode.Off,
                onClick = { selected = StartMode.Off }
            )
        }
    }
}

@Composable
private fun StartModeOptionCard(
    title: String,
    badge: String?,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = if (selected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        colors = colors
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun OnboardingFinishScreen(
    onCloseApp: () -> Unit,
    onOpenApp: () -> Unit
) {
    val viewModel: OnboardingFinishViewModel = hiltViewModel()
    OnboardingPage(
        title = "設定が完了しました",
        description = "このままアプリを閉じるか、Refocus のホーム画面を開いて設定を確認できます。",
        primaryButtonText = "Refocus を開く",
        onPrimaryClick = onOpenApp,
        secondaryButtonText = "アプリを閉じる",
        onSecondaryClick = onCloseApp
    )
}