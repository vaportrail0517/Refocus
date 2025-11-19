package com.example.refocus.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.ui.components.OnboardingPage

@Composable
fun OnboardingIntroScreen(
    onStartSetup: () -> Unit
) {
    OnboardingPage(
        title = "Refocus へようこそ",
        description = "アプリの連続使用時間をリアルタイムに可視化するために、最初にいくつか設定を行います。",
        primaryButtonText = "権限を設定する",
        onPrimaryClick = onStartSetup
    )
}

@Composable
fun OnboardingReadyScreen(
    onSelectApps: () -> Unit
) {
    OnboardingPage(
        title = "準備ができました",
        description = "次に、Refocusで可視化するアプリを選びましょう。",
        primaryButtonText = "対象アプリを選択",
        onPrimaryClick = onSelectApps
    )
}

@Composable
fun OnboardingFinishScreen(
    onCloseApp: () -> Unit,
    onOpenApp: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        OnboardingState.setCompleted(context, true)
        context.startOverlayService()
    }

    OnboardingPage(
        title = "設定が完了しました",
        description = "このままアプリを閉じるか、Refocus のホーム画面を開いて設定を確認できます。",
        primaryButtonText = "Refocus を開く",
        onPrimaryClick = onOpenApp,
        secondaryButtonText = "アプリを閉じる",
        onSecondaryClick = onCloseApp
    )
}