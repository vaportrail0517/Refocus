package com.example.refocus.feature.home

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.feature.common.overlay.rememberOverlayHealthStore
import com.example.refocus.feature.common.overlay.rememberOverlayServiceController
import com.example.refocus.feature.common.overlay.rememberOverlayServiceStatusProvider
import com.example.refocus.feature.common.permissions.rememberPermissionNavigator
import com.example.refocus.feature.common.permissions.rememberPermissionUiState
import com.example.refocus.feature.customize.CustomizeViewModel
import com.example.refocus.feature.stats.StatsDetailSection
import com.example.refocus.feature.stats.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    val overlayHealthStore = rememberOverlayHealthStore()
    var serviceStartFailure by remember { mutableStateOf<ServiceStartFailureUiModel?>(null) }

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

    LaunchedEffect(Unit) {
        serviceStartFailure = readServiceStartFailureBestEffort(overlayHealthStore)
    }

    val shouldShowStartFailure =
        hasCorePermissions &&
            settingsUiState.customize.overlayEnabled &&
            !isServiceRunning

    HomeScreen(
        isRunning = isServiceRunning && settingsUiState.customize.overlayEnabled,
        hasCorePermissions = hasCorePermissions,
        showNotificationWarning = showNotificationWarning,
        stats = statsUiState.todayStats,
        appLabelByPackage = statsUiState.appLabelByPackage,
        targetApps = targetApps,
        serviceStartFailure = if (shouldShowStartFailure) serviceStartFailure else null,
        onDismissServiceStartFailure = {
            coroutineScope.launch {
                serviceStartFailure = null
                try {
                    withContext(Dispatchers.IO) {
                        overlayHealthStore.update { current ->
                            current.copy(
                                lastStartFailureWallClockMillis = null,
                                lastStartFailureSource = null,
                                lastStartFailureSummary = null,
                            )
                        }
                    }
                } catch (_: Exception) {
                    // no-op
                }
            }
        },
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

                    if (isServiceRunning) {
                        serviceStartFailure = null
                    } else {
                        serviceStartFailure = readServiceStartFailureBestEffort(overlayHealthStore)
                    }
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

private const val START_FAILURE_SHOW_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1000L

private suspend fun readServiceStartFailureBestEffort(
    store: com.example.refocus.domain.overlay.port.OverlayHealthStore,
): ServiceStartFailureUiModel? {
    val snapshot =
        try {
            withContext(Dispatchers.IO) {
                store.read()
            }
        } catch (_: Exception) {
            return null
        }

    val whenMillis = snapshot.lastStartFailureWallClockMillis ?: return null
    val summary = snapshot.lastStartFailureSummary ?: return null

    val age = System.currentTimeMillis() - whenMillis
    if (age > START_FAILURE_SHOW_WINDOW_MILLIS) return null

    return ServiceStartFailureUiModel(
        occurredAtText = formatWallClockMillis(whenMillis),
        source = snapshot.lastStartFailureSource,
        summary = summary,
    )
}

private fun formatWallClockMillis(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val formatter = DateTimeFormatter.ofPattern("M/d HH:mm")
    return formatter.format(dt)
}
