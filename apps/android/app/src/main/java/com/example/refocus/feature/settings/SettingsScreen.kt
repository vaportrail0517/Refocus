package com.example.refocus.feature.settings

import android.app.Activity
import android.content.Context
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.feature.common.overlay.rememberOverlayServiceStatusProvider
import com.example.refocus.feature.common.overlay.rememberOverlayServiceController
import com.example.refocus.feature.common.permissions.rememberPermissionUiState
import com.example.refocus.feature.common.permissions.rememberPermissionStatusProvider
import com.example.refocus.feature.common.permissions.rememberPermissionNavigator
import com.example.refocus.feature.common.permissions.toPermissionUiState
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow
import kotlinx.coroutines.launch
import com.example.refocus.domain.overlay.OverlayServiceController
import com.example.refocus.ui.gateway.PermissionNavigator


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAppSelect: () -> Unit,
    onOpenPermissionFixFlow: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val overlayServiceStatusProvider = rememberOverlayServiceStatusProvider()
    val overlayServiceController = rememberOverlayServiceController()
    val permissionStatusProvider = rememberPermissionStatusProvider()
    val permissionNavigator = rememberPermissionNavigator()
    var activeDialog by remember { mutableStateOf<SettingsDialogType?>(null) }
    var isServiceRunning by remember { mutableStateOf(overlayServiceStatusProvider.isRunning()) }

    val permissionState = rememberPermissionUiState(
        onRefreshed = { latest ->
            isServiceRunning = overlayServiceStatusProvider.isRunning()

            if (!latest.hasCorePermissions) {
                val latestState = viewModel.uiState.value
                // 起動設定 or 実行中サービスが残っていたら OFF に揃える
                if (latestState.customize.overlayEnabled || isServiceRunning) {
                    viewModel.updateOverlayEnabled(false)
                    overlayServiceController.stop(source = "settings_permission_refresh")
                    isServiceRunning = false
                }
                // 自動起動も OFF に揃える
                if (latestState.customize.autoStartOnBoot) {
                    viewModel.updateAutoStartOnBoot(false)
                }
            }
        },
    )

    val permissions = permissionState.value
    val hasCorePermissions = permissions.hasCorePermissions

    val scrollState = rememberScrollState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            // リクエスト結果を受け取ったタイミングで，Watcher 経由で差分検知・記録まで行う．
            // これにより UI 更新とタイムライン記録の経路を統一できる．
            coroutineScope.launch {
                val latest = permissionStatusProvider.refreshAndRecord().toPermissionUiState()
                permissionState.value = latest
            }
        }
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "設定",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "戻る"
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsContent(
                uiState = uiState,
                usageGranted = permissions.usageGranted,
                overlayGranted = permissions.overlayGranted,
                notificationGranted = permissions.notificationGranted,
                hasCorePermissions = hasCorePermissions,
                isServiceRunning = isServiceRunning,
                onServiceRunningChange = { isServiceRunning = it },
                onOpenAppSelect = onOpenAppSelect,
                onRequireCorePermission = {
                    activeDialog = SettingsDialogType.CorePermissionRequired
                },
                onResetAllData = { activeDialog = SettingsDialogType.AppDataReset },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenNotificationSettings = {
                    activity?.let { permissionNavigator.openNotificationSettings(it) }
                },
                viewModel = viewModel,
                context = context,
                activity = activity,
                permissionNavigator = permissionNavigator,
                overlayServiceController = overlayServiceController,
            )


            when (activeDialog) {
                SettingsDialogType.AppDataReset -> {
                    AppDataResetDialog(
                        onResetAllData = {
                            viewModel.resetAllData()
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                SettingsDialogType.CorePermissionRequired -> {
                    CorePermissionRequiredDialog(
                        onStartPermissionFixFlow = {
                            activeDialog = null
                            onOpenPermissionFixFlow()
                        },
                        onDismiss = { activeDialog = null }
                    )
                }

                SettingsDialogType.SuggestionFeatureRequired -> {
                    SuggestionFeatureRequiredDialog(
                        onDismiss = { activeDialog = null }
                    )
                }

                null -> Unit
            }
        }
    }
}

@Composable
fun SettingsContent(
    uiState: SettingsViewModel.UiState,
    usageGranted: Boolean,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    hasCorePermissions: Boolean,
    isServiceRunning: Boolean,
    onServiceRunningChange: (Boolean) -> Unit,
    onOpenAppSelect: () -> Unit,
    onRequireCorePermission: () -> Unit,
    onResetAllData: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    viewModel: SettingsViewModel,
    context: Context,
    activity: Activity?,
    permissionNavigator: PermissionNavigator,
    overlayServiceController: OverlayServiceController,
) {
    val settings = uiState.customize
    val coroutineScope = rememberCoroutineScope()

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
                activity?.let { permissionNavigator.openUsageAccessSettings(it) }
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
                activity?.let { permissionNavigator.openOverlaySettings(it) }
            }
        )

        SettingRow(
            title = "通知",
            subtitle = "（任意）常駐通知に計測状態と操作ボタンを表示します。",
            trailing = {
                Switch(
                    checked = notificationGranted,
                    onCheckedChange = null,
                    enabled = true
                )
            },
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                    onRequestNotificationPermission()
                } else {
                    onOpenNotificationSettings()
                }
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
                val checked = uiState.customize.overlayEnabled && isServiceRunning
                Switch(
                    checked = checked,
                    enabled = hasCorePermissions,
                    onCheckedChange = { newChecked ->
                        if (!hasCorePermissions) {
                            // 権限不足 → ダイアログを出すだけ
                            onRequireCorePermission()
                            return@Switch
                        }
                        coroutineScope.launch {
                            if (newChecked) {
                                try {
                                    viewModel.setOverlayEnabledAndWait(true)
                                } catch (_: Exception) {
                                    // 永続化に失敗した場合は起動しない（設定値とサービス状態を一致させる）．
                                    onServiceRunningChange(false)
                                    return@launch
                                }
                                onServiceRunningChange(
                                    overlayServiceController.startIfReady(source = "settings_toggle_on"),
                                )
                            } else {
                                try {
                                    viewModel.setOverlayEnabledAndWait(false)
                                } catch (_: Exception) {
                                    // 失敗してもサービスだけは停止する（安全側）．
                                }
                                overlayServiceController.stop(source = "settings_toggle_off")
                                onServiceRunningChange(false)
                            }
                        }
                    }
                )
            },
            onClick = {
                if (!hasCorePermissions) {
                    onRequireCorePermission()
                    return@SettingRow
                }
                val currentlyOn = uiState.customize.overlayEnabled && isServiceRunning
                val turnOn = !currentlyOn
                coroutineScope.launch {
                    if (turnOn) {
                        try {
                            viewModel.setOverlayEnabledAndWait(true)
                        } catch (_: Exception) {
                            onServiceRunningChange(false)
                            return@launch
                        }
                        onServiceRunningChange(
                            overlayServiceController.startIfReady(source = "settings_row_toggle_on"),
                        )
                    } else {
                        try {
                            viewModel.setOverlayEnabledAndWait(false)
                        } catch (_: Exception) {
                            // 失敗してもサービスだけは停止する（安全側）．
                        }
                        overlayServiceController.stop(source = "settings_row_toggle_off")
                        onServiceRunningChange(false)
                    }
                }
            }
        )
        SettingRow(
            title = "端末起動時に自動起動",
            subtitle = "端末を再起動したときに自動で Refocus を起動します。※起動には少し時間がかかります。",
            trailing = {
                Switch(
                    checked = uiState.customize.autoStartOnBoot,
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
                viewModel.updateAutoStartOnBoot(!uiState.customize.autoStartOnBoot)
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

    // --- データ ---
    SectionCard(title = "データ") {
        SettingRow(
            title = "アプリの初期化",
            subtitle = "全セッションの記録や登録した提案などを削除し，設定もデフォルトに戻します．",
            onClick = onResetAllData,
        )
    }
}
