package com.example.refocus.feature.home

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.feature.common.overlay.rememberOverlayServiceController
import com.example.refocus.feature.common.overlay.rememberOverlayServiceStatusProvider
import com.example.refocus.feature.common.permissions.rememberPermissionNavigator
import com.example.refocus.feature.common.permissions.rememberPermissionUiState
import com.example.refocus.feature.customize.CustomizeViewModel
import com.example.refocus.feature.stats.StatsDetailSection
import com.example.refocus.feature.stats.StatsViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    onOpenStatsDetail: (StatsDetailSection) -> Unit = {},
    onOpenPermissionFixFlow: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppSelect: () -> Unit,
    statsViewModel: StatsViewModel = hiltViewModel(),
    customizeViewModel: CustomizeViewModel = hiltViewModel(),
    targetsViewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val overlayServiceController = rememberOverlayServiceController()
    val permissionNavigator = rememberPermissionNavigator()

    val statsUiState = statsViewModel.uiState.collectAsStateWithLifecycle().value
    val settingsUiState by customizeViewModel.uiState.collectAsStateWithLifecycle()
    val targetApps = targetsViewModel.targetApps.collectAsStateWithLifecycle().value

    val overlayServiceStatusProvider = rememberOverlayServiceStatusProvider()
    var isServiceRunning by remember { mutableStateOf(overlayServiceStatusProvider.isRunning()) }

    val permissionState =
        rememberPermissionUiState(
            onRefreshed = { latest ->
                isServiceRunning = overlayServiceStatusProvider.isRunning()

                if (!latest.hasCorePermissions) {
                    val latestCustomize = customizeViewModel.uiState.value
                    if (latestCustomize.customize.overlayEnabled || isServiceRunning) {
                        customizeViewModel.updateOverlayEnabled(false)
                        overlayServiceController.stop(source = "home_permission_refresh")
                        isServiceRunning = false
                    }
                }
            },
        )

    val permissions = permissionState.value
    val hasCorePermissions = permissions.hasCorePermissions
    val showNotificationWarning = permissions.showNotificationWarning

    HomeScreen(
        isRunning = isServiceRunning && settingsUiState.customize.overlayEnabled,
        hasCorePermissions = hasCorePermissions,
        showNotificationWarning = showNotificationWarning,
        stats = statsUiState.todayStats,
        appLabelByPackage = statsUiState.appLabelByPackage,
        targetApps = targetApps,
        onToggleRunning = { wantRunning ->
            if (!hasCorePermissions) {
                onOpenPermissionFixFlow()
                return@HomeScreen
            }
            coroutineScope.launch {
                if (wantRunning) {
                    try {
                        // 永続化が完了してからサービスを起動する（起動直後に停止監視へ負けるのを防ぐ）．
                        customizeViewModel.setOverlayEnabledAndWait(true)
                    } catch (_: Exception) {
                        isServiceRunning = false
                        return@launch
                    }
                    isServiceRunning =
                        overlayServiceController
                            .startIfReady(source = "home_toggle_on")
                } else {
                    try {
                        customizeViewModel.setOverlayEnabledAndWait(false)
                    } catch (_: Exception) {
                        // 失敗してもサービスだけは停止する（安全側）．
                    }
                    overlayServiceController.stop(source = "home_toggle_off")
                    isServiceRunning = false
                }
            }
        },
        onOpenSettings = onOpenSettings,
        onOpenStatsDetail = onOpenStatsDetail,
        onOpenPermissionFixFlow = onOpenPermissionFixFlow,
        onOpenNotificationSettings = {
            val a = activity
            if (a != null) {
                permissionNavigator.openNotificationSettings(a)
            }
        },
        onOpenAppSelect = onOpenAppSelect,
    )
}
