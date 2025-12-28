package com.example.refocus.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.refocus.domain.overlay.port.OverlayServiceController
import com.example.refocus.feature.common.overlay.rememberOverlayServiceController
import com.example.refocus.feature.common.overlay.rememberOverlayServiceStatusProvider
import com.example.refocus.feature.common.permissions.rememberPermissionNavigator
import com.example.refocus.feature.common.permissions.rememberPermissionStatusProvider
import com.example.refocus.feature.common.permissions.rememberPermissionUiState
import com.example.refocus.feature.common.permissions.toPermissionUiState
import com.example.refocus.gateway.PermissionNavigator
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow
import kotlinx.coroutines.launch


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



            SettingsDialogHost(
                activeDialog = activeDialog,
                onResetAllData = {
                    viewModel.resetAllData()
                },
                onStartPermissionFixFlow = {
                    onOpenPermissionFixFlow()
                },
                onDismiss = { activeDialog = null },
            )

        }
    }
}

