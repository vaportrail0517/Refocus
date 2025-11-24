package com.example.refocus.feature.settings

import androidx.compose.runtime.Composable
import com.example.refocus.core.util.formatDurationMillis
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun AdvancedSettingsContent(
    uiState: SettingsViewModel.UiState,
    onBackToBasic: () -> Unit,
    onOpenGraceDialog: () -> Unit,
    onOpenPollingDialog: () -> Unit,
    onOpenFontDialog: () -> Unit,
    onOpenTimeToMaxDialog: () -> Unit,
    onOpenSuggestionTriggerDialog: () -> Unit,
    onOpenSuggestionForegroundStableDialog: () -> Unit,
    onOpenSuggestionCooldownDialog: () -> Unit,
    onOpenSuggestionTimeoutDialog: () -> Unit,
    onOpenSuggestionInteractionLockoutDialog: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val settings = uiState.settings

    // 一番上に「基本設定に戻る」行を置いておく（＋将来 AppBar を載せてもよい）
    SectionCard(title = "基本設定") {
        SettingRow(
            title = "基本設定に戻る",
            subtitle = "普段使い向けのシンプルな設定に戻ります。",
            onClick = onBackToBasic,
        )
    }

    SectionCard(title = "監視・セッション") {
        SettingRow(
            title = "前面アプリをチェックする間隔",
            subtitle = "現在: ${settings.pollingIntervalMillis} ms 毎に対象アプリかどうか確認します。",
            onClick = onOpenPollingDialog,
        )
        SettingRow(
            title = "セッション継続の猶予時間",
            subtitle = "現在: 対象アプリを離れてから${formatDurationMillis(settings.gracePeriodMillis)}以内に戻れば同じセッションとみなします。",
            onClick = onOpenGraceDialog,
        )
    }

    SectionCard(title = "タイマーの表示") {
        SettingRow(
            title = "フォントサイズの範囲",
            subtitle = "現在: 最小 ${settings.minFontSizeSp} sp / 最大 ${settings.maxFontSizeSp} sp",
            onClick = onOpenFontDialog,
        )
        SettingRow(
            title = "最大サイズになるまでの時間",
            subtitle = "現在: ${settings.timeToMaxMinutes}分",
            onClick = onOpenTimeToMaxDialog,
        )
    }

    SectionCard(title = "提案の詳細") {
        SettingRow(
            title = "提案を出すために必要なセッションの継続時間",
            subtitle = "現在: ${formatDurationSeconds(settings.suggestionTriggerSeconds)}以上経過してから提案します。",
            onClick = onOpenSuggestionTriggerDialog,
        )
        SettingRow(
            title = "提案を出すために必要な対象アプリが連続して前面にいる時間",
            subtitle = "現在: ${formatDurationSeconds(settings.suggestionForegroundStableSeconds)}以上経過してから提案します。",
            onClick = onOpenSuggestionForegroundStableDialog,
        )
        SettingRow(
            title = "次の提案までの間隔",
            subtitle = "現在: ${formatDurationSeconds(settings.suggestionCooldownSeconds)}待ってから再び提案をします。",
            onClick = onOpenSuggestionCooldownDialog,
        )
        SettingRow(
            title = "提案カードを自動で閉じるまでの時間",
            subtitle = "現在: ${formatDurationSeconds(settings.suggestionTimeoutSeconds)}後に自動で閉じます。",
            onClick = onOpenSuggestionTimeoutDialog,
        )
        SettingRow(
            title = "提案表示直後の誤タップ防止時間",
            subtitle = "現在: 表示してから${settings.suggestionInteractionLockoutMillis} ms の間、提案カードを消せなくします。",
            onClick = onOpenSuggestionInteractionLockoutDialog,
        )
    }
}