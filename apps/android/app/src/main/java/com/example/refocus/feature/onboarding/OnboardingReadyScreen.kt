package com.example.refocus.feature.onboarding

import androidx.compose.runtime.Composable
import com.example.refocus.ui.components.OnboardingPage

/**
 * 権限セット完了後、「対象アプリ選択」へ橋渡しするページ。
 */
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