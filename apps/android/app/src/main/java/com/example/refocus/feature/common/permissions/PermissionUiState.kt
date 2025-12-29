package com.example.refocus.feature.common.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.refocus.core.model.PermissionSnapshot
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

data class PermissionUiState(
    val usageGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationGranted: Boolean,
) {
    val hasCorePermissions: Boolean
        get() = usageGranted && overlayGranted

    val showNotificationWarning: Boolean
        get() = hasCorePermissions && !notificationGranted
}

@Composable
fun rememberPermissionUiState(onRefreshed: (PermissionUiState) -> Unit = {}): MutableState<PermissionUiState> {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val entryPoint =
        remember(appContext) {
            EntryPointAccessors.fromApplication(appContext, PermissionStatusEntryPoint::class.java)
        }
    val permissionStatusProvider =
        remember(entryPoint) {
            entryPoint.permissionStatusProvider()
        }

    val state =
        remember(permissionStatusProvider) {
            mutableStateOf(permissionStatusProvider.readCurrentInstant().toPermissionUiState())
        }

    // 画面復帰（ON_RESUME）で権限状態を再評価し，必要なら呼び出し側へ通知する．
    // ここで PermissionStateWatcher を経由することで，権限変化がタイムラインへ記録される．
    DisposableEffect(lifecycleOwner, permissionStatusProvider) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    coroutineScope.launch {
                        val latest = permissionStatusProvider.refreshAndRecord().toPermissionUiState()
                        state.value = latest
                        onRefreshed(latest)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 初回も一度リフレッシュして，ベースライン記録と UI 反映を同期する
    LaunchedEffect(permissionStatusProvider) {
        val latest = permissionStatusProvider.refreshAndRecord().toPermissionUiState()
        state.value = latest
        onRefreshed(latest)
    }

    return state
}

fun PermissionSnapshot.toPermissionUiState(): PermissionUiState =
    PermissionUiState(
        usageGranted = usageGranted,
        overlayGranted = overlayGranted,
        notificationGranted = notificationGranted,
    )
