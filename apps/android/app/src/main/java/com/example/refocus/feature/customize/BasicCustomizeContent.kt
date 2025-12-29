package com.example.refocus.feature.customize

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun BasicCustomizeContent(
    uiState: CustomizeViewModel.UiState,
    viewModel: CustomizeViewModel,
    onOpenTimerTimeModeDialog: () -> Unit,
    onOpenTimerVisualTimeBasisDialog: () -> Unit,
    onOpenPresetManager: () -> Unit,
) {
    val settings = uiState.customize

    // --- タイマー（プリセット） ---
    SectionCard(title = "タイマー") {
        SettingRow(
            title = "タイマーに表示する時間",
            subtitle =
                when (settings.timerTimeMode) {
                    TimerTimeMode.SessionElapsed -> "現在: セッションの経過時間を表示します。"
                    TimerTimeMode.TodayThisTarget -> "現在: このアプリの今日の累計使用時間を表示します。"
                    TimerTimeMode.TodayAllTargets -> "現在: 全対象アプリの今日の累計使用時間を表示します。"
                },
            onClick = onOpenTimerTimeModeDialog,
        )

        SettingRow(
            title = "色とサイズの変化の基準",
            subtitle =
                when (settings.timerVisualTimeBasis) {
                    TimerVisualTimeBasis.SessionElapsed ->
                        "現在: セッションの経過時間を基準に、色とサイズが変化します。"

                    TimerVisualTimeBasis.FollowDisplayTime ->
                        "現在: タイマーに表示している時間を基準に、色とサイズが変化します。"
                },
            onClick = onOpenTimerVisualTimeBasisDialog,
        )

        val dragEnabled = settings.touchMode == TimerTouchMode.Drag
        SettingRow(
            title = "タイマーのタッチ操作",
            subtitle =
                if (dragEnabled) {
                    "ドラッグで移動：タイマーをドラッグして位置を変えられます。"
                } else {
                    "タップを透過：タイマーは固定され、タップは背面アプリに届きます。"
                },
            trailing = {
                Switch(
                    checked = dragEnabled,
                    onCheckedChange = { isOn ->
                        val mode = if (isOn) TimerTouchMode.Drag else TimerTouchMode.PassThrough
                        viewModel.updateOverlayTouchMode(mode)
                    },
                )
            },
            onClick = {
                val newMode =
                    if (dragEnabled) TimerTouchMode.PassThrough else TimerTouchMode.Drag
                viewModel.updateOverlayTouchMode(newMode)
            },
        )
    }

    // --- 提案（簡易） ---
    SectionCard(title = "提案") {
        val suggestionEnabled = settings.suggestionEnabled
        val restSuggestionEnabled = settings.restSuggestionEnabled

        SettingRow(
            title = "やりたいことの提案",
            subtitle =
                if (suggestionEnabled) {
                    "オン：一定時間経過すると、やりたいことを提案します。"
                } else {
                    "オフ：提案カードを表示しません。"
                },
            trailing = {
                Switch(
                    checked = suggestionEnabled,
                    onCheckedChange = { viewModel.updateSuggestionEnabled(it) },
                )
            },
            onClick = { viewModel.updateSuggestionEnabled(!suggestionEnabled) },
        )

        SettingRow(
            title = "休憩の提案",
            subtitle =
                when {
                    !suggestionEnabled ->
                        "提案が無効になっています。"

                    restSuggestionEnabled ->
                        "オン：やりたいことが未登録のとき、休憩を提案します。"

                    else ->
                        "オフ：休憩を提案しません。"
                },
            trailing = {
                Switch(
                    checked = restSuggestionEnabled,
                    enabled = suggestionEnabled,
                    onCheckedChange = { isOn ->
                        if (!suggestionEnabled) return@Switch
                        viewModel.updateRestSuggestionEnabled(isOn)
                    },
                )
            },
            onClick = {
                if (!suggestionEnabled) return@SettingRow
                viewModel.updateRestSuggestionEnabled(!restSuggestionEnabled)
            },
        )
    }

    // --- プリセット（入口のみ） ---
//    SectionCard(title = "プリセット") {
//        val presetName = when (uiState.preset) {
//            CustomizePreset.Default -> "Default"
//            CustomizePreset.Debug -> "Debug"
//            CustomizePreset.Custom -> "Custom"
//        }
//        val presetDescription = when (uiState.preset) {
//            CustomizePreset.Default -> "標準的なバランスの設定です。"
//            CustomizePreset.Debug -> "動作確認やデバッグに便利な設定です。"
//            CustomizePreset.Custom -> "一部の値がプリセットから変更されています。"
//        }
//        SettingRow(
//            title = "プリセットを管理",
//            subtitle = "現在: $presetName。$presetDescription",
//            onClick = onOpenPresetManager,
//        )
//    }
}
