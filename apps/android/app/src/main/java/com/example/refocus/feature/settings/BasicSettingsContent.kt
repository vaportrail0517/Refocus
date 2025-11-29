package com.example.refocus.feature.settings

import android.app.Activity
import android.content.Context
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import com.example.refocus.config.SettingsBasicPresets
import com.example.refocus.core.model.FontPreset
import com.example.refocus.core.model.GracePreset
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.SettingsPreset
import com.example.refocus.core.model.SuggestionTriggerPreset
import com.example.refocus.core.model.TimeToMaxPreset
import com.example.refocus.core.util.formatDurationMillisOrNull
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.system.overlay.service.startOverlayService
import com.example.refocus.system.overlay.service.stopOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.ui.components.PresetOption
import com.example.refocus.ui.components.PresetOptionRow
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun BasicSettingsContent(
    uiState: SettingsViewModel.UiState,
    usageGranted: Boolean,
    overlayGranted: Boolean,
    hasCorePermissions: Boolean,
    isServiceRunning: Boolean,
    onServiceRunningChange: (Boolean) -> Unit,
    onOpenAppSelect: () -> Unit,
    onRequireCorePermission: () -> Unit,
    onOpenAdvanced: () -> Unit,
    viewModel: SettingsViewModel,
    context: Context,
    activity: Activity?,
) {
    val settings = uiState.settings

    // --- 権限 ---
    SectionCard(
        title = "権限"
    ) {
        SettingRow(
            title = "使用状況へのアクセス",
            subtitle = "（必須）連続使用時間を計測するために必要です。",
            trailing = {
                Switch(
                    checked = usageGranted,
                    onCheckedChange = null,
                    enabled = true
                )
            },
            onClick = {
                activity?.let { PermissionHelper.openUsageAccessSettings(it) }
            }
        )
        SettingRow(
            title = "他のアプリの上に表示",
            subtitle = "（必須）タイマーを他のアプリの上に表示するために必要です。",
            trailing = {
                Switch(
                    checked = overlayGranted,
                    onCheckedChange = null,
                    enabled = true
                )
            },
            onClick = {
                activity?.let { PermissionHelper.openOverlaySettings(it) }
            }
        )
    }

    // --- 起動 ---
    SectionCard(
        title = "起動"
    ) {
        SettingRow(
            title = "Refocus を動かす",
            subtitle = if (!hasCorePermissions) {
                "権限が足りないため、現在 Refocus を動かすことはできません。上の「権限」から設定を有効にしてください。"
            } else if (isServiceRunning) {
                "現在: 計測中（対象アプリ利用時に連続使用時間を記録します）"
            } else {
                "現在: 停止中（対象アプリの計測は行われていません）"
            },
            trailing = {
                val checked = uiState.settings.overlayEnabled && isServiceRunning
                Switch(
                    checked = checked,
                    enabled = hasCorePermissions,
                    onCheckedChange = { newChecked ->
                        if (!hasCorePermissions) {
                            // 権限不足 → ダイアログを出すだけ
                            onRequireCorePermission()
                            return@Switch
                        }
                        if (newChecked) {
                            viewModel.updateOverlayEnabled(true)
                            context.startOverlayService()
                            onServiceRunningChange(true)
                        } else {
                            viewModel.updateOverlayEnabled(false)
                            context.stopOverlayService()
                            onServiceRunningChange(false)
                        }
                    }
                )
            },
            onClick = {
                if (!hasCorePermissions) {
                    onRequireCorePermission()
                    return@SettingRow
                }
                val currentlyOn = uiState.settings.overlayEnabled && isServiceRunning
                val turnOn = !currentlyOn
                if (turnOn) {
                    viewModel.updateOverlayEnabled(true)
                    context.startOverlayService()
                    onServiceRunningChange(true)
                } else {
                    viewModel.updateOverlayEnabled(false)
                    context.stopOverlayService()
                    onServiceRunningChange(false)
                }
            }
        )
        SettingRow(
            title = "端末起動時に自動起動",
            subtitle = "端末を再起動したときに自動で Refocus を起動します。※起動には少し時間がかかります。",
            trailing = {
                Switch(
                    checked = uiState.settings.autoStartOnBoot,
                    enabled = hasCorePermissions, // 権限不足時はグレーアウト
                    onCheckedChange = { enabled ->
                        if (!hasCorePermissions) {
                            // 権限不足 → ダイアログだけ
                            onRequireCorePermission()
                            return@Switch
                        }
                        viewModel.updateAutoStartOnBoot(enabled)
                    }
                )
            },
            onClick = {
                if (!hasCorePermissions) {
                    onRequireCorePermission()
                    return@SettingRow
                }
                viewModel.updateAutoStartOnBoot(!uiState.settings.autoStartOnBoot)
            }
        )
    }

    // --- アプリ ---
    SectionCard(title = "アプリ") {
        SettingRow(
            title = "対象アプリを設定",
            subtitle = "時間を計測したいアプリを選びます。",
            onClick = onOpenAppSelect,
        )
        val gracePreset = SettingsBasicPresets.gracePresetOrNull(settings)
        val formattedGraceTime = formatDurationMillisOrNull(settings.gracePeriodMillis)
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
                append(formatDurationSeconds(settings.suggestionTriggerSeconds))
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
    SectionCard(title = "詳細設定") {
        SettingRow(
            title = "詳細設定を開く",
            subtitle = "タイマー表示や提案などの詳細な設定が行えます。",
            onClick = onOpenAdvanced,
        )
    }
}