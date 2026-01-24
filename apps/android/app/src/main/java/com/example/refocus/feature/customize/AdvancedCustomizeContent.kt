package com.example.refocus.feature.customize

import androidx.compose.runtime.Composable
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.util.formatDurationMilliSecondsOrNull
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.feature.customize.components.PresetOption
import com.example.refocus.feature.customize.components.PresetOptionRow
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun AdvancedCustomizeContent(
    uiState: CustomizeViewModel.UiState,
    viewModel: CustomizeViewModel,
    onOpenGraceDialog: () -> Unit,
    onOpenFontDialog: () -> Unit,
    onOpenTimeToMaxDialog: () -> Unit,
    onOpenSuggestionTriggerDialog: () -> Unit,
    onOpenSuggestionForegroundStableDialog: () -> Unit,
    onOpenSuggestionCooldownDialog: () -> Unit,
    onOpenSuggestionTimeoutDialog: () -> Unit,
    onOpenSuggestionInteractionLockoutDialog: () -> Unit,
    onOpenGrowthModeDialog: () -> Unit,
    onOpenColorModeDialog: () -> Unit,
    onOpenFixedColorDialog: () -> Unit,
    onOpenGradientStartColorDialog: () -> Unit,
    onOpenGradientMiddleColorDialog: () -> Unit,
    onOpenGradientEndColorDialog: () -> Unit,
) {
    val settings = uiState.customize

    SectionCard(title = "セッション") {
        val formattedGraceTime = formatDurationMilliSecondsOrNull(settings.gracePeriodMillis)
        SettingRow(
            title = "セッションの停止猶予時間",
            subtitle =
                if (formattedGraceTime.isNullOrEmpty()) {
                    "現在: 猶予なし（対象アプリを離れたらセッションを終了します）。"
                } else {
                    "現在: ${formattedGraceTime}以内に対象アプリへ戻れば同じセッションとして扱います。"
                },
            onClick = onOpenGraceDialog,
        )
    }

    SectionCard(title = "タイマーの成長") {
        SettingRow(
            title = "最大サイズになるまでの時間",
            subtitle = "現在: ${formatDurationSeconds(settings.timeToMaxSeconds.toLong())}",
            onClick = onOpenTimeToMaxDialog,
        )

        SettingRow(
            title = "タイマーの成長モード",
            subtitle =
                when (settings.growthMode) {
                    TimerGrowthMode.Linear ->
                        "線形：時間に比例して一定のペースで大きくなります。"

                    TimerGrowthMode.FastToSlow ->
                        "スローイン：序盤でぐっと大きくなり、その後はゆっくり変化します。"

                    TimerGrowthMode.SlowToFast ->
                        "スローアウト：最初は控えめで、長く使うほど目立つようになります。"

                    TimerGrowthMode.SlowFastSlow ->
                        "スローインアウト：真ん中あたりで一番ペースが速くなります。"
                },
            onClick = onOpenGrowthModeDialog,
        )
    }

    SectionCard(title = "タイマーのサイズ") {
        SettingRow(
            title = "文字サイズ",
            subtitle = "現在: 最小 ${settings.minFontSizeSp.toInt()}sp / 最大 ${settings.maxFontSizeSp.toInt()}sp",
            onClick = onOpenFontDialog,
        )
    }

    SectionCard(title = "タイマーの色") {
        SettingRow(
            title = "タイマーの色モード",
            subtitle =
                when (settings.colorMode) {
                    TimerColorMode.Fixed ->
                        "単色：背景色を一色で固定します。"

                    TimerColorMode.GradientTwo ->
                        "2色グラデーション：開始色から終了色へ変化します。"

                    TimerColorMode.GradientThree ->
                        "3色グラデーション：開始・中間・終了の3色で変化します。"
                },
            onClick = onOpenColorModeDialog,
        )
        // --- 色の詳細設定（モードに応じて表示） ---
        when (settings.colorMode) {
            TimerColorMode.Fixed -> {
                SettingRow(
                    title = "単色の色を選ぶ",
                    subtitle = colorSubtitle(settings.fixedColorArgb, "デフォルトの色を使用中"),
                    onClick = onOpenFixedColorDialog,
                )
            }

            TimerColorMode.GradientTwo -> {
                SettingRow(
                    title = "開始色（短時間側）",
                    subtitle =
                        colorSubtitle(
                            settings.gradientStartColorArgb,
                            "デフォルトの開始色を使用中",
                        ),
                    onClick = onOpenGradientStartColorDialog,
                )
                SettingRow(
                    title = "終了色（長時間側）",
                    subtitle =
                        colorSubtitle(
                            settings.gradientEndColorArgb,
                            "デフォルトの終了色を使用中",
                        ),
                    onClick = onOpenGradientEndColorDialog,
                )
            }

            TimerColorMode.GradientThree -> {
                SettingRow(
                    title = "開始色",
                    subtitle =
                        colorSubtitle(
                            settings.gradientStartColorArgb,
                            "デフォルトの開始色を使用中",
                        ),
                    onClick = onOpenGradientStartColorDialog,
                )
                SettingRow(
                    title = "中間色",
                    subtitle =
                        colorSubtitle(
                            settings.gradientMiddleColorArgb,
                            "デフォルトの中間色を使用中",
                        ),
                    onClick = onOpenGradientMiddleColorDialog,
                )
                SettingRow(
                    title = "終了色",
                    subtitle =
                        colorSubtitle(
                            settings.gradientEndColorArgb,
                            "デフォルトの終了色を使用中",
                        ),
                    onClick = onOpenGradientEndColorDialog,
                )
            }
        }
    }

    SectionCard(title = "提案の詳細") {
        SettingRow(
            title = "提案するまでの時間",
            subtitle =
                buildString {
                    append("現在: 対象アプリの利用を開始してから")
                    append(formatDurationSeconds(settings.suggestionTriggerSeconds.toLong()))
                    append("以上で提案します。")
                },
            onClick = onOpenSuggestionTriggerDialog,
        )

        SettingRow(
            title = "提案を出すために必要な対象アプリが連続して前面にいる時間",
            subtitle = "現在: ${
                formatDurationSeconds(
                    settings.suggestionForegroundStableSeconds.toLong(),
                )
            }以上経過してから提案します。",
            onClick = onOpenSuggestionForegroundStableDialog,
        )
        SettingRow(
            title = "次の提案までの間隔",
            subtitle = "現在: ${formatDurationSeconds(settings.suggestionCooldownSeconds.toLong())}待ってから再び提案をします。",
            onClick = onOpenSuggestionCooldownDialog,
        )
//        SettingRow(
//            title = "提案カードを自動で閉じるまでの時間",
//            subtitle =
//                if (settings.suggestionTimeoutSeconds != 0) {
//                    "現在: ${formatDurationSeconds(settings.suggestionTimeoutSeconds.toLong())}後に自動で閉じます。"
//                } else {
//                    "現在：時間経過では閉じません。"
//                },
//            onClick = onOpenSuggestionTimeoutDialog,
//        )
//        SettingRow(
//            title = "提案表示直後の誤タップ防止時間",
//            subtitle = "現在: 表示してから${settings.suggestionInteractionLockoutMillis} ms の間、提案カードを消せなくします。",
//            onClick = onOpenSuggestionInteractionLockoutDialog,
//        )
    }

    // --- プリセット（管理） ---
    val presetOptions =
        listOf(
            PresetOption(CustomizePreset.Default, "Default"),
            PresetOption(CustomizePreset.Custom, "Custom"),
            PresetOption(CustomizePreset.Debug, "Debug"),
        )
    val subtitleDescription =
        when (uiState.preset) {
            CustomizePreset.Default -> "標準的なバランスの設定です。"
            CustomizePreset.Debug -> "動作確認やデバッグに便利な設定です。"
            CustomizePreset.Custom -> "一部の値がプリセットから変更されています。"
        }
    PresetOptionRow(
        title = "現在のプリセット",
        currentPreset = uiState.preset,
        options = presetOptions,
        currentValueDescription = subtitleDescription,
        onPresetSelected = { preset ->
            when (preset) {
                CustomizePreset.Default -> viewModel.applyPreset(CustomizePreset.Default)
                CustomizePreset.Debug -> viewModel.applyPreset(CustomizePreset.Debug)
                CustomizePreset.Custom -> viewModel.setPresetCustom()
            }
        },
    )
}

private fun colorSubtitle(
    argb: Int,
    fallback: String,
): String {
    if (argb == 0) return fallback
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val hex = "#%02X%02X%02X".format(r, g, b)
    return "現在: $hex"
}
