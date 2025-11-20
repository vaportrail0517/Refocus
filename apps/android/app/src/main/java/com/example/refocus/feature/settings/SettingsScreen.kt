package com.example.refocus.feature.settings

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.feature.overlay.OverlayService
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.feature.overlay.stopOverlayService

sealed interface SettingsDialog {
    data object Grace : SettingsDialog
    data object Polling : SettingsDialog
    data object FontRange : SettingsDialog
    data object TimeToMax : SettingsDialog
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
    var notificationGranted by remember {
        mutableStateOf(
            PermissionHelper.hasNotificationPermission(
                context
            )
        )
    }
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var fontRange by remember(
        uiState.overlaySettings.minFontSizeSp,
        uiState.overlaySettings.maxFontSizeSp
    ) {
        mutableStateOf(
            uiState.overlaySettings.minFontSizeSp..uiState.overlaySettings.maxFontSizeSp
        )
    }
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }

    // 画面復帰時に権限状態を更新
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = PermissionHelper.hasUsageAccess(context)
                overlayGranted = PermissionHelper.hasOverlayPermission(context)
                notificationGranted = PermissionHelper.hasNotificationPermission(context)
                isServiceRunning = OverlayService.isRunning
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionCard (
            title = "権限"
        ) {
            SettingRow(
                title = "使用状況へのアクセス（必須）",
                subtitle  = "連続使用時間を計測するために必要です。",
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
            SettingRow(
                title = "通知（任意）",
                subtitle = "やることの提案やお知らせに利用します。",
                trailing = {
                    Switch(
                        checked = notificationGranted,
                        onCheckedChange = null,
                        enabled = true
                    )
                },
                onClick = {
                    activity?.let { PermissionHelper.openNotificationSettings(it) }
                }
            )
        }

        SectionCard(
            title = "起動"
        ) {
            SettingRow(
                title = "Refocus を動かす",
                subtitle = if (isServiceRunning) {
                    "現在: 計測中（対象アプリ利用時に連続使用時間を記録します）"
                } else {
                    "現在: 停止中（対象アプリの計測は行われていません）"
                },
                trailing = {
                    val checked = uiState.overlaySettings.overlayEnabled && isServiceRunning
                    Switch(
                        checked = checked,
                        onCheckedChange = { newChecked ->
                            if (newChecked) {
                                viewModel.updateOverlayEnabled(true)
                                context.startOverlayService()
                                isServiceRunning = true
                            } else {
                                viewModel.updateOverlayEnabled(false)
                                context.stopOverlayService()
                                isServiceRunning = false
                            }
                        }
                    )
                },
                onClick = {
                    val currentlyOn = uiState.overlaySettings.overlayEnabled && isServiceRunning
                    val turnOn = !currentlyOn
                    if (turnOn) {
                        viewModel.updateOverlayEnabled(true)
                        context.startOverlayService()
                        isServiceRunning = true
                    } else {
                        viewModel.updateOverlayEnabled(false)
                        context.stopOverlayService()
                        isServiceRunning = false
                    }
                }
            )
            SettingRow(
                title = "端末起動時に自動起動",
                subtitle = "端末を再起動したときに自動で Refocus を起動します。",
                trailing = {
                    Switch(
                        checked = uiState.overlaySettings.autoStartOnBoot,
                        onCheckedChange = { enabled ->
                            viewModel.updateAutoStartOnBoot(enabled)
                        }
                    )
                           },
                onClick = {
                    viewModel.updateAutoStartOnBoot(!uiState.overlaySettings.autoStartOnBoot)
                }
            )
        }

        SectionCard (
            title = "アプリ"
        ) {
            SettingRow(
                title = "対象アプリを設定",
                subtitle = "時間を計測したいアプリを選択します。",
                onClick = onOpenAppSelect
            )
        }

        SectionCard (
            title = "タイマー"
        ) {
            SettingRow(
                title = "猶予時間",
                subtitle = "${formatGraceTimeText(uiState.overlaySettings.gracePeriodMillis)}（この時間以内に戻るとセッションを継続します）",
                onClick = {
                    activeDialog = SettingsDialog.Grace
                }
            )
            val pollingMs = uiState.overlaySettings.pollingIntervalMillis
//            SettingRow(
//                title = "監視間隔",
//                subtitle = "$pollingMs ms（アプリ切り替えの検出頻度）",
//                onClick = {
//                    activeDialog = SettingsDialog.Polling
//                }
//            )
            SettingRow(
                title = "フォントサイズ",
                subtitle = "最小 ${uiState.overlaySettings.minFontSizeSp} sp / 最大 ${uiState.overlaySettings.maxFontSizeSp} sp",
                onClick = {
                    fontRange = uiState.overlaySettings.minFontSizeSp..
                            uiState.overlaySettings.maxFontSizeSp
                    activeDialog = SettingsDialog.FontRange
                }
            )
            SettingRow(
                title = "最大サイズになるまでの時間",
                subtitle = "${uiState.overlaySettings.timeToMaxMinutes} 分",
                onClick = {
                    activeDialog = SettingsDialog.TimeToMax
                }
            )
            val dragEnabled = uiState.overlaySettings.touchMode == OverlayTouchMode.Drag
            SettingRow(
                title = "タイマーの移動",
                subtitle = if (dragEnabled) {
                    "オン：タイマーをドラッグして移動できます"
                } else {
                    "オフ：タイマーは固定され，タップはタイマーを透過します"
                },
                trailing = {
                    Switch(
                        checked = dragEnabled,
                        onCheckedChange = { isOn ->
                            val mode = if (isOn) {
                                OverlayTouchMode.Drag
                            } else {
                                OverlayTouchMode.PassThrough
                            }
                            viewModel.updateOverlayTouchMode(mode)
                        }
                    )
                },
                onClick = {
                    // 行全体をタップしてもトグルされるように
                    val newMode = if (dragEnabled) {
                        OverlayTouchMode.PassThrough
                    } else {
                        OverlayTouchMode.Drag
                    }
                    viewModel.updateOverlayTouchMode(newMode)
                }
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
            null -> Unit
        }
    }
}
