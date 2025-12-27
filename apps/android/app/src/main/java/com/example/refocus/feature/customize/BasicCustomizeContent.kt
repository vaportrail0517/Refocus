package com.example.refocus.feature.customize

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.util.formatDurationMilliSecondsOrNull
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.feature.customize.components.PresetOption
import com.example.refocus.feature.customize.components.PresetOptionRow
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun BasicCustomizeContent(
    uiState: CustomizeViewModel.UiState,
    viewModel: CustomizeViewModel,
    onOpenGraceDialog: () -> Unit,
    onOpenFontDialog: () -> Unit,
    onOpenTimeToMaxDialog: () -> Unit,
    onOpenTimerTimeModeDialog: () -> Unit,
    onOpenSuggestionTriggerDialog: () -> Unit,
) {
    val settings = uiState.customize

    // --- アプリ ---
    SectionCard(title = "アプリ") {
        val formattedGraceTime = formatDurationMilliSecondsOrNull(settings.gracePeriodMillis)
        SettingRow(
            title = "一時的なアプリ切り替えの猶予時間",
            subtitle = if (formattedGraceTime.isNullOrEmpty()) {
                "現在: 猶予なし（対象アプリを離れたらセッションを終了します）。"
            } else {
                "現在: ${formattedGraceTime}以内に対象アプリへ戻れば同じセッションとして扱います。"
            },
            onClick = onOpenGraceDialog,
        )
    }

    // --- タイマー（プリセット） ---
    SectionCard(title = "タイマー") {
        SettingRow(
            title = "タイマーに表示する時間",
            subtitle = when (settings.timerTimeMode) {
                TimerTimeMode.SessionElapsed -> "現在: セッションの経過時間を表示します。"
                TimerTimeMode.TodayThisTarget -> "現在: このアプリの今日の累計使用時間を表示します。"
                TimerTimeMode.TodayAllTargets -> "現在: 全対象アプリの今日の累計使用時間を表示します。"
            },
            onClick = onOpenTimerTimeModeDialog,
        )


        val dragEnabled = settings.touchMode == TimerTouchMode.Drag
        SettingRow(
            title = "タイマーのタッチ操作",
            subtitle = if (dragEnabled) {
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
                    }
                )
            },
            onClick = {
                val newMode =
                    if (dragEnabled) TimerTouchMode.PassThrough else TimerTouchMode.Drag
                viewModel.updateOverlayTouchMode(newMode)
            },
        )

        SettingRow(
            title = "文字サイズ",
            subtitle = "現在: 最小 ${settings.minFontSizeSp.toInt()}sp / 最大 ${settings.maxFontSizeSp.toInt()}sp",
            onClick = onOpenFontDialog,
        )

        SettingRow(
            title = "最大サイズになるまでの時間",
            subtitle = "現在: ${settings.timeToMaxMinutes}分",
            onClick = onOpenTimeToMaxDialog,
        )
    }

    // --- 提案（簡易） ---
    SectionCard(title = "提案") {
        val suggestionEnabled = settings.suggestionEnabled
        val restSuggestionEnabled = settings.restSuggestionEnabled

        SettingRow(
            title = "やりたいことの提案",
            subtitle = if (suggestionEnabled) {
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
            subtitle = when {
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
                    }
                )
            },
            onClick = {
                if (!suggestionEnabled) return@SettingRow
                viewModel.updateRestSuggestionEnabled(!restSuggestionEnabled)
            },
        )

        SettingRow(
            title = "提案するまでの時間",
            subtitle = buildString {
                append("現在: 対象アプリの利用を開始してから")
                append(formatDurationSeconds(settings.suggestionTriggerSeconds.toLong()))
                append("以上で提案します。")
            },
            onClick = onOpenSuggestionTriggerDialog,
        )
    }

    // --- 設定プリセット（全体） ---
    val presetOptions = listOf(
        PresetOption(CustomizePreset.Default, "Default"),
        PresetOption(CustomizePreset.Custom, "Custom"),
        PresetOption(CustomizePreset.Debug, "Debug"),
    )
    val subtitleDescription = when (uiState.preset) {
        CustomizePreset.Default -> "標準的なバランスの設定です。"
        CustomizePreset.Debug -> "動作確認やデバッグに便利な設定です。"
        CustomizePreset.Custom -> "一部の値がプリセットから変更されています。"
    }
    PresetOptionRow(
        title = "現在のプリセット",
        currentPreset = uiState.preset,
        options = presetOptions,
        currentValueDescription = subtitleDescription,
        onPresetSelected = { preset ->
            when (preset) {
                CustomizePreset.Default -> viewModel.applyPreset(CustomizePreset.Default)
                CustomizePreset.Debug -> viewModel.applyPreset(CustomizePreset.Debug)
                CustomizePreset.Custom -> viewModel.setPresetCustom()
            }
        },
    )

}