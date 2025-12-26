package com.example.refocus.feature.common.permissions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.refocus.system.permissions.PermissionHelper

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
fun rememberPermissionUiState(
    onRefreshed: (PermissionUiState) -> Unit = {},
): MutableState<PermissionUiState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val state = remember(context) {
        mutableStateOf(readPermissionUiState(context))
    }

    // 画面復帰（ON_RESUME）で権限状態を再評価し，必要なら呼び出し側へ通知する．
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = readPermissionUiState(context)
                state.value = latest
                onRefreshed(latest)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}

private fun readPermissionUiState(context: Context): PermissionUiState {
    return PermissionUiState(
        usageGranted = PermissionHelper.hasUsageAccess(context),
        overlayGranted = PermissionHelper.hasOverlayPermission(context),
        notificationGranted = PermissionHelper.hasNotificationPermission(context),
    )
}
