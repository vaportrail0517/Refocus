package com.example.refocus.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.refocus.ui.components.OnboardingPage


/**
 * オンボーディング最終ページ。
 *
 * - HOME へ進む
 * - アプリを閉じる
 *
 * のどちらかを選択する。
 */
@Composable
fun OnboardingFinishScreen(
    onCloseApp: () -> Unit,
    onOpenApp: () -> Unit
) {
    val viewModel: OnboardingFinishViewModel = hiltViewModel()
    OnboardingPage(
        title = "設定が完了しました",
        description = "このままアプリを閉じるか、Refocus のホーム画面を開いて設定を確認できます。",
        primaryButtonText = "Refocus を開く",
        onPrimaryClick = onOpenApp,
        secondaryButtonText = "アプリを閉じる",
        onSecondaryClick = onCloseApp
    )
}