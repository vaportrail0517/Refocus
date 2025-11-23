package com.example.refocus.feature.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.SettingsConfig.FontPreset
import com.example.refocus.core.model.SettingsConfig.GracePreset
import com.example.refocus.core.model.SettingsConfig.SuggestionCooldownPreset
import com.example.refocus.core.model.SettingsConfig.SuggestionTriggerPreset
import com.example.refocus.core.model.SettingsConfig.TimeToMaxPreset
import com.example.refocus.core.model.SettingsConfig.fontPresetOrNull
import com.example.refocus.core.model.SettingsConfig.gracePresetOrNull
import com.example.refocus.core.model.SettingsConfig.suggestionCooldownPresetOrNull
import com.example.refocus.core.model.SettingsConfig.suggestionTriggerPresetOrNull
import com.example.refocus.core.model.SettingsConfig.timeToMaxPresetOrNull
import com.example.refocus.core.model.SettingsPreset
import com.example.refocus.core.util.formatDurationMillis
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.feature.overlay.OverlayService
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.feature.overlay.stopOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.ui.components.OptionButtonsRow
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun SettingsScreen(
    onOpenAppSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var usageGranted by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var isAdvancedMode by remember { mutableStateOf(false) }
    var fontRange by remember(
        uiState.settings.minFontSizeSp,
        uiState.settings.maxFontSizeSp
    ) {
        mutableStateOf(
            uiState.settings.minFontSizeSp..uiState.settings.maxFontSizeSp
        )
    }
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }
    val hasCorePermissions = usageGranted && overlayGranted

    BackHandler(enabled = isAdvancedMode) {
        isAdvancedMode = false
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(isAdvancedMode) {
        scrollState.scrollTo(0)
    }

    // 画面復帰時に権限状態を更新
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = PermissionHelper.hasUsageAccess(context)
                overlayGranted = PermissionHelper.hasOverlayPermission(context)
                isServiceRunning = OverlayService.isRunning
                val hasCorePermissions = usageGranted && overlayGranted
                if (!hasCorePermissions) {
                    // 起動設定 or 実行中サービスが残っていたら OFF に揃える
                    if (uiState.settings.overlayEnabled || isServiceRunning) {
                        viewModel.updateOverlayEnabled(false)
                        context.stopOverlayService()
                        isServiceRunning = false
                    }
                    // 自動起動も OFF に揃える
                    if (uiState.settings.autoStartOnBoot) {
                        viewModel.updateAutoStartOnBoot(false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isAdvancedMode) {
            // ================= 基本設定 =================
            BasicSettingsContent(
                uiState = uiState,
                usageGranted = usageGranted,
                overlayGranted = overlayGranted,
                hasCorePermissions = hasCorePermissions,
                isServiceRunning = isServiceRunning,
                onServiceRunningChange = { isServiceRunning = it },
                onOpenAppSelect = onOpenAppSelect,
                onRequireCorePermission = { activeDialog = SettingsDialog.CorePermissionRequired },
                onOpenAdvanced = { isAdvancedMode = true },
                viewModel = viewModel,
                context = context,
                activity = activity,
            )
        } else {
            // ================= 詳細設定 =================
            AdvancedSettingsContent(
                uiState = uiState,
                onBackToBasic = { isAdvancedMode = false },
                onOpenGraceDialog = { activeDialog = SettingsDialog.GraceTime },
                onOpenPollingDialog = { activeDialog = SettingsDialog.PollingInterval },
                onOpenFontDialog = {
                    fontRange = uiState.settings.minFontSizeSp..
                            uiState.settings.maxFontSizeSp
                    activeDialog = SettingsDialog.FontRange
                },
                onOpenTimeToMaxDialog = { activeDialog = SettingsDialog.TimeToMax },
                onOpenSuggestionTriggerDialog = {
                    activeDialog = SettingsDialog.SuggestionTriggerTime
                },
                onOpenSuggestionForegroundStableDialog = {
                    activeDialog = SettingsDialog.SuggestionForegroundStable
                },
                onOpenSuggestionCooldownDialog = {
                    activeDialog = SettingsDialog.SuggestionCooldown
                },
                onOpenSuggestionTimeoutDialog = { activeDialog = SettingsDialog.SuggestionTimeout },
                onOpenSuggestionInteractionLockoutDialog = {
                    activeDialog = SettingsDialog.SuggestionInteractionLockout
                },
                viewModel = viewModel,
            )
        }

        when (activeDialog) {
            SettingsDialog.GraceTime -> {
                GraceTimeDialog(
                    currentMillis = uiState.settings.gracePeriodMillis,
                    onConfirm = { newMillis ->
                        viewModel.updateGracePeriodMillis(newMillis)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.PollingInterval -> {
                PollingIntervalDialog(
                    currentMillis = uiState.settings.pollingIntervalMillis,
                    onConfirm = { newMs ->
                        viewModel.updatePollingIntervalMillis(newMs)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.FontRange -> {
                FontRangeDialog(
                    initialRange = fontRange,
                    onConfirm = { newRange ->
                        val minFontSpLimit = 8f
                        val maxFontSpLimit = 96f
                        val clampedMin =
                            newRange.start.coerceIn(minFontSpLimit, maxFontSpLimit)
                        val clampedMax =
                            newRange.endInclusive.coerceIn(clampedMin, maxFontSpLimit)
                        viewModel.updateMinFontSizeSp(clampedMin)
                        viewModel.updateMaxFontSizeSp(clampedMax)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.TimeToMax -> {
                TimeToMaxDialog(
                    currentMinutes = uiState.settings.timeToMaxMinutes,
                    onConfirm = { minutes ->
                        viewModel.updateTimeToMaxMinutes(minutes)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.CorePermissionRequired -> {
                CorePermissionRequiredDialog(
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.SuggestionFeatureRequired -> {
                SuggestionFeatureRequiredDialog(
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.SuggestionTriggerTime -> {
                SuggestionTriggerTimeDialog(
                    currentSeconds = uiState.settings.suggestionTriggerSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionTriggerSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialog.SuggestionForegroundStable -> {
                SuggestionForegroundStableDialog(
                    currentSeconds = uiState.settings.suggestionForegroundStableSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionForegroundStableSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialog.SuggestionCooldown -> {
                SuggestionCooldownDialog(
                    currentSeconds = uiState.settings.suggestionCooldownSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionCooldownSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialog.SuggestionTimeout -> {
                SuggestionTimeoutDialog(
                    currentSeconds = uiState.settings.suggestionTimeoutSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionTimeoutSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialog.SuggestionInteractionLockout -> {
                SuggestionInteractionLockoutDialog(
                    currentMillis = uiState.settings.suggestionInteractionLockoutMillis,
                    onConfirm = { millis ->
                        viewModel.updateSuggestionInteractionLockoutMillis(millis)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            null -> Unit
        }
    }
}

@Composable
private fun BasicSettingsContent(
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
        val fontPreset = settings.fontPresetOrNull()
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

        val timePreset = settings.timeToMaxPresetOrNull()
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

        val gracePreset = settings.gracePresetOrNull()
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

        val trigPreset = settings.suggestionTriggerPresetOrNull()
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
        val cooldownPreset = settings.suggestionCooldownPresetOrNull()
        val cooldownSubtitle = buildString {
            append("現在: ")
            append(formatDurationSeconds(settings.suggestionCooldownSeconds))
            append("以上経過したら再び提案を行います。")

            append("（")
            append(
                when (cooldownPreset) {
                    SuggestionCooldownPreset.Infrequent -> "プリセット: 低い"
                    SuggestionCooldownPreset.Normal -> "プリセット: 普通"
                    SuggestionCooldownPreset.Frequent -> "プリセット: 高い"
                    null -> "カスタム"
                }
            )
            append("）")
        }
        OptionButtonsRow(
            title = "提案を再び表示する頻度",
            subtitle = cooldownSubtitle,
            optionLabels = listOf("低い", "普通", "高い"),
            selectedIndex = when (cooldownPreset) {
                SuggestionCooldownPreset.Infrequent -> 0
                SuggestionCooldownPreset.Normal -> 1
                SuggestionCooldownPreset.Frequent -> 2
                null -> null
            },
            onSelectIndex = { idx ->
                when (idx) {
                    0 -> viewModel.applySuggestionCooldownPreset(SuggestionCooldownPreset.Infrequent)
                    1 -> viewModel.applySuggestionCooldownPreset(SuggestionCooldownPreset.Normal)
                    2 -> viewModel.applySuggestionCooldownPreset(SuggestionCooldownPreset.Frequent)
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

@Composable
private fun AdvancedSettingsContent(
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
