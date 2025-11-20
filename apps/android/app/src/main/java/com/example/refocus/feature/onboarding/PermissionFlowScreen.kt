package com.example.refocus.feature.onboarding

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.refocus.system.permissions.PermissionHelper
import androidx.compose.material3.Text
import com.example.refocus.ui.components.OnboardingPage

enum class PermissionType {
    UsageAccess,
    Overlay,
    Notifications
}

enum class PermissionUIMode {
    TwoStepWithExplain,
    DirectGrant
}

data class PermissionStep(
    val type: PermissionType,
    val uiMode: PermissionUIMode
)

enum class PermissionPage {
    IntroOrSingle,
    Explain
}

@Composable
fun PermissionFlowScreen(
    onFlowFinished: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // 通知権限リクエストランチャー
    var requestNotificationPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // POST_NOTIFICATIONS はダイアログ内で完結するので、
        // 結果を見て次のステップに進む
        if (granted) {
            requestNotificationPermission?.invoke()
        }
    }

    // 権限定義（ここでは UI 文言は持たせず、タイプとモードだけ持つ）
    val steps = remember {
        buildList {
            add(
                PermissionStep(
                    type = PermissionType.UsageAccess,
                    uiMode = PermissionUIMode.TwoStepWithExplain
                )
            )
            add(
                PermissionStep(
                    type = PermissionType.Overlay,
                    uiMode = PermissionUIMode.TwoStepWithExplain
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionStep(
                        type = PermissionType.Notifications,
                        uiMode = PermissionUIMode.DirectGrant
                    )
                )
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableStateOf(PermissionPage.IntroOrSingle) }

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
                        setIndex = { currentIndex = it },
                        setPage = { currentPage = it }
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
            when (currentPage) {
                PermissionPage.IntroOrSingle -> {
                    UsageAccessIntroPage(
                        onNext = { currentPage = PermissionPage.Explain }
                    )
                }
                PermissionPage.Explain -> {
                    UsageAccessExplainPage(
                        onRequestPermission = {
                            PermissionHelper.openUsageAccessSettings(activity)
                        }
                    )
                }
            }
        }

        PermissionType.Overlay -> {
            when (currentPage) {
                PermissionPage.IntroOrSingle -> {
                    OverlayIntroPage(
                        onNext = { currentPage = PermissionPage.Explain }
                    )
                }
                PermissionPage.Explain -> {
                    OverlayExplainPage(
                        onRequestPermission = {
                            PermissionHelper.openOverlaySettings(activity)
                        }
                    )
                }
            }
        }

        PermissionType.Notifications -> {
            NotificationPermissionPage(
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // 許可ダイアログの結果が granted のときだけ次へ進む
                        requestNotificationPermission = {
                            moveToNextStep(
                                stepsSize = steps.size,
                                currentIndex = currentIndex,
                                setIndex = { currentIndex = it },
                                setPage = { currentPage = it }
                            )
                        }
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // API 32 以下は許可不要なので即次へ
                        moveToNextStep(
                            stepsSize = steps.size,
                            currentIndex = currentIndex,
                            setIndex = { currentIndex = it },
                            setPage = { currentPage = it }
                        )
                    }
                },
                onSkip = {
                    // 許可せずに次へ進む（任意）
                    moveToNextStep(
                        stepsSize = steps.size,
                        currentIndex = currentIndex,
                        setIndex = { currentIndex = it },
                        setPage = { currentPage = it }
                    )
                }
            )
        }
    }
}

/* ---------- 各権限ごとの画面 ---------- */

@Composable
private fun UsageAccessIntroPage(
    onNext: () -> Unit
) {
    OnboardingPage(
        title = "使用状況へのアクセス（必須）",
        description = "どのアプリをどれだけ連続して使っているかを計測するために必要な権限です。",
        primaryButtonText = "次へ",
        onPrimaryClick = onNext,
        content = {}
    )
}

@Composable
private fun UsageAccessExplainPage(
    onRequestPermission: () -> Unit
) {
    OnboardingPage(
        title = "使用状況へのアクセスを有効にしましょう",
        description = null,
        primaryButtonText = "設定を開く",
        onPrimaryClick = onRequestPermission,
        content = {
            Text("1. 「設定を開く」を押すと設定アプリが開きます。")
            Text("2. アプリ一覧から「Refocus」を選びます。")
            Text("3. 「使用状況へのアクセス」をオンにします。")
        }
    )
}

@Composable
private fun OverlayIntroPage(
    onNext: () -> Unit
) {
    OnboardingPage(
        title = "他のアプリの上に重ねて表示（必須）",
        description = "タイマーを他のアプリの上に表示するために必要です。",
        primaryButtonText = "次へ",
        onPrimaryClick = onNext,
        content = {}
    )
}


@Composable
private fun OverlayExplainPage(
    onRequestPermission: () -> Unit
) {
    OnboardingPage(
        title = "他のアプリの上に表示を許可しましょう",
        description = null,
        primaryButtonText = "設定を開く",
        onPrimaryClick = onRequestPermission,
        content = {
            Text("1. 「設定を開く」を押すと設定アプリが開きます。")
            Text("2. アプリ一覧から「Refocus」を選びます。")
            Text("3. 「他のアプリの上に表示」をオンにします。")
        }
    )
}

@Composable
private fun NotificationPermissionPage(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingPage(
        title = "通知（任意）",
        description = "やりたいことの提案などを行うために通知を使います。あとから設定画面でも変更できます。",
        primaryButtonText = "許可する",
        onPrimaryClick = onRequestPermission,
        secondaryButtonText = "あとで設定する",
        onSecondaryClick = onSkip,
        content = {}
    )
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
        PermissionType.Notifications -> PermissionHelper.hasNotificationPermission(context)
    }

private fun moveToNextStep(
    stepsSize: Int,
    currentIndex: Int,
    setIndex: (Int) -> Unit,
    setPage: (PermissionPage) -> Unit
) {
    if (currentIndex + 1 < stepsSize) {
        setIndex(currentIndex + 1)
        setPage(PermissionPage.IntroOrSingle)
    } else {
        setIndex(stepsSize)
    }
}
