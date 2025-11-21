package com.example.refocus.feature.onboarding

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.refocus.system.permissions.PermissionHelper
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.ui.components.OnboardingPage

enum class PermissionType {
    UsageAccess,
    Overlay,
}

data class PermissionStep(
    val type: PermissionType,
)

@Composable
fun PermissionFlowScreen(
    onFlowFinished: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val steps = remember {
        listOf(
            PermissionStep(PermissionType.UsageAccess),
            PermissionStep(PermissionType.Overlay)
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    val currentStep = steps.getOrNull(currentIndex)

    // すべてすでに許可済みなら即終了
    LaunchedEffect(Unit) {
        if (allPermissionsGranted(context)) {
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
                if (step != null && isGranted(context, step.type)) {
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

    // ここから UI 部分（権限ごとにページを分割）

    when (currentStep.type) {
        PermissionType.UsageAccess -> {
            UsageAccessPermissionPage(
                onRequestPermission = {
                    PermissionHelper.openUsageAccessSettings(activity)
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
                    PermissionHelper.openOverlaySettings(activity)
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

/* ---------- 各権限ごとの画面 ---------- */

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



/* ---------- ヘルパー ---------- */

private fun allPermissionsGranted(context: android.content.Context): Boolean {
    return PermissionHelper.hasUsageAccess(context) &&
            PermissionHelper.hasOverlayPermission(context)
}

private fun isGranted(context: android.content.Context, type: PermissionType): Boolean =
    when (type) {
        PermissionType.UsageAccess   -> PermissionHelper.hasUsageAccess(context)
        PermissionType.Overlay       -> PermissionHelper.hasOverlayPermission(context)
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
