package com.example.refocus.feature.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.example.refocus.domain.overlay.port.OverlayServiceController
import com.example.refocus.gateway.PermissionNavigator
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow
import kotlinx.coroutines.launch

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
        title = "権限",
    ) {
        SettingRow(
            title = "使用状況へのアクセス",
            subtitle = "（必須）連続使用時間を計測するために必要です。",
            trailing = {
                Switch(
                    checked = usageGranted,
                    onCheckedChange = null,
                    enabled = true,
                )
            },
            onClick = {
                activity?.let { permissionNavigator.openUsageAccessSettings(it) }
            },
        )
        SettingRow(
            title = "他のアプリの上に表示",
            subtitle = "（必須）タイマーを他のアプリの上に表示するために必要です。",
            trailing = {
                Switch(
                    checked = overlayGranted,
                    onCheckedChange = null,
                    enabled = true,
                )
            },
            onClick = {
                activity?.let { permissionNavigator.openOverlaySettings(it) }
            },
        )

        SettingRow(
            title = "通知",
            subtitle = "（任意）常駐通知に計測状態と操作ボタンを表示します。",
            trailing = {
                Switch(
                    checked = notificationGranted,
                    onCheckedChange = null,
                    enabled = true,
                )
            },
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                    onRequestNotificationPermission()
                } else {
                    onOpenNotificationSettings()
                }
            },
        )
    }

    // --- 起動 ---
    SectionCard(
        title = "起動",
    ) {
        SettingRow(
            title = "Refocus を動かす",
            subtitle =
                if (!hasCorePermissions) {
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
                    },
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
            },
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
                    },
                )
            },
            onClick = {
                if (!hasCorePermissions) {
                    onRequireCorePermission()
                    return@SettingRow
                }
                viewModel.updateAutoStartOnBoot(!uiState.customize.autoStartOnBoot)
            },
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
