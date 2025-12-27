package com.example.refocus.feature.onboarding

import androidx.compose.runtime.Composable
import com.example.refocus.ui.components.OnboardingPage

/**
 * オンボーディング 1 ページ目。
 *
 * - Refocus の概要説明
 * - 「権限を設定する」ボタンから権限フローへ遷移する
 */
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