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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.refocus.core.model.OverlaySettingsConfig.FontPreset
import com.example.refocus.core.model.OverlaySettingsConfig.GracePreset
import com.example.refocus.core.model.OverlaySettingsConfig.SuggestionTriggerPreset
import com.example.refocus.core.model.OverlaySettingsConfig.TimeToMaxPreset
import com.example.refocus.core.model.OverlaySettingsConfig.fontPresetOrNull
import com.example.refocus.core.model.OverlaySettingsConfig.gracePresetOrNull
import com.example.refocus.core.model.OverlaySettingsConfig.suggestionTriggerPresetOrNull
import com.example.refocus.core.model.OverlaySettingsConfig.timeToMaxPresetOrNull
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.SettingsPreset
import com.example.refocus.feature.overlay.OverlayService
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.feature.overlay.stopOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.ui.components.OptionButtonsRow
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

sealed interface SettingsDialog {
    data object Grace : SettingsDialog
    data object Polling : SettingsDialog
    data object FontRange : SettingsDialog
    data object TimeToMax : SettingsDialog
    data object CorePermission : SettingsDialog
    data object SuggestionFeature : SettingsDialog
}

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
    val uiState by viewModel.uiState.collectAsState()
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var usageGranted by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var isAdvancedMode by remember { mutableStateOf(false) }
    var fontRange by remember(
        uiState.overlaySettings.minFontSizeSp,
        uiState.overlaySettings.maxFontSizeSp
    ) {
        mutableStateOf(
            uiState.overlaySettings.minFontSizeSp..uiState.overlaySettings.maxFontSizeSp
        )
    }
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }
    val hasCorePermissions = usageGranted && overlayGranted

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
                    if (uiState.overlaySettings.overlayEnabled || isServiceRunning) {
                        viewModel.updateOverlayEnabled(false)
                        context.stopOverlayService()
                        isServiceRunning = false
                    }
                    // 自動起動も OFF に揃える
                    if (uiState.overlaySettings.autoStartOnBoot) {
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

    BackHandler(enabled = isAdvancedMode) {
        isAdvancedMode = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                onRequireCorePermission = { activeDialog = SettingsDialog.CorePermission },
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
                onOpenGraceDialog = { activeDialog = SettingsDialog.Grace },
                onOpenPollingDialog = { activeDialog = SettingsDialog.Polling },
                onOpenFontDialog = {
                    fontRange = uiState.overlaySettings.minFontSizeSp..
                            uiState.overlaySettings.maxFontSizeSp
                    activeDialog = SettingsDialog.FontRange
                },
                onOpenTimeToMaxDialog = { activeDialog = SettingsDialog.TimeToMax },
                viewModel = viewModel,
            )
        }

        when (activeDialog) {
            SettingsDialog.Grace -> {
                GraceTimeDialog(
                    currentMillis = uiState.overlaySettings.gracePeriodMillis,
                    onConfirm = { newMillis ->
                        viewModel.updateGracePeriodMillis(newMillis)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.Polling -> {
                PollingIntervalDialog(
                    currentMillis = uiState.overlaySettings.pollingIntervalMillis,
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
                    currentMinutes = uiState.overlaySettings.timeToMaxMinutes,
                    onConfirm = { minutes ->
                        viewModel.updateTimeToMaxMinutes(minutes)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.CorePermission -> {
                CorePermissionRequiredDialog(
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialog.SuggestionFeature -> {
                SuggestionFeatureRequiredDialog(
                    onDismiss = { activeDialog = null }
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
    val settings = uiState.overlaySettings

    // --- 権限 ---
    SectionCard(
        title = "権限"
    ) {
        SettingRow(
            title = "使用状況へのアクセス（必須）",
            subtitle = "連続使用時間を計測するために必要です。",
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
            title = "他のアプリの上に表示（必須）",
            subtitle = "タイマーを他のアプリの上に表示するために必要です。",
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
                val checked = uiState.overlaySettings.overlayEnabled && isServiceRunning
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
                val currentlyOn = uiState.overlaySettings.overlayEnabled && isServiceRunning
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
            subtitle = "端末を再起動したときに自動で Refocus を起動します。",
            trailing = {
                Switch(
                    checked = uiState.overlaySettings.autoStartOnBoot,
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
                viewModel.updateAutoStartOnBoot(!uiState.overlaySettings.autoStartOnBoot)
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
        val fontPreset = settings.fontPresetOrNull()
        OptionButtonsRow(
            title = "タイマーの文字サイズ",
            subtitle = when (fontPreset) {
                FontPreset.Small -> "小さめ"
                FontPreset.Medium -> "ふつう"
                FontPreset.Large -> "大きめ"
                null -> "カスタム（詳細設定で調整されています）"
            },
            optionLabels = listOf("小さめ", "ふつう", "大きめ"),
            selectedIndex = when (fontPreset) {
                FontPreset.Small -> 0
                FontPreset.Medium -> 1
                FontPreset.Large -> 2
                null -> null          // ← プリセットに合わない → どれも選ばれない
            },
            onSelectIndex = { idx: Int ->
                when (idx) {
                    0 -> viewModel.applyFontPreset(FontPreset.Small)
                    1 -> viewModel.applyFontPreset(FontPreset.Medium)
                    2 -> viewModel.applyFontPreset(FontPreset.Large)
                }
            }
        )

        val timePreset = settings.timeToMaxPresetOrNull()
        OptionButtonsRow(
            title = "タイマーが最大サイズになるまでの時間",
            subtitle = when (timePreset) {
                TimeToMaxPreset.Slow -> "遅め（約45分）"
                TimeToMaxPreset.Normal -> "ふつう（約30分）"
                TimeToMaxPreset.Fast -> "早め（約15分）"
                null -> "カスタム（詳細設定で調整されています）"
            },
            optionLabels = listOf("遅め", "ふつう", "早め"),
            selectedIndex = when (timePreset) {
                TimeToMaxPreset.Slow -> 0
                TimeToMaxPreset.Normal -> 1
                TimeToMaxPreset.Fast -> 2
                null -> null
            },
            onSelectIndex = { idx: Int ->
                when (idx) {
                    0 -> viewModel.applyTimeToMaxPreset(TimeToMaxPreset.Slow)
                    1 -> viewModel.applyTimeToMaxPreset(TimeToMaxPreset.Normal)
                    2 -> viewModel.applyTimeToMaxPreset(TimeToMaxPreset.Fast)
                }
            }
        )

        val gracePreset = settings.gracePresetOrNull()
        OptionButtonsRow(
            title = "一時的なアプリ切り替え",
            subtitle = when (gracePreset) {
                GracePreset.Short -> "短め（約30秒）"
                GracePreset.Normal -> "ふつう（約60秒）"
                GracePreset.Long -> "長め（約3分）"
                null -> "カスタム（詳細設定で調整されています）"
            },
            optionLabels = listOf("短め", "ふつう", "長め"),
            selectedIndex = when (gracePreset) {
                GracePreset.Short -> 0
                GracePreset.Normal -> 1
                GracePreset.Long -> 2
                null -> null
            },
            onSelectIndex = { idx: Int ->
                when (idx) {
                    0 -> viewModel.applyGracePreset(GracePreset.Short)
                    1 -> viewModel.applyGracePreset(GracePreset.Normal)
                    2 -> viewModel.applyGracePreset(GracePreset.Long)
                }
            }
        )
    }

    // --- 提案（簡易） ---
    SectionCard(title = "やりたいこと・休憩の提案") {
        val suggestionEnabled = settings.suggestionEnabled
        val restSuggestionEnabled = settings.restSuggestionEnabled

        SettingRow(
            title = "「やりたいこと」や休憩の提案",
            subtitle = if (suggestionEnabled) {
                "オン：一定時間使いすぎているときに、「やりたいこと」や休憩をそっと提案します。"
            } else {
                "オフ：提案カードは表示されません。"
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
            title = "やりたいことが無いときは休憩を提案",
            subtitle = when {
                !suggestionEnabled ->
                    "提案が無効になっています。"

                restSuggestionEnabled ->
                    "オン：やりたいことが未登録のとき、「少し休憩しませんか？」を提案します。"

                else ->
                    "オフ：やりたいことが未登録のときは提案しません。"
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
        OptionButtonsRow(
            title = "提案を出し始めるまでの時間",
            subtitle = when (trigPreset) {
                SuggestionTriggerPreset.Min10 -> "約10分連続で使ったとき"
                SuggestionTriggerPreset.Min15 -> "約15分連続で使ったとき"
                SuggestionTriggerPreset.Min30 -> "約30分連続で使ったとき"
                SuggestionTriggerPreset.Min60 -> "約60分連続で使ったとき"
                null -> "カスタム（詳細設定で調整されています）"
            },
            optionLabels = listOf("10分", "15分", "30分", "60分"),
            selectedIndex = when (trigPreset) {
                SuggestionTriggerPreset.Min10 -> 0
                SuggestionTriggerPreset.Min15 -> 1
                SuggestionTriggerPreset.Min30 -> 2
                SuggestionTriggerPreset.Min60 -> 3
                null -> null
            },
            onSelectIndex = { idx: Int ->
                when (idx) {
                    0 -> viewModel.applySuggestionTriggerPreset(SuggestionTriggerPreset.Min10)
                    1 -> viewModel.applySuggestionTriggerPreset(SuggestionTriggerPreset.Min15)
                    2 -> viewModel.applySuggestionTriggerPreset(SuggestionTriggerPreset.Min30)
                    3 -> viewModel.applySuggestionTriggerPreset(SuggestionTriggerPreset.Min60)
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
            subtitle = "監視間隔やタイマー表示、提案の細かいタイミングを調整します。",
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
    viewModel: SettingsViewModel,
) {
    val settings = uiState.overlaySettings

    // 一番上に「基本設定に戻る」行を置いておく（＋将来 AppBar を載せてもよい）
    SectionCard(title = "詳細設定") {
        SettingRow(
            title = "基本設定にもどる",
            subtitle = "ふだん使い向けのシンプルな設定に戻ります。",
            onClick = onBackToBasic,
        )
    }

    SectionCard(title = "監視・セッション") {
        SettingRow(
            title = "前面アプリをチェックする間隔",
            subtitle = "${settings.pollingIntervalMillis} ms ごとに対象アプリかどうか確認します。",
            onClick = onOpenPollingDialog,
        )
        SettingRow(
            title = "セッション継続の猶予時間",
            subtitle = "${settings.gracePeriodMillis / 1000} 秒以内に戻れば同じセッションとみなします。",
            onClick = onOpenGraceDialog,
        )
    }

    SectionCard(title = "タイマーの表示") {
        SettingRow(
            title = "フォントサイズの範囲",
            subtitle = "最小 ${settings.minFontSizeSp} sp / 最大 ${settings.maxFontSizeSp} sp",
            onClick = onOpenFontDialog,
        )
        SettingRow(
            title = "最大サイズになるまでの時間",
            subtitle = "${settings.timeToMaxMinutes} 分",
            onClick = onOpenTimeToMaxDialog,
        )
    }

    SectionCard(title = "タイマーのタッチ操作") {
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
    }

    SectionCard(title = "提案の詳細") {
        SettingRow(
            title = "提案カードを閉じるまでの時間",
            subtitle = "${settings.suggestionTimeoutSeconds} 秒後に自動で閉じます。",
            onClick = {
                // 将来: suggestionTimeout 用ダイアログを追加する
            },
        )
        SettingRow(
            title = "次の提案までの待ち時間",
            subtitle = "${settings.suggestionCooldownSeconds} 秒待ってから次の提案を出します。",
            onClick = {
                // 将来: suggestionCooldown 用ダイアログを追加する
            },
        )
        SettingRow(
            title = "前面が安定してから提案するまでの時間",
            subtitle = "${settings.suggestionForegroundStableSeconds} 秒以上経過してから提案します。",
            onClick = {
                // 将来: suggestionForegroundStable 用ダイアログを追加する
            },
        )
        SettingRow(
            title = "提案表示直後の誤タップ防止時間",
            subtitle = "${settings.suggestionInteractionLockoutMillis} ms のあいだボタンを押せなくします。",
            onClick = {
                // 将来: suggestionInteractionLockout 用ダイアログを追加する
            },
        )
    }
}
