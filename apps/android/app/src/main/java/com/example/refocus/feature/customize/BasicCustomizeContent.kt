package com.example.refocus.feature.customize

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import com.example.refocus.config.SettingsBasicPresets
import com.example.refocus.core.model.FontPreset
import com.example.refocus.core.model.GracePreset
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.SettingsPreset
import com.example.refocus.core.model.SuggestionTriggerPreset
import com.example.refocus.core.model.TimeToMaxPreset
import com.example.refocus.core.util.formatDurationMilliSecondsOrNull
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.ui.components.PresetOption
import com.example.refocus.ui.components.PresetOptionRow
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun BasicCustomizeContent(
    uiState: CustomizeViewModel.UiState,
    viewModel: CustomizeViewModel,
    onOpenAdvanced: () -> Unit,
) {
    val settings = uiState.settings

    // --- アプリ ---
    SectionCard(title = "アプリ") {
        val gracePreset = SettingsBasicPresets.gracePresetOrNull(settings)
        val formattedGraceTime = formatDurationMilliSecondsOrNull(settings.gracePeriodMillis)
        PresetOptionRow(
            title = "一時的なアプリ切り替え",
            currentPreset = gracePreset,
            options = listOf(
                PresetOption(GracePreset.Short, "短い"),
                PresetOption(GracePreset.Normal, "普通"),
                PresetOption(GracePreset.Long, "長い"),
            ),
            currentValueDescription = buildString {
                if (formattedGraceTime.isNullOrEmpty()) {
                    append("アプリの画面を閉じると猶予なしでセッションを終了します。")
                } else {
                    append(formattedGraceTime)
                    append("以内に対象アプリを再開すると同じセッションとして扱います。")
                }
            },
            onPresetSelected = { preset ->
                viewModel.applyGracePreset(preset)
            },
        )
    }

    // --- タイマー（プリセット） ---
    SectionCard(title = "タイマー") {
        val dragEnabled = settings.touchMode == OverlayTouchMode.Drag
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
                        val mode = if (isOn) OverlayTouchMode.Drag else OverlayTouchMode.PassThrough
                        viewModel.updateOverlayTouchMode(mode)
                    }
                )
            },
            onClick = {
                val newMode =
                    if (dragEnabled) OverlayTouchMode.PassThrough else OverlayTouchMode.Drag
                viewModel.updateOverlayTouchMode(newMode)
            },
        )

        val fontPreset = SettingsBasicPresets.fontPresetOrNull(settings)
        PresetOptionRow(
            title = "文字サイズ",
            currentPreset = fontPreset,
            options = listOf(
                PresetOption(FontPreset.Small, "小さい"),
                PresetOption(FontPreset.Medium, "普通"),
                PresetOption(FontPreset.Large, "大きい"),
            ),
            currentValueDescription = "最小 ${settings.minFontSizeSp.toInt()}sp / 最大 ${settings.maxFontSizeSp.toInt()}sp",
            onPresetSelected = { preset ->
                viewModel.applyFontPreset(preset)
            },
        )

        val timePreset = SettingsBasicPresets.timeToMaxPresetOrNull(settings)
        PresetOptionRow(
            title = "最大サイズになるまでの時間",
            currentPreset = timePreset,
            options = listOf(
                PresetOption(TimeToMaxPreset.Fast, "早い"),
                PresetOption(TimeToMaxPreset.Normal, "普通"),
                PresetOption(TimeToMaxPreset.Slow, "遅い"),
            ),
            currentValueDescription = "${settings.timeToMaxMinutes}分",
            onPresetSelected = { preset ->
                viewModel.applyTimeToMaxPreset(preset)
            },
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

        val trigPreset = SettingsBasicPresets.suggestionTriggerPresetOrNull(settings)
        PresetOptionRow(
            title = "提案するまでの時間",
            currentPreset = trigPreset,
            options = listOf(
                PresetOption(SuggestionTriggerPreset.Short, "短い"),
                PresetOption(SuggestionTriggerPreset.Normal, "普通"),
                PresetOption(SuggestionTriggerPreset.Long, "長い"),
            ),
            currentValueDescription = buildString {
                append("対象アプリの利用を開始してから")
                append(formatDurationSeconds(settings.suggestionTriggerSeconds.toLong()))
                append("以上経過したら提案を行います。")
            },
            onPresetSelected = { preset ->
                viewModel.applySuggestionTriggerPreset(preset)
            },
        )
    }

    // --- 設定プリセット（全体） ---
    val presetOptions = listOf(
        PresetOption(SettingsPreset.Default, "Default"),
        PresetOption(SettingsPreset.Custom, "Custom"),
        PresetOption(SettingsPreset.Debug, "Debug"),
    )
    val subtitleDescription = when (uiState.preset) {
        SettingsPreset.Default -> "標準的なバランスの設定です。"
        SettingsPreset.Debug -> "動作確認やデバッグに便利な設定です。"
        SettingsPreset.Custom -> "一部の値がプリセットから変更されています。"
    }
    PresetOptionRow(
        title = "現在のプリセット",
        currentPreset = uiState.preset,
        options = presetOptions,
        currentValueDescription = subtitleDescription,
        onPresetSelected = { preset ->
            when (preset) {
                SettingsPreset.Default -> viewModel.applyPreset(SettingsPreset.Default)
                SettingsPreset.Debug -> viewModel.applyPreset(SettingsPreset.Debug)
                SettingsPreset.Custom -> viewModel.setPresetCustom()
            }
        },
    )

    // --- 詳細設定への入口 ---
    SectionCard(title = "詳細カスタマイズ") {
        SettingRow(
            title = "詳細カスタマイズを開く",
            subtitle = "タイマー表示や提案などの詳細な設定が行えます。",
            onClick = onOpenAdvanced,
        )
    }
}