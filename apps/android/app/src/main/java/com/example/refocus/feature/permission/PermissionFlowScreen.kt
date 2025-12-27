package com.example.refocus.feature.permission

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.refocus.core.model.PermissionSnapshot
import com.example.refocus.feature.common.permissions.rememberPermissionNavigator
import com.example.refocus.feature.common.permissions.rememberPermissionStatusProvider
import com.example.refocus.ui.components.OnboardingPage
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

enum class PermissionType {
    UsageAccess,
    Overlay,
    Notifications,
}

data class PermissionStep(
    val type: PermissionType,
)

/**
 * コア権限（Usage / Overlay）を順番に案内するフロー。
 *
 * 追加で Android 13+ では通知権限（任意）も案内する。
 */
@Composable
fun PermissionFlowScreen(
    onFlowFinished: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionStatusProvider = rememberPermissionStatusProvider()
    val permissionNavigator = rememberPermissionNavigator()

    val steps = remember {
        buildList {
            add(PermissionStep(PermissionType.UsageAccess))
            add(PermissionStep(PermissionType.Overlay))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionStep(PermissionType.Notifications))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    val currentStep = steps.getOrNull(currentIndex)

    // すべてすでに許可済みなら即終了
    LaunchedEffect(Unit) {
        if (allPermissionsGranted(permissionStatusProvider.readCurrentInstant())) {
            onFlowFinished()
        }
    }

    // index が範囲外 → フロー完了
    LaunchedEffect(currentIndex, steps.size) {
        if (currentIndex >= steps.size) {
            onFlowFinished()
        }
    }

    if (currentStep == null || activity == null) {
        return
    }

    // 設定アプリから戻ってきた時（ON_RESUME）に権限を再チェック
    DisposableEffect(lifecycleOwner, currentIndex) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val step = steps.getOrNull(currentIndex)
                val latest = permissionStatusProvider.readCurrentInstant()
                if (step != null && isGranted(latest, step.type)) {
                    moveToNextStep(
                        stepsSize = steps.size,
                        currentIndex = currentIndex,
                        setIndex = { currentIndex = it }
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (currentStep.type) {
        PermissionType.UsageAccess -> {
            UsageAccessPermissionPage(
                onRequestPermission = {
                    permissionNavigator.openUsageAccessSettings(activity)
                },
                onSkip = {
                    moveToNextStep(
                        stepsSize = steps.size,
                        currentIndex = currentIndex,
                        setIndex = { currentIndex = it }
                    )
                }
            )
        }

        PermissionType.Overlay -> {
            OverlayPermissionPage(
                onRequestPermission = {
                    permissionNavigator.openOverlaySettings(activity)
                },
                onSkip = {
                    moveToNextStep(
                        stepsSize = steps.size,
                        currentIndex = currentIndex,
                        setIndex = { currentIndex = it }
                    )
                }
            )
        }

        PermissionType.Notifications -> {
            val requestLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { granted ->
                    if (granted) {
                        moveToNextStep(
                            stepsSize = steps.size,
                            currentIndex = currentIndex,
                            setIndex = { currentIndex = it }
                        )
                    }
                }
            )

            NotificationPermissionPage(
                onRequestPermission = {
                    requestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenSettings = {
                    permissionNavigator.openNotificationSettings(activity)
                },
                onSkip = {
                    moveToNextStep(
                        stepsSize = steps.size,
                        currentIndex = currentIndex,
                        setIndex = { currentIndex = it }
                    )
                }
            )
        }
    }
}

@Composable
private fun UsageAccessPermissionPage(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    OnboardingPage(
        title = "使用状況へのアクセス（必須）",
        description = "どのアプリをどれだけ連続して使っているかを計測するために必要な権限です。",
        primaryButtonText = "設定を開く",
        onPrimaryClick = onRequestPermission,
        secondaryButtonText = "あとで設定する",
        onSecondaryClick = onSkip
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        PermissionDetailCard(
            title = "使用状況アクセスの設定手順",
            steps = listOf(
                "「設定を開く」をタップします。",
                "表示された画面で「Refocus」を探してタップします。",
                "「使用状況へのアクセスを許可」をオンにします。",
                "戻るボタンで Refocus に戻ります。"
            )
        )
    }
}

@Composable
private fun OverlayPermissionPage(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    OnboardingPage(
        title = "他のアプリの上に重ねて表示（必須）",
        description = "タイマーを他のアプリの上に表示するために必要な権限です。",
        primaryButtonText = "設定を開く",
        onPrimaryClick = onRequestPermission,
        secondaryButtonText = "あとで設定する",
        onSecondaryClick = onSkip
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        PermissionDetailCard(
            title = "オーバーレイ表示の設定手順",
            steps = listOf(
                "「設定を開く」をタップします。",
                "「他のアプリの上に表示」または「他のアプリの上に重ねて表示」の一覧から「Refocus」を探します。",
                "「他のアプリの上に表示を許可」をオンにします。",
                "戻るボタンで Refocus に戻ります。"
            )
        )
    }
}

@Composable
private fun NotificationPermissionPage(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit
) {
    OnboardingPage(
        title = "通知（任意）",
        description = "Refocus の常駐通知を表示するために必要です。通知を許可すると、計測中のアプリ名や経過時間、操作ボタンを通知から使えます。",
        primaryButtonText = "通知を許可する",
        onPrimaryClick = onRequestPermission,
        secondaryButtonText = "あとで設定する",
        onSecondaryClick = onSkip
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        PermissionDetailCard(
            title = "通知の設定について",
            steps = listOf(
                "この権限は任意です（Refocus 自体は動作します）。",
                "拒否した場合でも、あとから設定画面の「通知」から許可できます。",
                "許可できない場合は次のボタンから通知設定を確認してください。"
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onOpenSettings) {
            Text(text = "通知設定を開く")
        }
    }
}

private fun allPermissionsGranted(snapshot: PermissionSnapshot): Boolean {
    val coreGranted = snapshot.usageGranted && snapshot.overlayGranted
    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        snapshot.notificationGranted
    } else {
        true
    }
    return coreGranted && notifGranted
}

private fun isGranted(snapshot: PermissionSnapshot, type: PermissionType): Boolean =
    when (type) {
        PermissionType.UsageAccess -> snapshot.usageGranted
        PermissionType.Overlay -> snapshot.overlayGranted
        PermissionType.Notifications -> snapshot.notificationGranted
    }

private fun moveToNextStep(
    stepsSize: Int,
    currentIndex: Int,
    setIndex: (Int) -> Unit
) {
    if (currentIndex + 1 < stepsSize) {
        setIndex(currentIndex + 1)
    } else {
        // 範囲外にして LaunchedEffect(currentIndex) で onFlowFinished を呼ばせる
        setIndex(stepsSize)
    }
}
