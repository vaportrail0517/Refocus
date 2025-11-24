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
import com.example.refocus.core.model.SuggestionCooldownPreset
import com.example.refocus.core.model.SuggestionTriggerPreset
import com.example.refocus.core.model.TimeToMaxPreset
import com.example.refocus.core.util.formatDurationMillis
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.feature.overlay.stopOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.ui.components.OptionButtonsRow
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
        val fontSubtitle = buildString {
            append("現在: 最小 ")
            append(settings.minFontSizeSp.toInt())
            append("sp / 最大 ")
            append(settings.maxFontSizeSp.toInt())
            append("sp")

            append("（")
            append(
                when (fontPreset) {
                    FontPreset.Small -> "プリセット: 小さい"
                    FontPreset.Medium -> "プリセット: 普通"
                    FontPreset.Large -> "プリセット: 大きい"
                    null -> "カスタム"
                }
            )
            append("）")
        }
        OptionButtonsRow(
            title = "文字サイズ",
            subtitle = fontSubtitle,
            optionLabels = listOf("小さい", "普通", "大きい"),
            selectedIndex = when (fontPreset) {
                FontPreset.Small -> 0
                FontPreset.Medium -> 1
                FontPreset.Large -> 2
                null -> null
            },
            onSelectIndex = { idx ->
                when (idx) {
                    0 -> viewModel.applyFontPreset(FontPreset.Small)
                    1 -> viewModel.applyFontPreset(FontPreset.Medium)
                    2 -> viewModel.applyFontPreset(FontPreset.Large)
                }
            }
        )

        val timePreset = SettingsBasicPresets.timeToMaxPresetOrNull(settings)
        val timeSubtitle = buildString {
            append("現在: ")
            append(settings.timeToMaxMinutes)
            append("分")

            append("（")
            append(
                when (timePreset) {
                    TimeToMaxPreset.Fast -> "プリセット: 早い"
                    TimeToMaxPreset.Normal -> "プリセット: 普通"
                    TimeToMaxPreset.Slow -> "プリセット: 遅い"
                    null -> "カスタム"
                }
            )
            append("）")
        }
        OptionButtonsRow(
            title = "最大サイズになるまでの時間",
            subtitle = timeSubtitle,
            optionLabels = listOf("早い", "普通", "遅い"),
            selectedIndex = when (timePreset) {
                TimeToMaxPreset.Fast -> 0
                TimeToMaxPreset.Normal -> 1
                TimeToMaxPreset.Slow -> 2
                null -> null
            },
            onSelectIndex = { idx ->
                when (idx) {
                    0 -> viewModel.applyTimeToMaxPreset(TimeToMaxPreset.Fast)
                    1 -> viewModel.applyTimeToMaxPreset(TimeToMaxPreset.Normal)
                    2 -> viewModel.applyTimeToMaxPreset(TimeToMaxPreset.Slow)
                }
            }
        )

        val gracePreset = SettingsBasicPresets.gracePresetOrNull(settings)
        val graceSubtitle = buildString {
            append("現在: ")
            append(formatDurationMillis(settings.gracePeriodMillis))
            append("以内に対象アプリを再開すると同じセッションとして扱います。")
            append("（")
            append(
                when (gracePreset) {
                    GracePreset.Short -> "プリセット: 短い"
                    GracePreset.Normal -> "プリセット: 普通"
                    GracePreset.Long -> "プリセット: 長い"
                    null -> "カスタム"
                }
            )
            append("）")
        }
        OptionButtonsRow(
            title = "一時的なアプリ切り替え",
            subtitle = graceSubtitle,
            optionLabels = listOf("短い", "普通", "長い"),
            selectedIndex = when (gracePreset) {
                GracePreset.Short -> 0
                GracePreset.Normal -> 1
                GracePreset.Long -> 2
                null -> null
            },
            onSelectIndex = { idx ->
                when (idx) {
                    0 -> viewModel.applyGracePreset(GracePreset.Short)
                    1 -> viewModel.applyGracePreset(GracePreset.Normal)
                    2 -> viewModel.applyGracePreset(GracePreset.Long)
                }
            }
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
        val trigSubtitle = buildString {
            append("現在: 対象アプリの利用を開始してから")
            append(formatDurationSeconds(settings.suggestionTriggerSeconds))
            append("以上経過したら提案を行います。")
            append("（")
            append(
                when (trigPreset) {
                    SuggestionTriggerPreset.Short -> "プリセット: 短い"
                    SuggestionTriggerPreset.Normal -> "プリセット: 普通"
                    SuggestionTriggerPreset.Long -> "プリセット: 長い"
                    null -> "カスタム"
                }
            )
            append("）")
        }
        OptionButtonsRow(
            title = "初めに提案するまでの時間",
            subtitle = trigSubtitle,
            optionLabels = listOf("短い", "普通", "長い"),
            selectedIndex = when (trigPreset) {
                SuggestionTriggerPreset.Short -> 0
                SuggestionTriggerPreset.Normal -> 1
                SuggestionTriggerPreset.Long -> 2
                null -> null
            },
            onSelectIndex = { idx: Int ->
                when (idx) {
                    0 -> viewModel.applySuggestionTriggerPreset(SuggestionTriggerPreset.Short)
                    1 -> viewModel.applySuggestionTriggerPreset(SuggestionTriggerPreset.Normal)
                    2 -> viewModel.applySuggestionTriggerPreset(SuggestionTriggerPreset.Long)
                }
            }
        )
        val cooldownPreset = SettingsBasicPresets.suggestionCooldownPresetOrNull(settings)
        val cooldownSubtitle = buildString {
            append("現在: ")
            append(formatDurationSeconds(settings.suggestionCooldownSeconds))
            append("以上経過したら再び提案を行います。")

            append("（")
            append(
                when (cooldownPreset) {
                    SuggestionCooldownPreset.Short -> "プリセット: 短い"
                    SuggestionCooldownPreset.Normal -> "プリセット: 普通"
                    SuggestionCooldownPreset.Long -> "プリセット: 長い"
                    null -> "カスタム"
                }
            )
            append("）")
        }
        OptionButtonsRow(
            title = "提案を再び表示する頻度",
            subtitle = cooldownSubtitle,
            optionLabels = listOf("短い", "普通", "長い"),
            selectedIndex = when (cooldownPreset) {
                SuggestionCooldownPreset.Short -> 0
                SuggestionCooldownPreset.Normal -> 1
                SuggestionCooldownPreset.Long -> 2
                null -> null
            },
            onSelectIndex = { idx ->
                when (idx) {
                    0 -> viewModel.applySuggestionCooldownPreset(SuggestionCooldownPreset.Short)
                    1 -> viewModel.applySuggestionCooldownPreset(SuggestionCooldownPreset.Normal)
                    2 -> viewModel.applySuggestionCooldownPreset(SuggestionCooldownPreset.Long)
                }
            }
        )
    }

    // --- 設定プリセット（全体） ---
    SectionCard(title = "設定プリセット") {
        val preset = uiState.preset
        val subtitle = when (preset) {
            SettingsPreset.Default -> "標準的なバランスの設定です。"
            SettingsPreset.Debug -> "動作確認やデバッグに便利な設定です。"
            SettingsPreset.Custom -> "一部の値がプリセットから変更されています。"
        }

        OptionButtonsRow(
            title = "現在のプリセット",
            subtitle = subtitle,
            optionLabels = listOf("Default", "Custom", "Debug"),
            selectedIndex = when (preset) {
                SettingsPreset.Default -> 0
                SettingsPreset.Custom -> 1
                SettingsPreset.Debug -> 2
            },
            onSelectIndex = { idx: Int ->
                when (idx) {
                    0 -> viewModel.applyPreset(SettingsPreset.Default)
                    1 -> viewModel.setPresetCustom()
                    2 -> viewModel.applyPreset(SettingsPreset.Debug)
                }
            }
        )
    }

    // --- 詳細設定への入口 ---
    SectionCard(title = "詳細設定") {
        SettingRow(
            title = "詳細設定を開く",
            subtitle = "タイマー表示や提案などの詳細な設定が行えます。",
            onClick = onOpenAdvanced,
        )
    }
}